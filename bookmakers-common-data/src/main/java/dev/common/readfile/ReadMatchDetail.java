package dev.common.readfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.MatchDetailMatchEntity;
import dev.common.entity.MatchDetailMomentumEntity;
import dev.common.entity.MatchDetailMomentumIncidentEntity;
import dev.common.entity.MatchDetailSummaryEntity;
import dev.common.entity.MatchDetailGoalEntity;
import dev.common.entity.MatchDetailFormationEntity;
import dev.common.entity.MatchDetailLineupEntity;
import dev.common.entity.MatchDetailMissingEntity;
import dev.common.entity.MatchDetailMemberStatEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.MatchDetailDataDTO;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 試合詳細データ CSV 読み込みクラス
 *
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV を読む。
 * キーは &lt;試合日&gt;/&lt;フォルダ名&gt;/&lt;prefix&gt;_&lt;フォルダ名&gt;.csv。prefix（match / momentum / ... / memberStat）で
 * どのエンティティに入れるかを決める。</p>
 * <ul>
 *   <li>列は <b>ヘッダ名で</b> 取る（列の順番が変わっても読める。memberStat は取れたタブによって列が変わるため）。</li>
 *   <li>必須の列（試合ID など）がヘッダに無い場合はファイルの読み込みエラー（{@link BookMakersCommonConst#ERR_CD_ERR_FILE_READS}）。</li>
 *   <li>無い列は空文字。値はそのまま（「-」も変換しない）。</li>
 *   <li>memberStat の未知の列キー（flashscore 側で増えた項目）は extraStats に「キー=値;」で入れる。</li>
 *   <li>memberStat_&lt;タブ&gt; と memberStatHeader は memberStat と重複するので対象外（{@link #kindOf} が null を返す）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class ReadMatchDetail implements ReadFileBodyIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadMatchDetail.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadMatchDetail.class.getName();

	/** UTF-8 BOM（EF BB BF） */
	private static final int[] UTF8_BOM = { 0xEF, 0xBB, 0xBF };

	/** キー: &lt;試合日&gt;/&lt;フォルダ名&gt;/&lt;ファイル名&gt; */
	private static final Pattern KEY_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})/([^/]+)/([^/]+)\\.csv$");

	/** prefix: 試合の基本情報 */
	public static final String KIND_MATCH = "match";

	/** prefix: マッチモメンタム */
	public static final String KIND_MOMENTUM = "momentum";

	/** prefix: モメンタム上のアイコン */
	public static final String KIND_MOMENTUM_INCIDENT = "momentumIncident";

	/** prefix: サマリーの出来事 */
	public static final String KIND_SUMMARY = "summary";

	/** prefix: ゴール */
	public static final String KIND_GOAL = "goal";

	/** prefix: フォーメーション */
	public static final String KIND_FORMATION = "formation";

	/** prefix: ラインアップ */
	public static final String KIND_LINEUP = "lineup";

	/** prefix: 欠場選手 */
	public static final String KIND_MISSING = "missing";

	/** prefix: 選手スタッツ（全タブまとめ） */
	public static final String KIND_MEMBER_STAT = "memberStat";

	/** 読み込み対象の prefix */
	public static final List<String> TARGET_KINDS = Collections.unmodifiableList(Arrays.asList(
			KIND_MATCH,
			KIND_MOMENTUM,
			KIND_MOMENTUM_INCIDENT,
			KIND_SUMMARY,
			KIND_GOAL,
			KIND_FORMATION,
			KIND_LINEUP,
			KIND_MISSING,
			KIND_MEMBER_STAT));

	/** 共通の必須列 */
	private static final List<String> REQUIRED_COLUMNS = Arrays.asList("試合ID", "試合日", "ホームチーム", "アウェーチーム");

	/** memberStat の選手情報の列（スタッツ以外） */
	private static final Set<String> MEMBER_STAT_BASE_COLUMNS = new HashSet<>(Arrays.asList(
			"試合ID", "試合日", "ホームチーム", "アウェーチーム", "選手ID", "選手名", "ポジション", "側", "チーム"));

	/** memberStat の既知の列キー（setMemberStat で項目に入れるもの） */
	private static final Set<String> MEMBER_STAT_KNOWN_KEYS = new HashSet<>(Arrays.asList(
			"FS_RATING",
			"SHOTS_TOTAL",
			"EXPECTED_GOALS",
			"PASSES_ACCURATE",
			"TOUCHES_TOTAL",
			"TOUCHES_BOX_OPPOSITE",
			"DRIBBLES_WON",
			"DUELS_TOTAL",
			"GOALS",
			"EXPECTED_GOALS_ON_TARGET",
			"SHOTS_ON_TARGET",
			"SHOTS_OFF_TARGET",
			"SHOTS_BLOCKED",
			"SHOTS_BOX_IN",
			"SHOTS_BOX_OUT",
			"SHOTS_HEAD",
			"BIG_CHANCES_MISSED",
			"FOULS_SUFFERED",
			"OFFSIDES",
			"BIG_CHANCES_CREATED",
			"KEY_PASSES",
			"ASSISTS_GOAL",
			"EXPECTED_ASSISTS",
			"PASSES_FINAL_THIRD_ACCURATE",
			"LONG_BALLS_ACCURATE",
			"CROSSES_ACCURATE",
			"DUELS_WON",
			"DUELS_AERIAL_WON",
			"DUELS_GROUND_WON",
			"TACKLES_WON",
			"FOULS_COMMITTED",
			"INTERCEPTIONS",
			"CLEARANCES",
			"ERRORS_LEAD_TO_GOAL",
			"ERRORS_LEAD_TO_SHOT",
			"MATCH_MINUTES_PLAYED",
			"GOALS_OWN",
			"CARDS_YELLOW",
			"CARDS_RED",
			"SAVES_TOTAL",
			"GOALS_CONCEDED",
			"GOALS_PREVENTED",
			"EXPECTED_GOALS_ON_TARGET_FACED",
			"PUNCHES_TOTAL",
			"KEEPER_THROWS_TOTAL",
			"KEEPER_SWEEPER_TOTAL"));

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * キーから prefix（読み込み対象の種類）を返す。
	 * @param key S3 キー
	 * @return prefix。対象外（memberStat_&lt;タブ&gt;・memberStatHeader・manifest・形が違うキー）は null
	 */
	public static String kindOf(String key) {
		if (key == null) {
			return null;
		}
		Matcher m = KEY_PATTERN.matcher(key);
		if (!m.matches()) {
			return null;
		}
		String folder = m.group(2);
		String fileBase = m.group(3);
		String suffix = "_" + folder;
		if (!fileBase.endsWith(suffix)) {
			return null;
		}
		String kind = fileBase.substring(0, fileBase.length() - suffix.length());
		return TARGET_KINDS.contains(kind) ? kind : null;
	}

	/**
	 * 試合詳細データ CSV の中身を取得する
	 * @param is ストリーム
	 * @param key S3 キー
	 * @return readFileOutputDTO（matchDetailData の該当リストに中身が入る）
	 */
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
		final String METHOD_NAME = "getFileBodyFromStream";
		ReadFileOutputDTO dto = new ReadFileOutputDTO();
		MatchDetailDataDTO data = new MatchDetailDataDTO();

		String kind = kindOf(key);
		if (kind == null) {
			dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			dto.setErrMessage("試合詳細データの対象ファイルではありません: key=" + key);
			return dto;
		}

		String errCd = BookMakersCommonConst.NORMAL_CD;
		StringBuilder errMsg = new StringBuilder();

		try (
				InputStream bomSafeIs = skipUtf8Bom(is);
				BufferedReader br = new BufferedReader(new InputStreamReader(bomSafeIs, StandardCharsets.UTF_8));
				CSVParser parser = CSVFormat.DEFAULT.builder()
						.setDelimiter(',')
						.setQuote('"')
						.setHeader()
						.setSkipHeaderRecord(true)
						.setIgnoreSurroundingSpaces(false)
						.build()
						.parse(br)
		) {
			List<String> headers = parser.getHeaderNames();
			for (String col : REQUIRED_COLUMNS) {
				if (!headers.contains(col)) {
					dto.setExceptionProject(PROJECT_NAME);
					dto.setExceptionClass(CLASS_NAME);
					dto.setExceptionMethod(METHOD_NAME);
					dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
					dto.setErrMessage("必須の列がありません: column=" + col + " key=" + key);
					return dto;
				}
			}

			for (CSVRecord record : parser) {
				if (record == null || record.size() == 0 || isRecordEffectivelyEmpty(record)) {
					continue;
				}
				if (!record.isConsistent()) {
					// 列数がヘッダと違う行: 取れる列だけ取る（足りない列は空文字）
					errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
					String msg = "column count mismatch key=" + key + " row=" + record.getRecordNumber()
							+ " cols=" + record.size() + " header=" + headers.size();
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, null, msg);
					errMsg.append(msg).append("|| ");
				}
				addRecord(kind, record, key, data, headers);
			}

			dto.setResultCd(errCd);
			dto.setErrMessage(errMsg.toString());
			dto.setMatchDetailData(data);
			return dto;

		} catch (Exception e) {
			dto.setExceptionProject(PROJECT_NAME);
			dto.setExceptionClass(CLASS_NAME);
			dto.setExceptionMethod(METHOD_NAME);
			dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			dto.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			dto.setThrowAble(e);
			return dto;
		}
	}

	/**
	 * 1 行を種類に合ったエンティティにしてリストに入れる
	 */
	private void addRecord(String kind, CSVRecord rec, String key, MatchDetailDataDTO data, List<String> headers) {
		switch (kind) {
		case KIND_MATCH: {
			MatchDetailMatchEntity e = new MatchDetailMatchEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setCategory(get(rec, "試合国及びカテゴリ"));
			e.setHomeScore(get(rec, "ホームスコア"));
			e.setAwayScore(get(rec, "アウェースコア"));
			e.setStatus(get(rec, "状態"));
			e.setStartTime(get(rec, "開始日時"));
			e.setMatchUrl(get(rec, "試合URL"));
			e.setScrapedAt(get(rec, "取得日時(JST)"));
			data.getMatchList().add(e);
			break;
		}
		case KIND_MOMENTUM: {
			MatchDetailMomentumEntity e = new MatchDetailMomentumEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setHalf(get(rec, "ハーフ"));
			e.setHalfSeq(get(rec, "ハーフ内番号"));
			e.setMinuteLabel(get(rec, "分"));
			e.setMinute(get(rec, "分(数値)"));
			e.setAddedMinute(get(rec, "追加分"));
			e.setSide(get(rec, "優勢側"));
			e.setTeamName(get(rec, "優勢チーム"));
			e.setMomentumValue(get(rec, "モメンタム値"));
			e.setSignedMomentumValue(get(rec, "符号付きモメンタム値(ホーム+/アウェー-)"));
			data.getMomentumList().add(e);
			break;
		}
		case KIND_MOMENTUM_INCIDENT: {
			MatchDetailMomentumIncidentEntity e = new MatchDetailMomentumIncidentEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setSeq(get(rec, "番号"));
			e.setIncidentType(get(rec, "種類"));
			e.setMinuteLabel(get(rec, "おおよその分"));
			e.setMinute(get(rec, "分(数値)"));
			e.setAddedMinute(get(rec, "追加分"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			data.getMomentumIncidentList().add(e);
			break;
		}
		case KIND_SUMMARY: {
			MatchDetailSummaryEntity e = new MatchDetailSummaryEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setHalf(get(rec, "ハーフ"));
			e.setHalfScore(get(rec, "ハーフ終了時スコア"));
			e.setHalfSeq(get(rec, "ハーフ内順番"));
			e.setTimeLabel(get(rec, "時間"));
			e.setMinute(get(rec, "分(数値)"));
			e.setAddedMinute(get(rec, "追加分"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			e.setIncidentType(get(rec, "種類"));
			e.setPlayerName(get(rec, "選手名"));
			e.setPlayerUrl(get(rec, "選手URL"));
			e.setSubOutPlayerName(get(rec, "交代OUT選手名"));
			e.setSubOutPlayerUrl(get(rec, "交代OUT選手URL"));
			e.setScoreAtTime(get(rec, "その時点スコア"));
			e.setNote(get(rec, "補足"));
			e.setNote2(get(rec, "補足2(アシスト等)"));
			e.setDescription(get(rec, "説明"));
			data.getSummaryList().add(e);
			break;
		}
		case KIND_GOAL: {
			MatchDetailGoalEntity e = new MatchDetailGoalEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setTimeLabel(get(rec, "時間"));
			e.setMinute(get(rec, "分(数値)"));
			e.setAddedMinute(get(rec, "追加分"));
			e.setHalf(get(rec, "ハーフ"));
			e.setScoringSide(get(rec, "得点側"));
			e.setScoringTeamName(get(rec, "得点チーム"));
			e.setScorerName(get(rec, "得点者"));
			e.setScorerTeamName(get(rec, "得点者の所属チーム"));
			e.setOwnGoalFlg(get(rec, "オウンゴール"));
			e.setPenaltyFlg(get(rec, "PK"));
			e.setAssistName(get(rec, "アシスト"));
			e.setScoreAtTime(get(rec, "その時点スコア"));
			e.setIncidentType(get(rec, "種類"));
			e.setNote(get(rec, "補足"));
			e.setPlayerUrl(get(rec, "選手URL"));
			data.getGoalList().add(e);
			break;
		}
		case KIND_FORMATION: {
			MatchDetailFormationEntity e = new MatchDetailFormationEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			e.setFormation(get(rec, "フォーメーション"));
			e.setAverageRating(get(rec, "平均レーティング"));
			e.setCoachName(get(rec, "監督"));
			e.setCoachNationality(get(rec, "監督国籍"));
			e.setStarterCount(get(rec, "スタメン人数"));
			e.setPlayedCount(get(rec, "出場人数"));
			data.getFormationList().add(e);
			break;
		}
		case KIND_LINEUP: {
			MatchDetailLineupEntity e = new MatchDetailLineupEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			e.setLineupType(get(rec, "区分"));
			e.setPlayerId(get(rec, "選手ID"));
			e.setJerseyNumber(get(rec, "背番号"));
			e.setPlayerName(get(rec, "選手名"));
			e.setNationality(get(rec, "国籍"));
			e.setRole(get(rec, "役割"));
			e.setPlayedFlg(get(rec, "出場"));
			e.setSubInMinute(get(rec, "途中出場の分"));
			e.setSubInForPlayerName(get(rec, "途中出場で代わった選手"));
			e.setSubOutMinute(get(rec, "途中交代の分"));
			e.setSubOutForPlayerName(get(rec, "途中交代で入った選手"));
			e.setFinalRating(get(rec, "終了時レーティング"));
			e.setPitchRow(get(rec, "ピッチ行"));
			e.setPitchCol(get(rec, "ピッチ列"));
			e.setGoalCount(get(rec, "得点数"));
			e.setOwnGoalCount(get(rec, "オウンゴール数"));
			e.setEvents(get(rec, "イベント"));
			data.getLineupList().add(e);
			break;
		}
		case KIND_MISSING: {
			MatchDetailMissingEntity e = new MatchDetailMissingEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			e.setPlayerName(get(rec, "選手名"));
			e.setNationality(get(rec, "国籍"));
			e.setReason(get(rec, "理由"));
			e.setPlayerUrl(get(rec, "選手URL"));
			data.getMissingList().add(e);
			break;
		}
		case KIND_MEMBER_STAT: {
			MatchDetailMemberStatEntity e = new MatchDetailMemberStatEntity();
			e.setFile(key);
			e.setMatchId(get(rec, "試合ID"));
			e.setMatchDate(get(rec, "試合日"));
			e.setHomeTeamName(get(rec, "ホームチーム"));
			e.setAwayTeamName(get(rec, "アウェーチーム"));
			e.setPlayerId(get(rec, "選手ID"));
			e.setPlayerName(get(rec, "選手名"));
			e.setPosition(get(rec, "ポジション"));
			e.setSide(get(rec, "側"));
			e.setTeamName(get(rec, "チーム"));
			setMemberStat(e, rec, headers);
			data.getMemberStatList().add(e);
			break;
		}
		default:
			break;
		}
	}

	/**
	 * memberStat のスタッツ列（列キー）をエンティティに入れる。未知の列キーは extraStats に入れる。
	 */
	private static void setMemberStat(MatchDetailMemberStatEntity e, CSVRecord rec, List<String> headers) {
		e.setFsRating(get(rec, "FS_RATING"));
		e.setShotsTotal(get(rec, "SHOTS_TOTAL"));
		e.setExpectedGoals(get(rec, "EXPECTED_GOALS"));
		e.setPassesAccurate(get(rec, "PASSES_ACCURATE"));
		e.setTouchesTotal(get(rec, "TOUCHES_TOTAL"));
		e.setTouchesBoxOpposite(get(rec, "TOUCHES_BOX_OPPOSITE"));
		e.setDribblesWon(get(rec, "DRIBBLES_WON"));
		e.setDuelsTotal(get(rec, "DUELS_TOTAL"));
		e.setGoals(get(rec, "GOALS"));
		e.setExpectedGoalsOnTarget(get(rec, "EXPECTED_GOALS_ON_TARGET"));
		e.setShotsOnTarget(get(rec, "SHOTS_ON_TARGET"));
		e.setShotsOffTarget(get(rec, "SHOTS_OFF_TARGET"));
		e.setShotsBlocked(get(rec, "SHOTS_BLOCKED"));
		e.setShotsBoxIn(get(rec, "SHOTS_BOX_IN"));
		e.setShotsBoxOut(get(rec, "SHOTS_BOX_OUT"));
		e.setShotsHead(get(rec, "SHOTS_HEAD"));
		e.setBigChancesMissed(get(rec, "BIG_CHANCES_MISSED"));
		e.setFoulsSuffered(get(rec, "FOULS_SUFFERED"));
		e.setOffsides(get(rec, "OFFSIDES"));
		e.setBigChancesCreated(get(rec, "BIG_CHANCES_CREATED"));
		e.setKeyPasses(get(rec, "KEY_PASSES"));
		e.setAssistsGoal(get(rec, "ASSISTS_GOAL"));
		e.setExpectedAssists(get(rec, "EXPECTED_ASSISTS"));
		e.setPassesFinalThirdAccurate(get(rec, "PASSES_FINAL_THIRD_ACCURATE"));
		e.setLongBallsAccurate(get(rec, "LONG_BALLS_ACCURATE"));
		e.setCrossesAccurate(get(rec, "CROSSES_ACCURATE"));
		e.setDuelsWon(get(rec, "DUELS_WON"));
		e.setDuelsAerialWon(get(rec, "DUELS_AERIAL_WON"));
		e.setDuelsGroundWon(get(rec, "DUELS_GROUND_WON"));
		e.setTacklesWon(get(rec, "TACKLES_WON"));
		e.setFoulsCommitted(get(rec, "FOULS_COMMITTED"));
		e.setInterceptions(get(rec, "INTERCEPTIONS"));
		e.setClearances(get(rec, "CLEARANCES"));
		e.setErrorsLeadToGoal(get(rec, "ERRORS_LEAD_TO_GOAL"));
		e.setErrorsLeadToShot(get(rec, "ERRORS_LEAD_TO_SHOT"));
		e.setMatchMinutesPlayed(get(rec, "MATCH_MINUTES_PLAYED"));
		e.setGoalsOwn(get(rec, "GOALS_OWN"));
		e.setCardsYellow(get(rec, "CARDS_YELLOW"));
		e.setCardsRed(get(rec, "CARDS_RED"));
		e.setSavesTotal(get(rec, "SAVES_TOTAL"));
		e.setGoalsConceded(get(rec, "GOALS_CONCEDED"));
		e.setGoalsPrevented(get(rec, "GOALS_PREVENTED"));
		e.setExpectedGoalsOnTargetFaced(get(rec, "EXPECTED_GOALS_ON_TARGET_FACED"));
		e.setPunchesTotal(get(rec, "PUNCHES_TOTAL"));
		e.setKeeperThrowsTotal(get(rec, "KEEPER_THROWS_TOTAL"));
		e.setKeeperSweeperTotal(get(rec, "KEEPER_SWEEPER_TOTAL"));

		Map<String, String> extra = new LinkedHashMap<>();
		for (String h : headers) {
			if (MEMBER_STAT_BASE_COLUMNS.contains(h) || MEMBER_STAT_KNOWN_KEYS.contains(h)) {
				continue;
			}
			extra.put(h, get(rec, h));
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> en : extra.entrySet()) {
			sb.append(en.getKey()).append('=').append(en.getValue()).append(';');
		}
		e.setExtraStats(sb.toString());
	}

	/**
	 * BOM を読み飛ばす
	 */
	private static InputStream skipUtf8Bom(InputStream is) throws IOException {
		PushbackInputStream pis = new PushbackInputStream(is, UTF8_BOM.length);
		byte[] head = new byte[UTF8_BOM.length];
		int n = pis.read(head, 0, UTF8_BOM.length);
		boolean isBom = n == UTF8_BOM.length
				&& (head[0] & 0xFF) == UTF8_BOM[0]
				&& (head[1] & 0xFF) == UTF8_BOM[1]
				&& (head[2] & 0xFF) == UTF8_BOM[2];
		if (!isBom && n > 0) {
			pis.unread(head, 0, n);
		}
		return pis;
	}

	/**
	 * ヘッダ名で安全に値を取得（列が無い・行が短いときは空文字）
	 */
	private static String get(CSVRecord record, String name) {
		if (record == null || !record.isMapped(name) || !record.isSet(name)) {
			return "";
		}
		String value = record.get(name);
		return value == null ? "" : value;
	}

	/**
	 * 実質空行判定
	 */
	private static boolean isRecordEffectivelyEmpty(CSVRecord record) {
		for (int i = 0; i < record.size(); i++) {
			String v = record.get(i);
			if (v != null && !v.trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
