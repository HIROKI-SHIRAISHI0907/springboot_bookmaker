package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadGeografic;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.CsvContentUtil;
import dev.common.util.S3QuarantineUtil;

/**
 * 地理情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>ファイルが無い場合は「データなし」で null を返す（B015 がまだ動いていない通常のケース。エラーログにしない）。</li>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、読み込みクラスに渡す前に判定し（{@link CsvContentUtil#isHeaderOnly}）、
 *       空のリストを返す。B014 は登録せずに、結果 CSV と入力 JSON を削除する。
 *       （以前は null を返していたため、削除されずに毎回同じファイルを読み続けていた）</li>
 *   <li>ダウンロード失敗は null（削除せず次回もう一度読む）。</li>
 *   <li>ログのラベルを [B015] から [B014] に修正。</li>
 * </ul>
 * <p>戻り値: null = ファイルなし・ダウンロード失敗・読み込みエラー（退避済み）（何もしない）、空のリスト = ヘッダのみ（削除だけ行う）、それ以外 = 登録対象。</p>
 * @author shiraishitoshio
 *
 *
 * <p>2026-10 修正（読み込めない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>読み込めないファイル（読み込みクラスがエラーを返した、またはデータ行はあるのに正しい行が 1 件も無い）は、
 *       {@link S3QuarantineUtil} で error/＜元のキー＞.error に退避する。残すと Lambda が「.csv あり」と判定して
 *       ECS を起動し続け、毎回同じファイルで失敗するため。中身は退避先に残るので後から調べられる。</li>
 *   <li>ダウンロード失敗・タイムアウトは一時的なエラーとみなし、退避せず次回もう一度読む。</li>
 *   <li>ReadGeografic は id・緯度経度が数値でないとファイル全体が読み込みエラーになるため、退避の対象になりやすい。</li>
 * </ul>
 */
@Component
public class GetGeograficInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetGeograficInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetGeograficInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetGeograficInfo.class);

	/** 取得対象ファイル（B015 の結果 CSV） */
	public static final String GEOGRAFIC_RESULT_KEY = "output/b015_team_location.csv";

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadGeografic readGeografic;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public List<TeamLocationEntity> getData() {
		final String METHOD_NAME = "getData";

		String bucket = config.getS3Geografic();
		String key = GEOGRAFIC_RESULT_KEY; // outputsフォルダ

		log.info("[B014] S3 bucket={} key={}", bucket, key);

		// ファイルが無い場合は「データなし」（エラーにしない）
		try {
			if (!s3Operator.existsOnS3(bucket, key)) {
				String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3: ファイルなし)");
				return null;
			}
		} catch (Exception e) {
			// 存在確認に失敗した場合は、従来どおりダウンロードを試みる
			log.warn("[B014] S3 存在確認に失敗（ダウンロードを試みます）: {}", e.toString());
		}

		byte[] bytes;
		try (InputStream is = s3Operator.download(bucket, key)) {
			bytes = is.readAllBytes();
		} catch (Exception e) {
			String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
					"S3 b015_team_location.csv ダウンロードエラー（削除せず次回もう一度読む）");
			return null;
		}

		// ヘッダのみ: 読み込みクラスに渡さず、空のリストを返す（B014 でファイルを削除する）
		if (CsvContentUtil.isHeaderOnly(bytes)) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"データ行なし（ヘッダのみ）のため登録せず削除対象にします: key=" + key);
			return new ArrayList<>();
		}

		try (InputStream is = new ByteArrayInputStream(bytes)) {
			ReadFileOutputDTO dto = readGeografic.getFileBodyFromStream(is, key);

			if (dto == null || !BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
				// 読み込みエラー（id・緯度経度が数値でない等）: 退避する（残すと Lambda が ECS を起動し続けるため）
				String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd,
						null,
						"S3 b015_team_location.csv 読み込みエラー（error/ に退避します）" + dto.getThrowAble()
								+ (dto == null ? " 読み込み結果なし" : ""));
				S3QuarantineUtil.quarantine(bucket, key, "読み込みエラー（ReadGeografic）",
						s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				return null;
			}

			List<TeamLocationEntity> entity = dto.getTeamLocationList();
			if (entity == null || entity.isEmpty()) {
				// データ行はあるのに正しい行が 1 件も無い（ヘッダのみは上で判定済み）: 壊れたファイルとして退避する
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
						"S3 b015_team_location.csv 正しいデータ行が0件（error/ に退避します）");
				S3QuarantineUtil.quarantine(bucket, key, "正しいデータ行が0件（ReadGeografic）",
						s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				return null;
			}

			return entity;

		} catch (Exception e) {
			// 読み込みクラスの外で起きた想定外のエラー（一時的なものとみなし、退避せず次回もう一度読む）
			String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
					"S3 b015_team_location.csv 読み込みエラー（削除せず次回もう一度読む）");
			return null;
		}
	}
}
