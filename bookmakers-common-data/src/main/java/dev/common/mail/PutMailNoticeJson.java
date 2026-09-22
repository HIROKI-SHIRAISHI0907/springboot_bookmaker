package dev.common.mail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
import lombok.extern.slf4j.Slf4j;

/**
 * S3上にどのメールデータを格納したかを表す制御JSON(XXX.json)へ、
 * メール処理キーを登録・更新するクラス。
 *
 * <p>XXX.json は「メール処理キー」をキー、フラグ文字列を値に持つマップ構造を想定している。
 *
 * <pre>
 * {
 *   "orderMail"    : "",
 *   "reminderMail" : "通知済"
 * }
 * </pre>
 *
 * <p>本クラスでは、以下の処理を行う。
 * <ul>
 *   <li>メール処理キーを「未通知」状態で登録する</li>
 *   <li>メール処理キーを「通知済」状態へ更新する</li>
 *   <li>メール処理キーを「送信失敗」状態へ更新する</li>
 * </ul>
 *
 * <p>設計方針:
 * <ul>
 *   <li>jsonFileNameが既にS3へアップロード済みの場合は、その内容を取得して更新する</li>
 *   <li>指定された処理キーが存在しない場合は新規追加する</li>
 *   <li>指定された処理キーが既に存在する場合は、そのキーの値のみ更新する</li>
 *   <li>他の処理キーの値は変更しない</li>
 *   <li>既存JSONの取得・パースに失敗した場合は空Mapから処理を継続する</li>
 * </ul>
 *
 * <p>{@link S3Operator} には文字列を直接書き込むputObjectのようなメソッドが
 * 無いため、書き込みは {@link S3Operator#putJson(String, String, String)} を、
 * 存在確認は {@link S3Operator#existsOnS3(String, String)}、
 * 読み取りは {@link S3Operator#downloadTextUtf8(String, String)} を利用する。
 *
 * <p><b>既知の注意点:</b>
 * 本実装はS3からの取得→メモリ上での更新→S3への書き戻し、
 * というread-modify-write方式のため、同一のjsonFileNameに対して複数の登録・更新が
 * ほぼ同時に発生すると、後勝ちで片方の更新が失われる可能性がある。
 * 同時実行の可能性がある場合は、S3の条件付きPUT(If-Match/If-None-Match)や、
 * DynamoDB等での排他制御の導入を検討すること。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Component
public class PutMailNoticeJson {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = PutMailNoticeJson.class
			.getProtectionDomain()
			.getCodeSource()
			.getLocation()
			.getPath();

	/** クラス名 */
	private static final String CLASS_NAME = PutMailNoticeJson.class.getName();

	/** JSON内で「未通知」であることを表すフラグ値 */
	private static final String UNNOTIFIED_FLAG = "";

	/** JSON内で「通知済」であることを表すフラグ値 */
	private static final String NOTIFIED_FLAG = "通知済";

	/** JSON内で「送信失敗」であることを表すフラグ値 */
	private static final String FAILED_FLAG = "送信失敗";

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	/** JSON変換用ObjectMapper */
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 処理キーを未通知状態でJSONへ登録する。
	 *
	 * <p>指定されたJSONが既に存在する場合は既存の内容を取得し、
	 * 指定された処理キーを追加または未通知状態へ上書きする。
	 *
	 * @param jsonFileName 格納JSONファイル名
	 * @param mailProcessKey 登録するメール処理キー
	 */
	public void putJson(String jsonFileName, String mailProcessKey) {

		log.info("[{}] putJson開始 project={}, jsonFileName={}",
				CLASS_NAME, PROJECT_NAME, jsonFileName);

		if (jsonFileName == null || jsonFileName.isEmpty()) {
			log.warn("[{}] jsonFileNameが未指定のため処理をスキップします。",
					CLASS_NAME);
			return;
		}

		if (mailProcessKey == null || mailProcessKey.isEmpty()) {
			log.warn("[{}] keyが未指定のため処理をスキップします。jsonFileName={}",
					CLASS_NAME, jsonFileName);
			return;
		}

		String mailBucket = config.getS3BucketsMail();

		// 1. 既存JSONを取得
		Map<String, String> noticeMap = loadExistingNoticeMap(mailBucket, jsonFileName);

		// 2. 処理キーを未通知状態で登録
		if (noticeMap.containsKey(mailProcessKey)) {
			log.info("[{}] 既に登録済みのキーです。上書きします。key={}",
					CLASS_NAME, mailProcessKey);
		} else {
			log.info("[{}] 新規のキーとして追記します。key={}",
					CLASS_NAME, mailProcessKey);
		}

		noticeMap.put(mailProcessKey, UNNOTIFIED_FLAG);

		// 3. S3へ書き戻し
		writeNoticeJson(mailBucket, jsonFileName, noticeMap);

		log.info("[{}] JSONへの処理キー登録が完了しました。bucket={}, key={}, mailProcessKey={}",
				CLASS_NAME, mailBucket, jsonFileName, mailProcessKey);
	}

	/**
	 * 処理キーを通知済状態へ更新する。
	 *
	 * <p>既存JSONから処理キーを取得し、
	 * 指定された処理キーの値を「通知済」へ変更する。
	 *
	 * <p>他の処理キーの値は変更しない。
	 *
	 * @param jsonFileName 格納JSONファイル名
	 * @param mailProcessKey 通知済へ更新するメール処理キー
	 */
	public void updateNoticeCompleted(String jsonFileName, String mailProcessKey) {
		updateNoticeFlag(jsonFileName, mailProcessKey, NOTIFIED_FLAG, "通知済");
	}

	/**
	 * 処理キーを送信失敗状態へ更新する。
	 *
	 * <p>送信が既定の回数（現状3回）失敗し、以降このバッチの対象から外れる
	 * 処理キーについて、JSON上でも「送信失敗」であることを明示的に分かるように
	 * 上書きする。
	 *
	 * <p>既存JSONから処理キーを取得し、指定された処理キーの値を「送信失敗」へ
	 * 変更する。他の処理キーの値は変更しない。
	 *
	 * @param jsonFileName 格納JSONファイル名
	 * @param mailProcessKey 送信失敗へ更新するメール処理キー
	 */
	public void updateNoticeFailed(String jsonFileName, String mailProcessKey) {
		updateNoticeFlag(jsonFileName, mailProcessKey, FAILED_FLAG, "送信失敗");
	}

	/**
	 * 処理キーの状態を指定されたフラグ値へ更新する共通処理。
	 * updateNoticeCompleted / updateNoticeFailed から呼ばれる。
	 *
	 * @param jsonFileName 格納JSONファイル名
	 * @param mailProcessKey 更新するメール処理キー
	 * @param flagValue JSONに書き込むフラグ値（"通知済"・"送信失敗"等）
	 * @param flagLabel ログ出力用のラベル（フラグ値と同じ文言でよい）
	 */
	private void updateNoticeFlag(
			String jsonFileName,
			String mailProcessKey,
			String flagValue,
			String flagLabel) {

		log.info("[{}] updateNoticeFlag({})開始 project={}, jsonFileName={}",
				CLASS_NAME, flagLabel, PROJECT_NAME, jsonFileName);

		if (jsonFileName == null || jsonFileName.isEmpty()) {
			log.warn("[{}] jsonFileNameが未指定のため処理をスキップします。",
					CLASS_NAME);
			return;
		}

		if (mailProcessKey == null || mailProcessKey.isEmpty()) {
			log.warn("[{}] mailProcessKeyが未指定のため処理をスキップします。jsonFileName={}",
					CLASS_NAME, jsonFileName);
			return;
		}

		String mailBucket = config.getS3BucketsMail();

		// 1. 既存JSONを取得
		Map<String, String> noticeMap = loadExistingNoticeMap(mailBucket, jsonFileName);

		// 2. 処理キーの存在確認
		if (!noticeMap.containsKey(mailProcessKey)) {
			log.warn("[{}] {}へ更新する処理キーがJSONに存在しません。"
					+ "処理キーを新規追加せず、処理をスキップします。"
					+ "bucket={}, key={}, mailProcessKey={}",
					CLASS_NAME, flagLabel, mailBucket, jsonFileName, mailProcessKey);
			return;
		}

		// 3. 指定されたフラグ値へ更新
		noticeMap.put(mailProcessKey, flagValue);

		// 4. S3へ書き戻し
		writeNoticeJson(mailBucket, jsonFileName, noticeMap);

		log.info("[{}] JSONの処理キーを{}へ更新しました。"
				+ "bucket={}, key={}, mailProcessKey={}",
				CLASS_NAME, flagLabel, mailBucket, jsonFileName, mailProcessKey);
	}

	/**
	 * 既存のJSON(Map)をS3から取得する。
	 *
	 * <p>jsonFileNameが未アップロードの場合は空Mapを返す。
	 * 取得・パースに失敗した場合も例外で処理を止めず、
	 * 空Mapにフォールバックして後続処理を継続する。
	 *
	 * @param mailBucket バケット名
	 * @param jsonFileName 対象JSONファイル名
	 * @return 既存のメール処理キー→フラグのMap
	 */
	private Map<String, String> loadExistingNoticeMap(
			String mailBucket,
			String jsonFileName) {

		if (!s3Operator.existsOnS3(mailBucket, jsonFileName)) {
			log.info(
					"[{}] 対象JSONがS3上に未アップロードのため、新規にMapを作成します。"
							+ "bucket={}, key={}",
					CLASS_NAME,
					mailBucket,
					jsonFileName);

			return new LinkedHashMap<>();
		}

		try {

			String existingJson = s3Operator.downloadTextUtf8(mailBucket, jsonFileName);
			if (existingJson == null || existingJson.isBlank()) {
				log.warn(
						"[{}] 既存JSONが空のため、新規にMapを作成します。"
								+ "bucket={}, key={}",
						CLASS_NAME,
						mailBucket,
						jsonFileName);
				return new LinkedHashMap<>();
			}

			return objectMapper.readValue(
					existingJson,
					objectMapper.getTypeFactory()
							.constructMapType(
									LinkedHashMap.class,
									String.class,
									String.class));
		} catch (Exception e) {
			log.error(
					"[{}] 既存JSONの取得/パースに失敗しました。"
							+ "空Mapから作り直して処理を継続します。"
							+ "bucket={}, key={}",
					CLASS_NAME,
					mailBucket,
					jsonFileName,
					e);

			return new LinkedHashMap<>();
		}
	}

	/**
	 * MapをJSONへ変換してS3へ書き込む。
	 *
	 * @param mailBucket バケット名
	 * @param jsonFileName JSONファイル名
	 * @param noticeMap メール処理キーと通知状態のMap
	 */
	private void writeNoticeJson(
			String mailBucket,
			String jsonFileName,
			Map<String, String> noticeMap) {

		// 1. MapをJSON文字列へ変換
		String updatedJson;
		try {
			updatedJson = objectMapper.writeValueAsString(noticeMap);
		} catch (JsonProcessingException e) {
			log.error(
					"[{}] JSONへの変換に失敗しました。bucket={}, key={}",
					CLASS_NAME,
					mailBucket,
					jsonFileName,
					e);
			throw new IllegalStateException(
					"JSONへの変換に失敗しました: " + jsonFileName,
					e);
		}

		// 2. S3へ書き込み
		try {
			s3Operator.putJson(
					mailBucket,
					jsonFileName,
					updatedJson);
		} catch (Exception e) {
			log.error(
					"[{}] S3への書き戻しに失敗しました。bucket={}, key={}",
					CLASS_NAME,
					mailBucket,
					jsonFileName,
					e);
			// 元の仕様に合わせ、S3書き込みエラーでは処理を止めない
			return;
		}
	}
}