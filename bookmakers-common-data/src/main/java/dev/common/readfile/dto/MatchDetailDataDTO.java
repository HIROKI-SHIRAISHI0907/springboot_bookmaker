package dev.common.readfile.dto;

import java.util.ArrayList;
import java.util.List;

import dev.common.entity.MatchDetailMatchEntity;
import dev.common.entity.MatchDetailMomentumEntity;
import dev.common.entity.MatchDetailMomentumIncidentEntity;
import dev.common.entity.MatchDetailSummaryEntity;
import dev.common.entity.MatchDetailGoalEntity;
import dev.common.entity.MatchDetailFormationEntity;
import dev.common.entity.MatchDetailLineupEntity;
import dev.common.entity.MatchDetailMissingEntity;
import dev.common.entity.MatchDetailMemberStatEntity;
import lombok.Data;

/**
 * 試合詳細データ（1 試合 = S3 の 1 フォルダ）の読み込み結果
 * <p>aws-s3-detail-data-csv の &lt;試合日&gt;/&lt;ホーム&gt;_vs_&lt;アウェー&gt;_&lt;試合ID&gt;/ 配下の CSV をまとめたもの。
 * ReadMatchDetail は 1 ファイル分（該当するリストだけ中身あり）を返し、GetMatchDetailInfo がフォルダ単位にまとめる。</p>
 * @author shiraishitoshio
 *
 */
@Data
public class MatchDetailDataDTO {

	/** フォルダのキー（例: 2026-10-25/ナポリ_vs_ローマ_SzXZwanC） */
	private String folderKey;

	/** 試合日（フォルダの日付） */
	private String matchDate;

	/** 試合ID（フォルダ名の末尾） */
	private String matchId;

	/** manifest のキー */
	private String manifestKey;

	/** manifest の取得日時（JST） */
	private String scrapedAtJst;

	/** manifest の errors（取れなかったページなど） */
	private List<String> scrapeErrors = new ArrayList<>();

	/** フォルダ内の全キー（CSV・manifest。登録後に削除する場合に使う） */
	private List<String> s3Keys = new ArrayList<>();

	/** 試合の基本情報（match_*.csv） */
	private List<MatchDetailMatchEntity> matchList = new ArrayList<>();

	/** マッチモメンタム（momentum_*.csv） */
	private List<MatchDetailMomentumEntity> momentumList = new ArrayList<>();

	/** モメンタム上のアイコン（momentumIncident_*.csv） */
	private List<MatchDetailMomentumIncidentEntity> momentumIncidentList = new ArrayList<>();

	/** サマリーの出来事（summary_*.csv） */
	private List<MatchDetailSummaryEntity> summaryList = new ArrayList<>();

	/** ゴール（goal_*.csv） */
	private List<MatchDetailGoalEntity> goalList = new ArrayList<>();

	/** フォーメーション（formation_*.csv） */
	private List<MatchDetailFormationEntity> formationList = new ArrayList<>();

	/** ラインアップ（lineup_*.csv） */
	private List<MatchDetailLineupEntity> lineupList = new ArrayList<>();

	/** 欠場選手（missing_*.csv） */
	private List<MatchDetailMissingEntity> missingList = new ArrayList<>();

	/** 選手スタッツ（全タブまとめ）（memberStat_*.csv） */
	private List<MatchDetailMemberStatEntity> memberStatList = new ArrayList<>();

	/**
	 * 別の読み込み結果（同じフォルダの別ファイル）を足し込む
	 * @param other 足し込む結果
	 */
	public void merge(MatchDetailDataDTO other) {
		if (other == null) {
			return;
		}
		this.matchList.addAll(other.getMatchList());
		this.momentumList.addAll(other.getMomentumList());
		this.momentumIncidentList.addAll(other.getMomentumIncidentList());
		this.summaryList.addAll(other.getSummaryList());
		this.goalList.addAll(other.getGoalList());
		this.formationList.addAll(other.getFormationList());
		this.lineupList.addAll(other.getLineupList());
		this.missingList.addAll(other.getMissingList());
		this.memberStatList.addAll(other.getMemberStatList());
	}
}
