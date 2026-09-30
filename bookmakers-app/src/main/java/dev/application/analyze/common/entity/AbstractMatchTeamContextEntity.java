package dev.application.analyze.common.entity;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「1試合 × 1チーム視点」の共通項目（新しい統計テーブル用の親クラス）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * seq（seq_counter 採番）・シーズン・国・リーグ・マッチID・チーム・対戦相手・H/A を持つ。
 * 1試合をホーム視点・アウェー視点の2行に分けて持つテーブルの Entity はこのクラスを継承する。
 * </p>
 * <ul>
 *   <li>seq・season は Writer で設定する（シーズンは SeasonResolverIF、seq は SeqNumberingService）。</li>
 *   <li>チームマスタが無いため、チームは名前で持つ（旧 teamId / leagueId は名前と同じ値だったため削除）。
 *       チームマスタができたら team_id 列を追加すること。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractMatchTeamContextEntity extends MetaEntity {

	/** seq（&lt;シーズン&gt;-&lt;6桁枝番&gt;。seq_counter で採番） */
	private String seq;

	/** シーズン（SeasonResolverIF） */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** マッチID（参照用） */
	private String matchId;

	/** チーム（この行の視点） */
	private String team;

	/** 対戦相手 */
	private String opponent;

	/** H: ホーム / A: アウェー */
	private String ha;
}