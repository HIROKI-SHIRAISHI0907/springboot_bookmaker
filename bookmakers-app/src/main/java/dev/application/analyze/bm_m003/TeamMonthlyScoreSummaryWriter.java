package dev.application.analyze.bm_m003;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryStat.TeamYearKey;
import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.TeamMonthlyScoreSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * team_monthly_score_summary の書き込み専用 Writer。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link TeamMonthlyScoreSummaryStat} が「国・リーグ・チーム・H/A・年」単位にまとめた12か月分の得点数を、
 * 既存行があれば加算して UPDATE、なければ INSERT する。
 * </p>
 *
 * <h2>トランザクション設計</h2>
 * <ul>
 *   <li>1回の集計結果（全行）を <b>1トランザクション</b> で保存する。
 *       途中で失敗した場合は全件ロールバックされ、「一部だけ加算された」状態が残らない。</li>
 *   <li>1回の集計で扱う行数は「チーム数×2×年数」程度で少ないため、トランザクションが長時間になることはない。</li>
 *   <li>行はキー順（{@link TeamYearKey} の並び）に処理し、同時実行時に行ロックを取る順番を揃えて
 *       デッドロックを起きにくくしている。</li>
 *   <li>以前は「チーム×月」ごとに SELECT+UPDATE を別トランザクションで実行していた。
 *       12か月分をまとめて1回の UPDATE にしたため、DB アクセスは最大 1/12 になった。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>同時実行での加算漏れ（lost update）</b>: findByCount → UPDATE の読み取り・書き込みの間に
 *       別の処理が同じ行を更新すると、片方の加算が消える。完全に防ぐには、
 *       行ロック付きの検索（SELECT ... FOR UPDATE）を Mapper に追加して findByCount の代わりに使うこと。</li>
 *   <li><b>初回 INSERT の重複</b>: (country, league, team_name, ha, year) に一意制約がないと、
 *       同時実行で同じキーの行が2件できる。一意制約を付ければ、重複時は DuplicateKeyException で
 *       その回の保存が全件ロールバックされる（重複行はできない。その回の入力を再処理すれば正しく加算される）。
 *       PostgreSQL では制約違反後に同じトランザクションで SQL を続けられないため、捕まえて再試行はしない。</li>
 *   <li><b>同じキーの行が既に複数ある場合</b>: 先頭1件だけを更新する（どれが先頭かは SQL の並び順次第）。</li>
 * </ul>
 *
 * <h2>seq（主キー）の採番</h2>
 * <p>
 * 【変更】INSERT 時に「&lt;シーズン&gt;-&lt;6桁枝番&gt;」（例: 2025-2026-000001）を
 * {@link SeqNumberingService} で採番して設定する。シーズンは {@link SeasonResolverIF}
 * （country_league_season_master.season）から取得する。枝番はテーブル×シーズンで 1 から振る。
 * 既存行の UPDATE では seq を変えない。
 * </p>
 * <ul>
 *   <li>Mapper の insertTeamMonthlyScore に seq 列を追加し、seq 列を文字列型にしておくこと。</li>
 *   <li>SeasonResolverIF の実装が未連携のうちは、新規行の INSERT が例外になる（既存行の UPDATE だけなら動く）。</li>
 * </ul>
 *
 * <p>推奨 DDL（PostgreSQL）:</p>
 * <pre>
 * CREATE UNIQUE INDEX IF NOT EXISTS uq_team_monthly_score_summary
 *   ON team_monthly_score_summary (country, league, team_name, ha, year);
 * </pre>
 */
@Service
public class TeamMonthlyScoreSummaryWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMonthlyScoreSummaryWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMonthlyScoreSummaryWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M003";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "team_monthly_score_summary";

	/** 1回の保存処理中のシーズンキャッシュ（国,リーグ → シーズン） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	@Autowired
	private TeamMonthlyScoreSummaryRepository teamMonthlyScoreSummaryRepository;

	/** 【追加】seq 採番 */
	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * 【追加】シーズン取得（country_league_season_master 連携後に実装を用意する）。
	 * 未実装でもアプリが起動できるよう required = false。INSERT 時に未実装なら例外。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 集計結果（全行）を1トランザクションで加算保存する。
	 *
	 * @param aggregate キー → 12か月分の加算値（キー順に処理するため、TreeMap など順序付きのマップを渡すこと）
	 */
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
	public void addMonthlyGoalsAll(Map<TeamYearKey, int[]> aggregate) {
		final String METHOD_NAME = "addMonthlyGoalsAll";
		if (aggregate == null || aggregate.isEmpty()) {
			return;
		}

		int insertCount = 0;
		int updateCount = 0;
		SEASON_CACHE.set(new HashMap<>());
		try {
			for (Map.Entry<TeamYearKey, int[]> entry : aggregate.entrySet()) {
				boolean inserted = addMonthlyGoals(entry.getKey(), entry.getValue());
				if (inserted) {
					insertCount++;
				} else {
					updateCount++;
				}
			}
		} finally {
			SEASON_CACHE.remove();
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00006I_UPDATE_SUCCESS,
				BM_NUMBER + " 登録件数: " + insertCount + "件, 更新件数: " + updateCount + "件");
	}

	/**
	 * 1行分（チーム×H/A×年）の12か月分を加算保存する（呼び出し元のトランザクション内で実行）。
	 *
	 * @param key 保存単位のキー
	 * @param deltas 12か月分の加算値（添字 0 = 1月）
	 * @return true: INSERT した / false: UPDATE した
	 */
	private boolean addMonthlyGoals(TeamYearKey key, int[] deltas) {
		validate(deltas);

		TeamMonthlyScoreSummaryEntity current = findCurrent(key);
		if (current == null) {
			// 一意制約がある場合、同時実行で先に INSERT されると DuplicateKeyException になる。
			// PostgreSQL は制約違反の時点でトランザクション全体が中断状態になり、以降の SQL が
			// 実行できないため、ここでは捕まえずに全件ロールバックさせる（その回の入力を再処理すれば正しく加算される）。
			insert(key, deltas);
			return true;
		}
		update(key, current, deltas);
		return false;
	}

	/** 既存行を取得（なければ null。複数あれば先頭） */
	private TeamMonthlyScoreSummaryEntity findCurrent(TeamYearKey key) {
		TeamMonthlyScoreSummaryEntity condition = baseEntity(key);
		List<TeamMonthlyScoreSummaryEntity> list =
				this.teamMonthlyScoreSummaryRepository.findByCount(condition);
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	private void insert(TeamYearKey key, int[] deltas) {
		final String METHOD_NAME = "insert";
		TeamMonthlyScoreSummaryEntity saveEntity = baseEntity(key);
		saveEntity.applyMonths(deltas);
		// 【追加】seq を「<シーズン>-<枝番>」で採番（同じトランザクション内。INSERT 失敗時は採番もロールバック）
		saveEntity.setSeq(this.seqNumberingService.nextSeq(TABLE_NAME, resolveSeason(key)));

		int result = this.teamMonthlyScoreSummaryRepository.insertTeamMonthlyScore(saveEntity);
		if (result != 1) {
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00007E_INSERT_FAILED,
					1, result,
					key.toString());
		}
	}

	private void update(TeamYearKey key, TeamMonthlyScoreSummaryEntity current, int[] deltas) {
		final String METHOD_NAME = "update";
		int[] months = current.toMonthArray();
		for (int i = 0; i < months.length; i++) {
			months[i] += deltas[i];
		}

		TeamMonthlyScoreSummaryEntity saveEntity = baseEntity(key);
		saveEntity.setSeq(current.getSeq());
		saveEntity.applyMonths(months);

		int result = this.teamMonthlyScoreSummaryRepository.update(saveEntity);
		if (result != 1) {
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00008E_UPDATE_FAILED,
					1, result,
					String.format("seq=%s, %s", current.getSeq(), key));
		}
	}

	/**
	 * 【追加】国・リーグのシーズンを取得する（1回の保存処理の中ではキャッシュを使う）。
	 * SeasonResolver の実装が未連携の場合は例外（INSERT を伴う保存は全件ロールバック）。
	 */
	private String resolveSeason(TeamYearKey key) {
		if (this.seasonResolver == null) {
			throw new IllegalStateException(
					"SeasonResolver の実装がありません（country_league_season_master 未連携）: " + key);
		}
		String cacheKey = key.getCountry() + "," + key.getLeague();
		Map<String, String> cache = SEASON_CACHE.get();
		String season = cache.get(cacheKey);
		if (season == null) {
			season = this.seasonResolver.resolveSeason(key.getCountry(), key.getLeague());
			cache.put(cacheKey, season);
		}
		return season;
	}

	private TeamMonthlyScoreSummaryEntity baseEntity(TeamYearKey key) {
		TeamMonthlyScoreSummaryEntity e = new TeamMonthlyScoreSummaryEntity();
		e.setCountry(key.getCountry());
		e.setLeague(key.getLeague());
		e.setTeamName(key.getTeam());
		e.setHa(key.getHa());
		e.setYear(key.getYear());
		return e;
	}

	private void validate(int[] deltas) {
		if (deltas == null || deltas.length != TeamMonthlyScoreSummaryEntity.MONTH_COUNT) {
			throw new IllegalArgumentException("deltas is invalid.");
		}
		for (int d : deltas) {
			if (d < 0) {
				throw new IllegalArgumentException("goalCount is negative.");
			}
		}
	}
}
