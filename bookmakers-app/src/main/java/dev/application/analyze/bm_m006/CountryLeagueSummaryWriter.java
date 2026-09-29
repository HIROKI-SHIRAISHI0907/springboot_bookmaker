package dev.application.analyze.bm_m006;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.CountryLeagueSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M006 登録・更新処理（country_league_summary）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link CountryLeagueSummaryStat} が集計した「国,リーグ → 今回の試合数」を、
 * 国 × リーグ × シーズンの行の csv_count に加算する（行がなければ作る）。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく {@link SeasonResolverIF}（country_league_season_master.season_year）から取得する。
 *       1回の保存処理の中では国,リーグごとにキャッシュする。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       既に行があるときは採番しない（番号を消費しない）。</li>
 *   <li>シーズンが取得できない国,リーグは、DB 書き込みの前にスキップする（ログに出す）。他の国,リーグは保存する。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <ul>
 *   <li>1回の集計結果（全リーグ分）を <b>1トランザクション</b> で保存する。途中で失敗すると全件ロールバックされ、
 *       一部のリーグだけ加算された状態は残らない（そのまま再実行すれば正しく加算される）。
 *       行数はリーグ数程度で少ないため、トランザクションが長くなることはない。</li>
 *   <li>加算は「csv_count = csv_count + 加算分」を1本の SQL（INSERT ... ON CONFLICT DO UPDATE）で行うため、
 *       別プロセスが同時に動いても加算は消えない。以前の「読み取り → 加算 → UPDATE」と JVM 内ロックは廃止した。</li>
 *   <li>行はキー順（国,リーグの文字列順）に処理し、同時実行時に行ロックを取る順番を揃えてデッドロックを起きにくくしている。</li>
 *   <li>以前の DuplicateKeyException を捕まえて再読込する処理は、PostgreSQL では制約違反でトランザクションが中断されるため
 *       動かなかった。ON CONFLICT で重複自体が起きないようにしたので廃止した。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>csv_count は処理した回数の合計</b>: 同じ試合でも流れてくるたびに数える（仕様）。試合数としては使えない。</li>
 *   <li><b>ロールバック後の再実行</b>: 失敗した回の入力を再処理すれば正しく加算される。成功した回を再処理すると二重に加算される。</li>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行があるときは同時実行が順番待ちになる。</li>
 *   <li><b>シーズンは処理日基準</b>: シーズン切替直後に流れてきた前シーズンのデータは、新シーズンの行に加算される。</li>
 * </ul>
 */
@Service
public class CountryLeagueSummaryWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSummaryWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSummaryWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M006";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "country_league_summary";

	/** CountryLeagueSummaryRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * シーズン取得（実装: CountryLeagueSeasonResolver）。
	 * 実装が無い場合もアプリが起動できるよう required = false。実装が無ければ全リーグをスキップする。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 集計結果（全リーグ分）を1トランザクションで加算保存する。
	 *
	 * @param counts 国,リーグ（{@link CountryLeagueKey}）→ 今回の加算分（0 以下・null は無視）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void addCountsAll(Map<CountryLeagueKey, Integer> counts) {
		final String METHOD_NAME = "addCountsAll";
		if (counts == null || counts.isEmpty()) {
			return;
		}

		// 1) キー順に並べ、書き込みの前にシーズンを決める（取得できない国,リーグはスキップ）
		Map<CountryLeagueKey, String> seasons = new LinkedHashMap<>();
		Map<String, String> seasonCache = new HashMap<>();
		int skipCount = 0;
		for (Map.Entry<CountryLeagueKey, Integer> e : new TreeMap<>(counts).entrySet()) {
			if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
				continue;
			}
			String season = resolveSeason(e.getKey(), seasonCache);
			if (season == null) {
				skipCount++;
				continue;
			}
			seasons.put(e.getKey(), season);
		}

		// 2) 加算保存
		int insertCount = 0;
		int addCount = 0;
		for (Map.Entry<CountryLeagueKey, String> e : seasons.entrySet()) {
			CountryLeagueKey key = e.getKey();
			String season = e.getValue();
			int add = counts.get(key);

			String seq = this.countryLeagueSummaryRepository.findSeq(season, key.getCountry(), key.getLeague());
			boolean isNew = (seq == null);
			if (isNew) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
			}

			CountryLeagueSummaryEntity entity = new CountryLeagueSummaryEntity();
			entity.setSeq(seq);
			entity.setSeason(season);
			entity.setCountry(key.getCountry());
			entity.setLeague(key.getLeague());
			entity.setCsvCount(add);

			int result = this.countryLeagueSummaryRepository.upsertAdd(entity);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						1, result,
						String.format("seq=%s, season=%s, country=%s, league=%s, add=%d",
								seq, season, key.getCountry(), key.getLeague(), add));
			}
			if (isNew) {
				insertCount++;
			} else {
				addCount++;
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00006I_UPDATE_SUCCESS,
				BM_NUMBER + " 登録件数: " + insertCount + "件, 加算件数: " + addCount + "件, シーズン取得不可: "
						+ skipCount + "件");
	}

	/**
	 * 国,リーグのシーズンを取得する（取得できなければ null。ログを出す）。
	 */
	private String resolveSeason(CountryLeagueKey key, Map<String, String> cache) {
		final String METHOD_NAME = "resolveSeason";
		String cacheKey = key.getCountry() + "\u0000" + key.getLeague();
		if (cache.containsKey(cacheKey)) {
			return cache.get(cacheKey);
		}

		String season = null;
		if (this.seasonResolver == null) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					BM_NUMBER + " SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）: "
							+ key);
		} else {
			try {
				String s = this.seasonResolver.resolveSeason(key.getCountry(), key.getLeague());
				if (s != null && !s.isBlank()) {
					season = s.trim();
				}
			} catch (RuntimeException ex) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, ex,
						BM_NUMBER + " シーズン取得不可のためスキップ: " + key);
			}
		}
		cache.put(cacheKey, season);
		return season;
	}

	/**
	 * 集計キー（国, リーグ）。record は使わない。文字列順に並ぶ（国 → リーグ）。
	 */
	public static final class CountryLeagueKey implements Comparable<CountryLeagueKey> {

		private final String country;
		private final String league;

		public CountryLeagueKey(String country, String league) {
			if (country == null || league == null) {
				throw new IllegalArgumentException("country/league is null.");
			}
			this.country = country;
			this.league = league;
		}

		public String getCountry() {
			return this.country;
		}

		public String getLeague() {
			return this.league;
		}

		@Override
		public int compareTo(CountryLeagueKey o) {
			int c = this.country.compareTo(o.country);
			return (c != 0) ? c : this.league.compareTo(o.league);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof CountryLeagueKey)) {
				return false;
			}
			CountryLeagueKey o = (CountryLeagueKey) obj;
			return this.country.equals(o.country) && this.league.equals(o.league);
		}

		@Override
		public int hashCode() {
			return 31 * this.country.hashCode() + this.league.hashCode();
		}

		@Override
		public String toString() {
			return "country=" + this.country + ", league=" + this.league;
		}
	}
}
