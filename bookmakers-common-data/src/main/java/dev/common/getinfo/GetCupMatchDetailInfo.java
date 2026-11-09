package dev.common.getinfo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.readfile.dto.MatchDetailDataDTO;

/**
 * 大会試合詳細データ取得管理クラス
 *
 * <p>大会（ACL など）の結果スクレイパー（CupResultData_aws_S3.py）が
 * aws-s3-cup-schedule-csv の {@value #CUP_DETAIL_PREFIX} 配下に出力した CSV を、1 試合（= 1 フォルダ）ごとにまとめて読む。
 * aws-s3-detail-data-csv は他の処理も置くので、大会の分はバケットを分けている。</p>
 *
 * <pre>
 * detail/&lt;試合日&gt;/&lt;ホーム&gt;_vs_&lt;アウェー&gt;_&lt;試合ID&gt;/
 *     match_*.csv / summary_*.csv / goal_*.csv / lineup_*.csv / formation_*.csv / missing_*.csv / momentum_*.csv
 *     manifest_*.json   … スクレイパーが最後に置く（これがあれば全ファイルそろっている）
 * detail/ 以外（b026_cup_competition.json・json/ など）は対象外。
 * </pre>
 *
 * <p>ファイルの形は試合詳細（B016）と同じなので、読み方（manifest の確認・並行読み込み・読めない試合は入れない）は
 * {@link GetMatchDetailInfo#getData(String, String, String)} を使う。</p>
 * <p>戻り値: key = フォルダのキー（detail/ を外した「試合日/フォルダ名」）、value = その試合のデータ
 * （{@link MatchDetailDataDTO#getS3Keys()} は detail/ を含めた S3 のキー）。
 * 対象が無ければ空の Map、S3 の一覧取得・スレッドの失敗時は null。</p>
 * @author shiraishitoshio
 *
 */
@Component
public class GetCupMatchDetailInfo {

	/** 大会の試合詳細を置くフォルダ（CupResultData_aws_S3.py の DETAIL_PREFIX と合わせる） */
	public static final String CUP_DETAIL_PREFIX = "detail/";

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** 試合詳細データ取得管理クラス（読み方を共通で使う） */
	@Autowired
	private GetMatchDetailInfo getMatchDetailInfo;

	/**
	 * 取得メソッド（大会の全試合）
	 * @return key = フォルダのキー、value = その試合のデータ
	 */
	public Map<String, MatchDetailDataDTO> getData() {
		return getData(null);
	}

	/**
	 * 取得メソッド（試合日を指定）
	 * @param matchDate 試合日（yyyy-MM-dd）。null・空なら全試合
	 * @return key = フォルダのキー、value = その試合のデータ
	 */
	public Map<String, MatchDetailDataDTO> getData(String matchDate) {
		return this.getMatchDetailInfo.getData(config.getS3BucketsCupSchedule(), CUP_DETAIL_PREFIX, matchDate);
	}

	/**
	 * 読むバケット
	 * @return aws-s3-cup-schedule-csv
	 */
	public String getBucket() {
		return config.getS3BucketsCupSchedule();
	}
}
