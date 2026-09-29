package dev.application.analyze.bm_m004;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.TeamTimeSegmentStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;

/**
 * BM_M004 登録処理（team_time_segment_stats・縦持ち）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link TeamTimeSegmentStat} が作った1試合分の行（2チーム × 11時間帯 = 22行）に
 * シーズンと seq を設定し、UPSERT する。
 * 一意キー（シーズン・国リーグ・対象チーム・相手チーム・H/A・時間帯）が既にあれば上書きするため、
 * 同じ試合を再処理しても行は増えない（冪等）。
 * </p>
 *
 * <h2>シーズンの取得</h2>
 * <p>
 * 他の Writer（BM_M003 など）と同じく、シーズンは Writer 内で {@link SeasonResolverIF}
 * （実装: CountryLeagueSeasonResolver / country_league_season_master.season_year）から取得する。
 * Stat 側はシーズンを設定しない（設定済みでも上書きする）。
 * </p>
 * <ul>
 *   <li>国・リーグは行の dataCategory（"国: リーグ - ラウンドN" 形式）を {@link CountryLeagueParser} で分割して求める（形式が違う場合はシーズン取得不可としてスキップ）。</li>
 *   <li>1回の集計処理（{@link #clearSeasonCache()} から次の {@link #clearSeasonCache()} まで）の間は、
 *       国,リーグごとの結果をスレッド単位でキャッシュし、試合ごとにマスタを引かない。
 *       取得できなかった国,リーグも「取得不可」としてキャッシュする。</li>
 *   <li>取得できない場合は {@link SeasonNotResolvedException} を投げる。DB 書き込みの前に判定するため、
 *       その試合は何も保存されない。呼び出し側（Stat）はこの例外だけを捕まえて、その試合をスキップする。</li>
 * </ul>
 *
 * <h2>seq（主キー）の採番</h2>
 * <p>
 * 他のテーブルと同じく「&lt;シーズン&gt;-&lt;6桁枝番&gt;」（例: 2025/2026-000123）を
 * {@link SeqNumberingService}（seq_counter）で採番する。枝番はテーブル×シーズンで 1 から振る。
 * </p>
 * <ul>
 *   <li>先に1チーム分（対象・相手・H/A）の既存行の seq を引き、既にある時間帯はその seq を使い回す。
 *       新しい時間帯の行だけ採番するため、同じ試合を再処理しても番号を消費しない。</li>
 *   <li>採番は UPSERT と同じトランザクション内（seq_counter の行ロックはコミットまで保持）。
 *       ロールバック時は採番も戻るため欠番にならない。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合（22行）＝1トランザクション（REQUIRES_NEW）。途中で失敗するとその試合の22行はすべてロールバックされ、
 * 一部の時間帯だけ更新された状態は残らない。それ以前に登録済みの試合は取り消されない
 * （UPSERT のため、再実行しても二重にはならない）。
 * 1トランザクションは22行の UPSERT のみで短く、データが次々流れ込んでもロック時間は短い。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、
 *       新規行を含む試合の保存は（別スレッド・別プロセスでも）1試合ずつ順番になる。
 *       1トランザクションが短いので通常は問題ないが、並列度を上げても速くならない。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 両方が新規として採番し、後の INSERT は ON CONFLICT で更新になる。
 *       後の処理の番号は欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>: シーズン切替直後に前シーズンの試合を処理すると、新シーズンとして保存される。</li>
 *   <li><b>キャッシュのクリア忘れ</b>: スレッドプールのスレッドにキャッシュが残ると、シーズン切替後も古いシーズンを使い続ける。
 *       呼び出し側は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li><b>SeasonResolverIF の Bean がない</b>: アプリは起動するが、全試合が {@link SeasonNotResolvedException} でスキップされる。</li>
 *   <li><b>同じ組み合わせ（対象・相手・H/A）の試合がシーズン内に複数ある場合</b>: 後から処理した試合で上書きされる。</li>
 *   <li><b>1試合分に別の国,リーグの行が混ざっている場合</b>: 不正データとして例外（IllegalArgumentException）。
 *       Stat の作り方では起きない。</li>
 * </ul>
 */
@Service
public class TeamTimeSegmentWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamTimeSegmentWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamTimeSegmentWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M004";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "team_time_segment_stats";

	/** シーズン取得不可を表すキャッシュ値 */
	private static final String NOT_RESOLVED = "";

	/** 1回の集計処理中のシーズンキャッシュ（国,リーグ → シーズン。取得不可は NOT_RESOLVED） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	@Autowired
	private TeamTimeSegmentStatsRepository teamTimeSegmentStatsRepository;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * シーズン取得（実装: CountryLeagueSeasonResolver）。
	 * 実装が無い場合もアプリが起動できるよう required = false。登録時に実装が無ければ例外。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * シーズンのキャッシュを破棄する。集計の開始時と終了時（finally）に呼ぶこと。
	 */
	public void clearSeasonCache() {
		SEASON_CACHE.remove();
	}

	/**
	 * 1試合分の行にシーズンと seq を設定し、1トランザクションで UPSERT する。
	 *
	 * @param entities 1試合分（null・空の場合は何もしない。null 要素は無視）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void upsertMatch(List<TeamTimeSegmentStatsEntity> entities) {
		final String METHOD_NAME = "upsertMatch";
		if (entities == null || entities.isEmpty()) {
			return;
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		String dataCategory = requireSingleDataCategory(entities);
		String season = resolveSeason(dataCategory);

		int count = 0;
		int numbered = 0;
		String fillChar = null;
		// 1チーム分（対象,相手,H/A）ごとの既存 seq（時間帯 → seq）
		Map<String, Map<String, String>> existingSeqByTeam = new HashMap<>();
		for (TeamTimeSegmentStatsEntity entity : entities) {
			if (entity == null) {
				continue;
			}
			entity.setSeason(season);
			String seq = findExistingSeq(entity, existingSeqByTeam);
			if (seq == null) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
				numbered++;
			}
			entity.setSeq(seq);
			fillChar = buildLoggerFillChar(entity);
			int result = this.teamTimeSegmentStatsRepository.upsert(entity);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						1, result,
						fillChar + ", 時間帯: " + entity.getTimeSegment());
			}
			count++;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				BM_NUMBER + " 登録/更新件数: " + count + "件（うち新規採番: " + numbered + "件） (" + fillChar + " ほか)");
	}

	/**
	 * 同じ一意キーの既存行の seq を返す（なければ null）。
	 * 1チーム分（対象,相手,H/A）ごとに1回だけ DB を引く。
	 */
	private String findExistingSeq(TeamTimeSegmentStatsEntity entity,
			Map<String, Map<String, String>> existingSeqByTeam) {
		String teamKey = entity.getTeamName() + "\u0000" + entity.getOpponentTeamName() + "\u0000" + entity.getHa();
		Map<String, String> bySegment = existingSeqByTeam.get(teamKey);
		if (bySegment == null) {
			bySegment = new HashMap<>();
			List<TeamTimeSegmentStatsEntity> rows = this.teamTimeSegmentStatsRepository.findSeqByMatchKey(
					entity.getSeason(), entity.getDataCategory(), entity.getTeamName(),
					entity.getOpponentTeamName(), entity.getHa());
			if (rows != null) {
				for (TeamTimeSegmentStatsEntity r : rows) {
					if (r != null && r.getTimeSegment() != null && r.getSeq() != null) {
						bySegment.put(r.getTimeSegment(), r.getSeq());
					}
				}
			}
			existingSeqByTeam.put(teamKey, bySegment);
		}
		return bySegment.get(entity.getTimeSegment());
	}

	/**
	 * 1試合分の行がすべて同じ国,リーグであることを確認し、その値を返す。
	 */
	private String requireSingleDataCategory(List<TeamTimeSegmentStatsEntity> entities) {
		String dataCategory = null;
		for (TeamTimeSegmentStatsEntity e : entities) {
			if (e == null) {
				continue;
			}
			String dc = e.getDataCategory();
			if (dc == null || dc.isBlank()) {
				throw new IllegalArgumentException(BM_NUMBER + " dataCategory が空の行があります: " + buildLoggerFillChar(e));
			}
			if (dataCategory == null) {
				dataCategory = dc;
			} else if (!dataCategory.equals(dc)) {
				throw new IllegalArgumentException(
						BM_NUMBER + " 1試合分に別の国,リーグの行が混ざっています: " + dataCategory + " / " + dc);
			}
		}
		if (dataCategory == null) {
			throw new IllegalArgumentException(BM_NUMBER + " 有効な行がありません");
		}
		return dataCategory;
	}

	/**
	 * 国,リーグのシーズンを取得する（1回の集計処理の中ではキャッシュを使う）。
	 *
	 * @throws SeasonNotResolvedException 取得できない場合
	 */
	private String resolveSeason(String dataCategory) {
		final String METHOD_NAME = "resolveSeason";
		if (this.seasonResolver == null) {
			throw new SeasonNotResolvedException(
					"SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）: "
							+ dataCategory);
		}

		Map<String, String> cache = SEASON_CACHE.get();
		String season = cache.get(dataCategory);
		if (season == null) {
			season = NOT_RESOLVED;
			try {
				String[] split = CountryLeagueParser.parse(dataCategory);
				if (split != null && split.length >= 2) {
					String s = this.seasonResolver.resolveSeason(split[0].trim(), split[1].trim());
					if (s != null && !s.isBlank()) {
						season = s.trim();
					}
				}
			} catch (RuntimeException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						"countryLeague=" + dataCategory);
			}
			cache.put(dataCategory, season);
		}

		if (NOT_RESOLVED.equals(season)) {
			throw new SeasonNotResolvedException("シーズンを取得できません: " + dataCategory);
		}
		return season;
	}

	/**
	 * ログ用の埋め字を作る。
	 */
	private String buildLoggerFillChar(TeamTimeSegmentStatsEntity entity) {
		StringBuilder sb = new StringBuilder();
		sb.append("seq: ").append(entity.getSeq()).append(", ");
		sb.append("シーズン: ").append(entity.getSeason()).append(", ");
		sb.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		sb.append("対象チーム: ").append(entity.getTeamName()).append(", ");
		sb.append("相手チーム: ").append(entity.getOpponentTeamName()).append(", ");
		sb.append("H/A: ").append(entity.getHa());
		return sb.toString();
	}

	/**
	 * シーズンが取得できないことを表す例外。
	 * DB 書き込みの前に投げるため、この例外で終わった試合は何も保存されていない。
	 */
	public static class SeasonNotResolvedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SeasonNotResolvedException(String message) {
			super(message);
		}
	}
}
