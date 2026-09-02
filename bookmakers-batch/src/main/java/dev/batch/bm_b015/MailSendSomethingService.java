package dev.batch.bm_b015;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b096.MailSendBatchService;
import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;

/**
 * MailSendSomethingServiceロジック
 * <p>
 * 各種契機（ECSの稼働状況、シーズン終了間近のリーグなど）を検知し、
 * mail_send_manageへ送信予約を登録する「検知・登録」専用のバッチ。
 * 実際のメール送信はbm_b096.MailLaunchServiceが担う。
 * </p>
 *
 * @author shiraishitoshio
 *
 */
@Component
public class MailSendSomethingService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MailSendSomethingService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MailSendSomethingService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "MAIL_LAUNCH";

	/** リアルタイムスクレイピングECS稼働開始 */
	private static final String BATCH_MAIL_ID_004 = "bm-mail-004";

	/** リアルタイムスクレイピングECS稼働終了 */
	private static final String BATCH_MAIL_ID_005 = "bm-mail-005";

	/** シーズン終了間近のリーグのお知らせ */
	private static final String BATCH_MAIL_ID_006 = "bm-mail-006";

	/** ecs_slots_yyyy-MM-dd.json が置かれているS3バケット名 */
	private static final String ECS_SLOTS_BUCKET = "aws-s3-no-ecs-task-time-csv";

	/** ecs_slots_yyyy-MM-dd.json のファイル名プレフィックス */
	private static final String ECS_SLOTS_FILE_PREFIX = "ecs_slots_";

	/** DBのregister_time(UTC想定)をJVMのタイムゾーン(JST)起因のズレから補正するための時間 */
	private static final int TIMEZONE_CORRECTION_HOURS = 9;

	/** bikouの「実行日時」プレースホルダーキー */
	private static final String EXECUTED_AT_PLACEHOLDER = "EXECUTED_AT";

	/** bikouの「対象リーグ名」プレースホルダーキー */
	private static final String LEAGUE_NAME_PLACEHOLDER = "LEAGUE_NAME";

	/** bikouの「シーズン終了予定日」プレースホルダーキー */
	private static final String SEASON_END_DATE_PLACEHOLDER = "SEASON_END_DATE";

	/** bikouの「通知予定日」プレースホルダーキー */
	private static final String NOTICE_TIME_PLACEHOLDER = "NOTICE_TIME";

	/**
	 * bikou内で複数値（複数リーグ名・複数日付）を連結する際の区切り文字。
	 * <p>
	 * mail_send_manage.bikouは "KEY1=VALUE1,KEY2=VALUE2" 形式であり、
	 * "," はキー・バリューの組の区切りとして予約されているため、
	 * VALUE内に半角カンマを含めるとパースが壊れる。
	 * そのため、複数リーグ名・複数日付を1つのVALUEにまとめる際は、
	 * 半角カンマ ", " ではなく読点「、」を区切り文字として使用する。
	 * </p>
	 */
	private static final String MULTI_VALUE_DELIMITER = "、";

	/** シーズン終了間近とみなす閾値（本日から何日後までのend_season_dateを対象とするか） */
	private static final int SEASON_END_THRESHOLD_DAYS = 7;
	@Autowired
	private MailSendBatchService mailSendBatchService;
	@Autowired
	private MailSendBatchRepository mailSendBatchRepository;
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ObjectMapper objectMapper;

	/** システム通知（ECS稼働開始/終了、シーズン終了間近など）の送信元兼送り先アドレス */
	@Value("${mail.accounts.system.username}")
	private String sourceMailAddress;

	/**
	 * 契機処理によるメール送信予約バッチ実行
	 * <p>
	 * 1. bm-mail-004: リアルタイムスクレイピングECS稼働開始のお知らせ
	 * 2. bm-mail-005: リアルタイムスクレイピングECS稼働終了のお知らせ
	 * 3. bm-mail-006: シーズン終了間近のリーグのお知らせ
	 * </p>
	 * を検知し、mail_send_manageへ登録する。実際の送信はbm_b096.MailLaunchServiceが行う。
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// リアルタイムスクレイピングのECS稼働開始/終了を検知
		checkEcsStopIntervalsAndNotify(METHOD_NAME);
		// シーズン終了間近のリーグを検知
		checkSeasonEndingSoonAndNotify(METHOD_NAME);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * ecs_slots_yyyy-MM-dd.json（yyyy-MM-ddはJSTの本日日付）をS3から取得し、
	 * ecs_stop_intervals（ECSを止める時間帯の配列）と現在時刻（JST）を比較する。
	 *
	 * ・現在時刻がいずれかのstop interval内に入っている場合 → bm-mail-005（稼働終了）
	 * ・現在時刻がstop intervalの外で、直近に終わったintervalがある場合 → bm-mail-004（稼働開始）
	 *
	 * このバッチは繰り返し実行される想定のため、「その区切り（interval開始/終了）以降に
	 * 既に登録済みかどうか」をmail_send_manageのregister_timeで判定し、多重登録を防ぐ
	 * （{@link #notifyIfNotAlreadyRegistered(String, OffsetDateTime, OffsetDateTime)}）。
	 *
	 * ecs_slots_*.jsonがまだ存在しない場合や取得・パースに失敗した場合は、
	 * このバッチ全体（他の契機検知処理）を失敗させたくないのでログのみ出して継続する。
	 *
	 * @param callerMethodName 呼び出し元メソッド名（ログ用）
	 */
	private void checkEcsStopIntervalsAndNotify(String callerMethodName) {
		final String METHOD_NAME = "checkEcsStopIntervalsAndNotify";
		ZoneId jst = DateOffsetDecisionUtil.getZoneId();
		LocalDate todayJst = LocalDate.now(jst);
		String fileName = ECS_SLOTS_FILE_PREFIX + todayJst + ".json";

		String content;
		try {
			content = s3Operator.downloadTextUtf8(ECS_SLOTS_BUCKET, fileName);
		} catch (Exception e) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"ecs_slots取得に失敗したためECS稼働開始/終了通知はスキップします file=" + fileName + " error=" + e.getMessage());
			return;
		}
		if (content == null || content.isBlank()) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"ecs_slotsが未生成のためECS稼働開始/終了通知はスキップします file=" + fileName);
			return;
		}

		List<EcsStopInterval> intervals;
		try {
			intervals = parseStopIntervals(content);
		} catch (Exception e) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"ecs_slotsのパースに失敗したためECS稼働開始/終了通知はスキップします file=" + fileName + " error=" + e.getMessage());
			return;
		}

		OffsetDateTime now = OffsetDateTime.now(jst);

		// 現在時刻がいずれかのstop interval内に入っているか
		EcsStopInterval activeInterval = intervals.stream()
				.filter(iv -> !now.isBefore(iv.start()) && now.isBefore(iv.end()))
				.findFirst()
				.orElse(null);

		if (activeInterval != null) {
			// ECS停止時間帯に入っている → bm-mail-005（稼働終了）
			// 「この停止時間帯が始まって以降」に既に登録済みでなければ新規登録する
			notifyIfNotAlreadyRegistered(BATCH_MAIL_ID_005, activeInterval.start(), now);
			return;
		}

		// 停止時間帯の外 → 直近に終わったintervalを探す（複数ある場合は最も遅く終わったもの）
		EcsStopInterval lastEnded = intervals.stream()
				.filter(iv -> !iv.end().isAfter(now))
				.max(Comparator.comparing(EcsStopInterval::end))
				.orElse(null);

		if (lastEnded != null) {
			// 「このintervalが終わって以降」に既に登録済みでなければ新規登録する
			notifyIfNotAlreadyRegistered(BATCH_MAIL_ID_004, lastEnded.end(), now);
		}
	}

	/**
	 * JSON文字列からecs_stop_intervals配列を読み取る。
	 *
	 * @param content ecs_slots_*.jsonの中身
	 * @return {start, end}のリスト（無ければ空リスト）
	 * @throws Exception JSONのパースに失敗した場合
	 */
	private List<EcsStopInterval> parseStopIntervals(String content) throws Exception {
		List<EcsStopInterval> intervals = new ArrayList<>();
		JsonNode root = objectMapper.readTree(content);
		JsonNode intervalsNode = root.path("ecs_stop_intervals");
		if (intervalsNode.isArray()) {
			for (JsonNode node : intervalsNode) {
				OffsetDateTime start = OffsetDateTime.parse(node.path("start").asText());
				OffsetDateTime end = OffsetDateTime.parse(node.path("end").asText());
				intervals.add(new EcsStopInterval(start, end));
			}
		}
		return intervals;
	}

	/**
	 * 指定した境界時刻（interval開始 or 終了）以降に、指定mailIdが既にmail_send_manageへ
	 * 登録済みでなければ、新規に送信予約を登録する。
	 *
	 * mail_send_manage.register_timeはCURRENT_TIMESTAMP（Postgres側のUTC時刻）で入るが、
	 * JVMの既定タイムゾーンがJSTの場合、JDBCがこれをJSTとして読み込んでしまい、
	 * 実際より9時間過去の値として扱われる（PasswordResetService#isExpiredと同じ現象）。
	 * そのため、DBから読んだregister_timeには+9時間の補正をかけたうえで比較する。
	 *
	 * @param mailId      bm-mail-004 or bm-mail-005
	 * @param boundaryJst この時刻以降に送信登録済みかどうかを判定する境界（interval開始 or 終了、JST）
	 * @param nowJst      現在時刻（bikouのEXECUTED_AT用）
	 */
	private void notifyIfNotAlreadyRegistered(String mailId, OffsetDateTime boundaryJst, OffsetDateTime nowJst) {
		final String METHOD_NAME = "notifyIfNotAlreadyRegistered";
		Timestamp latestRegisterTimeUtc = mailSendBatchRepository.findLatestRegisterTime(mailId);
		if (latestRegisterTimeUtc != null) {
			Instant corrected = latestRegisterTimeUtc.toInstant().plusSeconds(TIMEZONE_CORRECTION_HOURS * 3600L);
			if (!corrected.isBefore(boundaryJst.toInstant())) {
				// この境界以降に既に登録済みなのでスキップ（多重送信防止）
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						"既に登録済みのため通知をスキップします mailId=" + mailId + " boundary=" + boundaryJst);
				return;
			}
		}

		// 通知の送信予約
		mailSendBatchService.send(mailId, sourceMailAddress,
				EXECUTED_AT_PLACEHOLDER + "=" + nowJst.toLocalDateTime());

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"ECS稼働開始/終了通知を登録しました mailId=" + mailId + " boundary=" + boundaryJst);
	}

	/**
	 * country_league_season_master から、end_season_date が
	 * 「本日（JST）から{@value #SEASON_END_THRESHOLD_DAYS}日後まで」の範囲にあるリーグを取得し、
	 * 該当するリーグが1件以上あればbm-mail-006（シーズン終了間近のお知らせ）を送信予約する。
	 *
	 * このバッチは1日に何度も実行される想定だが、bm-mail-006は対象期間中「1日1回」で
	 * よいため、本日（JST）中に既に送信登録済みであれば重複登録しない
	 * （{@link #hasAlreadyNotifiedToday(LocalDate)}）。
	 *
	 * 対象リーグが複数ある場合は、LEAGUE_NAME・SEASON_END_DATEともに
	 * {@link #MULTI_VALUE_DELIMITER}（読点「、」）区切りで1つのbikou値にまとめる
	 * （半角カンマはbikouのキー・バリュー区切りと衝突するため使用しない）。
	 *
	 * end_season_dateはtimestamptz（UTCで保持）のため、JSTの日付範囲
	 * （本日0時（JST）〜{@value #SEASON_END_THRESHOLD_DAYS}日後の翌日0時（JST）の半開区間）を
	 * UTCの瞬時点に変換したうえでrepositoryへ渡す。取得したend_season_dateを
	 * メール表示用のyyyy-MM-dd文字列へ整形する際も、JSTへ変換してから行う
	 * （DB・SQL側ではタイムゾーンを意識させず、JST変換はすべてこのクラスで行う）。
	 *
	 * country_league_season_masterの取得に失敗した場合は、このバッチ全体
	 * （他の契機検知処理）を失敗させたくないのでログのみ出して継続する。
	 *
	 * この通知は1週間前から毎日10:00に送信されるようにする
	 *
	 * @param callerMethodName 呼び出し元メソッド名（ログ用）
	 */
	private void checkSeasonEndingSoonAndNotify(String callerMethodName) {
		final String METHOD_NAME = "checkSeasonEndingSoonAndNotify";
		ZoneId jst = DateOffsetDecisionUtil.getZoneId();
		LocalDate todayJst = LocalDate.now(jst);
		LocalDate thresholdDateJst = todayJst.plusDays(SEASON_END_THRESHOLD_DAYS);

		// JSTの日付範囲（本日0時 〜 閾値日の翌日0時の半開区間）をUTCの瞬時点に変換する
		Timestamp fromInclusive = Timestamp.from(todayJst.atStartOfDay(jst).toInstant());
		Timestamp toExclusive = Timestamp.from(thresholdDateJst.plusDays(1).atStartOfDay(jst).toInstant());

		List<LeagueSeasonEndDTO> endingLeagues;
		try {
			endingLeagues = countryLeagueSeasonMasterBatchRepository.findLeaguesEndingBetween(fromInclusive, toExclusive);
		} catch (Exception e) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"country_league_season_masterの取得に失敗したためシーズン終了間近通知はスキップします error=" + e.getMessage());
			return;
		}
		if (endingLeagues == null || endingLeagues.isEmpty()) {
			return;
		}

		if (hasAlreadyNotifiedToday(todayJst)) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"本日は既にシーズン終了間近通知を登録済みのためスキップします mailId=" + BATCH_MAIL_ID_006);
			return;
		}

		String leagueNames = endingLeagues.stream()
				.map(LeagueSeasonEndDTO::getLeagueName)
				.collect(Collectors.joining(MULTI_VALUE_DELIMITER));
		String seasonEndDates = endingLeagues.stream()
				.map(dto -> dto.getEndSeasonDate().toInstant().atZone(jst).toLocalDate().toString())
				.collect(Collectors.joining(MULTI_VALUE_DELIMITER));

		OffsetDateTime nowJst = OffsetDateTime.now(jst);

		// 通知の送信予約
		mailSendBatchService.send(BATCH_MAIL_ID_006, sourceMailAddress,
				LEAGUE_NAME_PLACEHOLDER + "=" + leagueNames + ","
						+ SEASON_END_DATE_PLACEHOLDER + "=" + seasonEndDates + ","
						+ EXECUTED_AT_PLACEHOLDER + "=" + nowJst.toLocalDateTime() + ","
						+ NOTICE_TIME_PLACEHOLDER + "=" + "10:00:00+0900");

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"シーズン終了間近通知を登録しました mailId=" + BATCH_MAIL_ID_006 + " leagues=" + leagueNames);
	}

	/**
	 * bm-mail-006が本日（JST）中に既に送信登録済みかどうかを判定する。
	 *
	 * mail_send_manage.register_timeはCURRENT_TIMESTAMP（Postgres側のUTC時刻）で入るが、
	 * JVMの既定タイムゾーンがJSTの場合、JDBCがこれをJSTとして読み込んでしまい、
	 * 実際より9時間過去の値として扱われる（PasswordResetService#isExpiredと同じ現象）。
	 * そのため、DBから読んだregister_timeには+9時間の補正をかけたうえで、
	 * JST日付が本日と一致するかどうかで判定する（interval境界との比較ではなく、
	 * 「同じJST日付か」で判定する点がbm-mail-004/005とは異なる）。
	 *
	 * @param todayJst 本日日付（JST）
	 * @return 本日中に登録済みであればtrue
	 */
	private boolean hasAlreadyNotifiedToday(LocalDate todayJst) {
		Timestamp latestRegisterTimeUtc = mailSendBatchRepository.findLatestRegisterTime(BATCH_MAIL_ID_006);
		if (latestRegisterTimeUtc == null) {
			return false;
		}
		Instant corrected = latestRegisterTimeUtc.toInstant().plusSeconds(TIMEZONE_CORRECTION_HOURS * 3600L);
		LocalDate registeredDateJst = corrected.atZone(
				DateOffsetDecisionUtil.getZoneId()).toLocalDate();
		return registeredDateJst.isEqual(todayJst);
	}

	/**
	 * ECS停止時間帯（ecs_stop_intervals の1要素）。
	 */
	private static final class EcsStopInterval {

		private final OffsetDateTime start;
		private final OffsetDateTime end;

		/**
		 * @param start 停止開始時刻（JST）
		 * @param end   停止終了時刻（JST）
		 */
		private EcsStopInterval(OffsetDateTime start, OffsetDateTime end) {
			this.start = start;
			this.end = end;
		}

		private OffsetDateTime start() {
			return start;
		}

		private OffsetDateTime end() {
			return end;
		}
	}
}