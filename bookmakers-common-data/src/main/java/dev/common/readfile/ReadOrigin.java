package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadOrigin implements ReadFileBodyIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadOrigin.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadOrigin.class.getName();

	/** 必須列数（0～103 を使うため 104 列必要） */
	private static final int REQUIRED_COLUMN_COUNT = 104;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 統計データファイルの中身を取得する
	 * @param is ストリーム名
	 * @param key キー
	 * @return readFileOutputDTO
	 */
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
		final String METHOD_NAME = "getFileBodyFromStream";

		ReadFileOutputDTO dto = new ReadFileOutputDTO();
		List<DataEntity> entityList = new ArrayList<>();

		try (
				BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
				CSVParser parser = CSVFormat.DEFAULT.builder()
						.setDelimiter(',')
						.setQuote('"')
						.setIgnoreSurroundingSpaces(false)
						.build()
						.parse(br)
		) {
			int row = 0;

			for (CSVRecord record : parser) {
				row++;

				// ヘッダスキップ
				if (row == 1) {
					continue;
				}

				// 空行スキップ
				if (record == null || record.size() == 0 || isRecordEffectivelyEmpty(record)) {
					continue;
				}

				// 必須列数チェック
				if (record.size() < REQUIRED_COLUMN_COUNT) {
					String msg = "CSV column shortage"
							+ " key=" + key
							+ " row=" + row
							+ " columnSize=" + record.size()
							+ " required=" + REQUIRED_COLUMN_COUNT;
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, null);
					continue;
				}

				DataEntity mappingDto = new DataEntity();
				mappingDto.setFile(key); // S3 key を保存

				mappingDto.setHomeRank(get(record, 0));
				mappingDto.setDataCategory(get(record, 1));
				mappingDto.setTimes(get(record, 2));
				mappingDto.setHomeTeamName(get(record, 3));
				mappingDto.setHomeScore(get(record, 4));
				mappingDto.setAwayRank(get(record, 5));
				mappingDto.setAwayTeamName(get(record, 6));
				mappingDto.setAwayScore(get(record, 7));
				mappingDto.setHomeExp(get(record, 8));
				mappingDto.setAwayExp(get(record, 9));
				mappingDto.setHomeInGoalExp(get(record, 10));
				mappingDto.setAwayInGoalExp(get(record, 11));
				mappingDto.setHomeDonation(get(record, 12));
				mappingDto.setAwayDonation(get(record, 13));
				mappingDto.setHomeShootAll(stripDotZero(get(record, 14)));
				mappingDto.setAwayShootAll(stripDotZero(get(record, 15)));
				mappingDto.setHomeShootIn(stripDotZero(get(record, 16)));
				mappingDto.setAwayShootIn(stripDotZero(get(record, 17)));
				mappingDto.setHomeShootOut(stripDotZero(get(record, 18)));
				mappingDto.setAwayShootOut(stripDotZero(get(record, 19)));
				mappingDto.setHomeBlockShoot(stripDotZero(get(record, 20)));
				mappingDto.setAwayBlockShoot(stripDotZero(get(record, 21)));
				mappingDto.setHomeBigChance(stripDotZero(get(record, 22)));
				mappingDto.setAwayBigChance(stripDotZero(get(record, 23)));
				mappingDto.setHomeCorner(stripDotZero(get(record, 24)));
				mappingDto.setAwayCorner(stripDotZero(get(record, 25)));
				mappingDto.setHomeBoxShootIn(stripDotZero(get(record, 26)));
				mappingDto.setAwayBoxShootIn(stripDotZero(get(record, 27)));
				mappingDto.setHomeBoxShootOut(stripDotZero(get(record, 28)));
				mappingDto.setAwayBoxShootOut(stripDotZero(get(record, 29)));
				mappingDto.setHomeGoalPost(stripDotZero(get(record, 30)));
				mappingDto.setAwayGoalPost(stripDotZero(get(record, 31)));
				mappingDto.setHomeGoalHead(stripDotZero(get(record, 32)));
				mappingDto.setAwayGoalHead(stripDotZero(get(record, 33)));
				mappingDto.setHomeKeeperSave(stripDotZero(get(record, 34)));
				mappingDto.setAwayKeeperSave(stripDotZero(get(record, 35)));
				mappingDto.setHomeFreeKick(stripDotZero(get(record, 36)));
				mappingDto.setAwayFreeKick(stripDotZero(get(record, 37)));
				mappingDto.setHomeOffside(stripDotZero(get(record, 38)));
				mappingDto.setAwayOffside(stripDotZero(get(record, 39)));
				mappingDto.setHomeFoul(stripDotZero(get(record, 40)));
				mappingDto.setAwayFoul(stripDotZero(get(record, 41)));
				mappingDto.setHomeYellowCard(stripDotZero(get(record, 42)));
				mappingDto.setAwayYellowCard(stripDotZero(get(record, 43)));
				mappingDto.setHomeRedCard(stripDotZero(get(record, 44)));
				mappingDto.setAwayRedCard(stripDotZero(get(record, 45)));
				mappingDto.setHomeSlowIn(stripDotZero(get(record, 46)));
				mappingDto.setAwaySlowIn(stripDotZero(get(record, 47)));
				mappingDto.setHomeBoxTouch(stripDotZero(get(record, 48)));
				mappingDto.setAwayBoxTouch(stripDotZero(get(record, 49)));
				mappingDto.setHomePassCount(get(record, 50));
				mappingDto.setAwayPassCount(get(record, 51));
				mappingDto.setHomeLongPassCount(get(record, 52));
				mappingDto.setAwayLongPassCount(get(record, 53));
				mappingDto.setHomeFinalThirdPassCount(get(record, 54));
				mappingDto.setAwayFinalThirdPassCount(get(record, 55));
				mappingDto.setHomeCrossCount(get(record, 56));
				mappingDto.setAwayCrossCount(get(record, 57));
				mappingDto.setHomeTackleCount(get(record, 58));
				mappingDto.setAwayTackleCount(get(record, 59));
				mappingDto.setHomeClearCount(stripDotZero(get(record, 60)));
				mappingDto.setAwayClearCount(stripDotZero(get(record, 61)));
				mappingDto.setHomeDuelCount(stripDotZero(get(record, 62)));
				mappingDto.setAwayDuelCount(stripDotZero(get(record, 63)));
				mappingDto.setHomeInterceptCount(stripDotZero(get(record, 64)));
				mappingDto.setAwayInterceptCount(stripDotZero(get(record, 65)));
				mappingDto.setRecordTime(get(record, 66));
				mappingDto.setWeather(get(record, 67));
				mappingDto.setTemparature(get(record, 68));
				mappingDto.setHumid(get(record, 69));
				mappingDto.setJudgeMember(get(record, 70));
				mappingDto.setHomeManager(get(record, 71));
				mappingDto.setAwayManager(get(record, 72));
				mappingDto.setHomeFormation(get(record, 73));
				mappingDto.setAwayFormation(get(record, 74));
				mappingDto.setStudium(get(record, 75));
				mappingDto.setCapacity(get(record, 76));
				mappingDto.setAudience(get(record, 77));
				mappingDto.setLocation(get(record, 78));
				mappingDto.setHomeMaxGettingScorer(get(record, 79));
				mappingDto.setAwayMaxGettingScorer(get(record, 80));
				mappingDto.setHomeMaxGettingScorerGameSituation(get(record, 81));
				mappingDto.setAwayMaxGettingScorerGameSituation(get(record, 82));
				mappingDto.setHomeTeamHomeScore(get(record, 83));
				mappingDto.setHomeTeamHomeLost(get(record, 84));
				mappingDto.setAwayTeamHomeScore(get(record, 85));
				mappingDto.setAwayTeamHomeLost(get(record, 86));
				mappingDto.setHomeTeamAwayScore(get(record, 87));
				mappingDto.setHomeTeamAwayLost(get(record, 88));
				mappingDto.setAwayTeamAwayScore(get(record, 89));
				mappingDto.setAwayTeamAwayLost(get(record, 90));
				mappingDto.setNoticeFlg(get(record, 91));
				mappingDto.setGameLink(get(record, 92));
				mappingDto.setGoalTime(get(record, 93));
				mappingDto.setGoalTeamMember(get(record, 94));
				mappingDto.setJudge(get(record, 95));
				mappingDto.setHomeTeamStyle(get(record, 96));
				mappingDto.setAwayTeamStyle(get(record, 97));
				mappingDto.setProbablity(get(record, 98));
				mappingDto.setPredictionScoreTime(get(record, 99));
				mappingDto.setMatchId(normalizeMatchId(get(record, 100).trim()));

				String timeSortSecondsRaw = get(record, 101).trim();
				try {
					mappingDto.setTimeSortSeconds(Integer.parseInt(timeSortSecondsRaw));
				} catch (Exception e) {
					String msg = "timeSortSeconds parse error"
							+ " key=" + key
							+ " row=" + row
							+ " data=" + timeSortSecondsRaw;
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, e);
					mappingDto.setTimeSortSeconds(1);
				}

				mappingDto.setAtThatTimes(get(record, 102));
				entityList.add(mappingDto);
			}

			dto.setResultCd(BookMakersCommonConst.NORMAL_CD);
			dto.setDataList(entityList);
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
	 * CSVRecord から安全に値を取得
	 * @param record CSVレコード
	 * @param index 列番号
	 * @return 値
	 */
	private static String get(CSVRecord record, int index) {
		if (record == null) {
			return "";
		}
		if (index < 0 || index >= record.size()) {
			return "";
		}
		String value = record.get(index);
		return value == null ? "" : value;
	}

	/**
	 * 実質空行判定
	 * @param record CSVレコード
	 * @return true: 空行
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

	/**
	 * ".0" を除去
	 * @param value 元値
	 * @return 加工後
	 */
	private static String stripDotZero(String value) {
		if (value == null) {
			return "";
		}
		return value.replace(".0", "");
	}

	/**
	 * matchidの正規化
	 * @param raw raw
	 * @return matchId
	 */
	private static String normalizeMatchId(String raw) {
		if (raw == null) {
			return null;
		}

		// ?mid=XXXX を最優先で拾う
		var m1 = java.util.regex.Pattern.compile("[?&#]mid=([A-Za-z0-9]+)").matcher(raw);
		if (m1.find()) {
			return m1.group(1);
		}

		// /match/{mid}/ …形式
		var m2 = java.util.regex.Pattern.compile("/match/([A-Za-z0-9]{6,20})(?:/|$)").matcher(raw);
		if (m2.find()) {
			return m2.group(1);
		}

		// それ以外はそのまま
		return raw.trim();
	}
}
