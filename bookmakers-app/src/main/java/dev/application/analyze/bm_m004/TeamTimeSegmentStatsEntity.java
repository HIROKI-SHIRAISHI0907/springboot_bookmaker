package dev.application.analyze.bm_m004;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * team_time_segment_stats テーブルに対応するエンティティ（BM_M004 時間帯別 対戦成績・縦持ち）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 「シーズン × 対象チーム × 相手チーム × ホーム/アウェー × 時間帯」。
 * 対象チームがその試合のその時間帯（0-10, 11-20, …, 81-90, AT）に記録した値を持つ。
 * 1試合につき 2チーム × 11時間帯 = 22行。シーズンごとに同じ組み合わせ（対象・相手・H/A）の試合は1試合のため、
 * (season, dataCategory, teamName, opponentTeamName, ha, timeSegment) で一意になる。
 * 主キーは seq（「&lt;シーズン&gt;-&lt;6桁枝番&gt;」、例: 2025/2026-000123）。
 * </p>
 *
 * <h3>値の意味</h3>
 * <ul>
 *   <li>回数・期待値・得点: その時間帯に増えた数（時間帯の最後の累計 − 直前の時間帯の最後の累計）</li>
 *   <li>ポゼッション: その時間帯のポゼッション（累計ポゼッションと経過分数から逆算した近似値）</li>
 *   <li>パス系（成功/試行/率）: その時間帯の成功数・試行数と、そこから計算した成功率</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>時間帯の境界はスナップショット次第</b>: 時間帯内の最後の行が 8分なら、8〜10分の出来事は次の時間帯に入る。</li>
 *   <li><b>行がない時間帯</b>: 値はすべて null（snapshotCount = 0）。その分の出来事は次にデータがある時間帯にまとめて入る。</li>
 *   <li><b>ゴール取り消し・値の欠落</b>: 累計が減った場合の増分は 0 とする（取り消し前の時間帯に得点が残ることがある）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamTimeSegmentStatsEntity extends MetaEntity {

	/** seq（主キー。「<シーズン>-<6桁枝番>」。Writer で seq_counter から採番する） */
	private String seq;

	/** シーズン（country_league_season_master.season_year） */
	private String season;

	/** 国,リーグ */
	private String dataCategory;

	/** 対象チーム */
	private String teamName;

	/** 相手チーム */
	private String opponentTeamName;

	/** 対象チームのホーム/アウェー（"H" / "A"） */
	private String ha;

	/** マッチID（参照用） */
	private String matchId;

	/** 時間帯（0-10, 11-20, …, 81-90, AT） */
	private String timeSegment;

	/** 時間帯の並び順（0〜10） */
	private Integer segmentOrder;

	/** 時間帯内のスナップショット行数（0 なら値はすべて null） */
	private Integer snapshotCount;

	/** 時間帯内の得点 */
	private Integer goalFor;

	/** 時間帯内の失点 */
	private Integer goalAgainst;

	/** 時間帯内の期待値（xG）増分 */
	private Double exp;

	/** 時間帯内の枠内ゴール期待値増分 */
	private Double inGoalExp;

	/** 時間帯内のポゼッション（%） */
	private Double possession;

	/** シュート数 */
	private Integer shootAll;

	/** 枠内シュート数 */
	private Integer shootIn;

	/** 枠外シュート数 */
	private Integer shootOut;

	/** ブロックされたシュート数 */
	private Integer shootBlocked;

	/** ビッグチャンス数 */
	private Integer bigChance;

	/** コーナーキック数 */
	private Integer cornerKick;

	/** ボックス内シュート数 */
	private Integer boxShootIn;

	/** ボックス外シュート数 */
	private Integer boxShootOut;

	/** ゴールポスト数 */
	private Integer goalPost;

	/** ヘディングゴール数 */
	private Integer goalHead;

	/** キーパーセーブ数 */
	private Integer keeperSave;

	/** フリーキック数 */
	private Integer freeKick;

	/** オフサイド数 */
	private Integer offside;

	/** ファウル数 */
	private Integer foul;

	/** イエローカード数 */
	private Integer yellowCard;

	/** レッドカード数 */
	private Integer redCard;

	/** スローイン数 */
	private Integer slowIn;

	/** ボックスタッチ数 */
	private Integer boxTouch;

	/** クリア数 */
	private Integer clearCount;

	/** デュエル勝利数 */
	private Integer duelCount;

	/** インターセプト数 */
	private Integer interceptCount;

	/** パス成功数 */
	private Integer passSuccess;

	/** パス試行数 */
	private Integer passTry;

	/** パス成功率（%） */
	private Double passRate;

	/** ロングパス成功数 */
	private Integer longPassSuccess;

	/** ロングパス試行数 */
	private Integer longPassTry;

	/** ロングパス成功率（%） */
	private Double longPassRate;

	/** ファイナルサードパス成功数 */
	private Integer finalThirdPassSuccess;

	/** ファイナルサードパス試行数 */
	private Integer finalThirdPassTry;

	/** ファイナルサードパス成功率（%） */
	private Double finalThirdPassRate;

	/** クロス成功数 */
	private Integer crossSuccess;

	/** クロス試行数 */
	private Integer crossTry;

	/** クロス成功率（%） */
	private Double crossRate;

	/** タックル成功数 */
	private Integer tackleSuccess;

	/** タックル試行数 */
	private Integer tackleTry;

	/** タックル成功率（%） */
	private Double tackleRate;
}
