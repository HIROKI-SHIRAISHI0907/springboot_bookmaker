package dev.common.filemng;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * ファイル記入クラス
 * @author shiraishitoshio
 *
 */
public class FileMngWrapper {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FileMngWrapper.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FileMngWrapper.class.getSimpleName();

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * クラス内のどこか（フィールド定義部）に追加：英->日 見出しマップ
	 */
	private static final Map<String, String> JP_HEADER_MAP = new LinkedHashMap<>();
	static {
		JP_HEADER_MAP.put("seq", "通番");
		JP_HEADER_MAP.put("conditionResultDataSeqId", "条件分岐結果通番ID");
		JP_HEADER_MAP.put("dataCategory", "対戦チームカテゴリ");
		JP_HEADER_MAP.put("times", "試合時間");
		JP_HEADER_MAP.put("homeRank", "ホーム順位");
		JP_HEADER_MAP.put("homeTeamName", "ホームチーム");
		JP_HEADER_MAP.put("homeScore", "ホームスコア");
		JP_HEADER_MAP.put("awayRank", "アウェー順位");
		JP_HEADER_MAP.put("awayTeamName", "アウェーチーム");
		JP_HEADER_MAP.put("awayScore", "アウェースコア");
		JP_HEADER_MAP.put("homeExp", "ホーム期待値");
		JP_HEADER_MAP.put("awayExp", "アウェー期待値");
		JP_HEADER_MAP.put("homeDonation", "ホームポゼッション");
		JP_HEADER_MAP.put("awayDonation", "アウェーポゼッション");
		JP_HEADER_MAP.put("homeShootAll", "ホームシュート数");
		JP_HEADER_MAP.put("awayShootAll", "アウェーシュート数");
		JP_HEADER_MAP.put("homeShootIn", "ホーム枠内シュート");
		JP_HEADER_MAP.put("awayShootIn", "アウェー枠内シュート");
		JP_HEADER_MAP.put("homeShootOut", "ホーム枠外シュート");
		JP_HEADER_MAP.put("awayShootOut", "アウェー枠外シュート");
		JP_HEADER_MAP.put("homeBlockShoot", "ホームブロックシュート");
		JP_HEADER_MAP.put("awayBlockShoot", "アウェーブロックシュート");
		JP_HEADER_MAP.put("homeBigChance", "ホームビックチャンス");
		JP_HEADER_MAP.put("awayBigChance", "アウェービックチャンス");
		JP_HEADER_MAP.put("homeCorner", "ホームコーナーキック");
		JP_HEADER_MAP.put("awayCorner", "アウェーコーナーキック");
		JP_HEADER_MAP.put("homeBoxShootIn", "ホームボックス内シュート");
		JP_HEADER_MAP.put("awayBoxShootIn", "アウェーボックス内シュート");
		JP_HEADER_MAP.put("homeBoxShootOut", "ホームボックス外シュート");
		JP_HEADER_MAP.put("awayBoxShootOut", "アウェーボックス外シュート");
		JP_HEADER_MAP.put("homeGoalPost", "ホームゴールポスト");
		JP_HEADER_MAP.put("awayGoalPost", "アウェーゴールポスト");
		JP_HEADER_MAP.put("homeGoalHead", "ホームヘディングゴール");
		JP_HEADER_MAP.put("awayGoalHead", "アウェーヘディングゴール");
		JP_HEADER_MAP.put("homeKeeperSave", "ホームキーパーセーブ");
		JP_HEADER_MAP.put("awayKeeperSave", "アウェーキーパーセーブ");
		JP_HEADER_MAP.put("homeFreeKick", "ホームフリーキック");
		JP_HEADER_MAP.put("awayFreeKick", "アウェーフリーキック");
		JP_HEADER_MAP.put("homeOffside", "ホームオフサイド");
		JP_HEADER_MAP.put("awayOffside", "アウェーオフサイド");
		JP_HEADER_MAP.put("homeFoul", "ホームファウル");
		JP_HEADER_MAP.put("awayFoul", "アウェーファウル");
		JP_HEADER_MAP.put("homeYellowCard", "ホームイエローカード");
		JP_HEADER_MAP.put("awayYellowCard", "アウェーイエローカード");
		JP_HEADER_MAP.put("homeRedCard", "ホームレッドカード");
		JP_HEADER_MAP.put("awayRedCard", "アウェーレッドカード");
		JP_HEADER_MAP.put("homeSlowIn", "ホームスローイン");
		JP_HEADER_MAP.put("awaySlowIn", "アウェースローイン");
		JP_HEADER_MAP.put("homeBoxTouch", "ホームボックスタッチ");
		JP_HEADER_MAP.put("awayBoxTouch", "アウェーボックスタッチ");
		JP_HEADER_MAP.put("homePassCount", "ホームパス数");
		JP_HEADER_MAP.put("awayPassCount", "アウェーパス数");
		JP_HEADER_MAP.put("homeLongPassCount", "ホームロングパス数");
		JP_HEADER_MAP.put("awayLongPassCount", "アウェーロングパス数");
		JP_HEADER_MAP.put("homeFinalThirdPassCount", "ホームファイナルサードパス数");
		JP_HEADER_MAP.put("awayFinalThirdPassCount", "アウェーファイナルサードパス数");
		JP_HEADER_MAP.put("homeCrossCount", "ホームクロス数");
		JP_HEADER_MAP.put("awayCrossCount", "アウェークロス数");
		JP_HEADER_MAP.put("homeTackleCount", "ホームタックル数");
		JP_HEADER_MAP.put("awayTackleCount", "アウェータックル数");
		JP_HEADER_MAP.put("homeClearCount", "ホームクリア数");
		JP_HEADER_MAP.put("awayClearCount", "アウェークリア数");
		JP_HEADER_MAP.put("homeDuelCount", "ホームデュエル勝利数");
		JP_HEADER_MAP.put("awayDuelCount", "アウェーデュエル勝利数");
		JP_HEADER_MAP.put("homeInterceptCount", "ホームインターセプト数");
		JP_HEADER_MAP.put("awayInterceptCount", "アウェーインターセプト数");
		JP_HEADER_MAP.put("recordTime", "記録時間");
		JP_HEADER_MAP.put("weather", "天気");
		JP_HEADER_MAP.put("temparature", "気温");
		JP_HEADER_MAP.put("humid", "湿度");
		JP_HEADER_MAP.put("judgeMember", "審判");
		JP_HEADER_MAP.put("homeManager", "ホーム監督");
		JP_HEADER_MAP.put("awayManager", "アウェー監督");
		JP_HEADER_MAP.put("homeFormation", "ホームフォーメーション");
		JP_HEADER_MAP.put("awayFormation", "アウェーフォーメーション");
		JP_HEADER_MAP.put("studium", "スタジアム");
		JP_HEADER_MAP.put("capacity", "収容人数");
		JP_HEADER_MAP.put("audience", "観客数");
		JP_HEADER_MAP.put("homeMaxGettingScorer", "ホームチーム最大得点者");
		JP_HEADER_MAP.put("awayMaxGettingScorer", "アウェーチーム最大得点者");
		JP_HEADER_MAP.put("homeMaxGettingScorerGameSituation", "ホームチーム最大得点者出場状況");
		JP_HEADER_MAP.put("awayMaxGettingScorerGameSituation", "アウェーチーム最大得点者出場状況");
		JP_HEADER_MAP.put("homeTeamHomeScore", "ホームチームホーム得点数");
		JP_HEADER_MAP.put("homeTeamHomeLost", "ホームチームホーム失点数");
		JP_HEADER_MAP.put("awayTeamHomeScore", "アウェーチームホーム得点数");
		JP_HEADER_MAP.put("awayTeamHomeLost", "アウェーチームホーム失点数");
		JP_HEADER_MAP.put("homeTeamAwayScore", "ホームチームアウェー得点数");
		JP_HEADER_MAP.put("homeTeamAwayLost", "ホームチームアウェー失点数");
		JP_HEADER_MAP.put("awayTeamAwayScore", "アウェーチームアウェー得点数");
		JP_HEADER_MAP.put("awayTeamAwayLost", "アウェーチームアウェー失点数");
		JP_HEADER_MAP.put("noticeFlg", "通知フラグ");
		JP_HEADER_MAP.put("goalTime", "ゴール時間");
		JP_HEADER_MAP.put("goalTeamMember", "ゴール選手名");
		JP_HEADER_MAP.put("judge", "判定結果");
		JP_HEADER_MAP.put("homeTeamStyle", "ホームチームスタイル");
		JP_HEADER_MAP.put("awayTeamStyle", "アウェーチームスタイル");
		JP_HEADER_MAP.put("probablity", "確率");
		JP_HEADER_MAP.put("predictionScoreTime", "スコア予想時間");
	}

	/**
	 * ファイル記入メソッド
	 * @param filename
	 * @param body
	 */
	public void write(String filename, String body) {
		final String METHOD_NAME = "write";
		try (FileOutputStream fileOutputStream = new FileOutputStream(filename, true);
				BufferedWriter bufferedWriter = new BufferedWriter(
						new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))) {

			bufferedWriter.write(body);
			bufferedWriter.newLine();
		} catch (IOException e) {
			String messageCd = "ファイル記入エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
		}
	}

	/**
	 * Java の List#toString() で書かれた "[[1, 2], [3], [4,5,6]]" を
	 * List<List<Integer>> にパースする。
	 * フォーマットが崩れている場合は可能な範囲で読みます（不正トークンはスキップ）。
	 */
	public List<List<Integer>> readSeqBuckets(String filePath) {
		String raw = readAsString(filePath).trim();
		if (raw.isEmpty())
			return Collections.emptyList();

		// 余計な空白を削除（パース簡略化）
		String s = raw.replaceAll("\\s+", "");

		List<List<Integer>> result = new ArrayList<>();
		int depth = 0;
		int startInner = -1;

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '[') {
				depth++;
				// 深さが2になった瞬間に内側配列の開始
				if (depth == 2) {
					startInner = i + 1;
				}
			} else if (c == ']') {
				// 深さ2から1へ戻る瞬間が内側配列の終わり
				if (depth == 2 && startInner >= 0) {
					String inner = s.substring(startInner, i); // 例: "1,2,3"
					List<Integer> bucket = new ArrayList<>();
					if (!inner.isEmpty()) {
						String[] tokens = inner.split(",");
						for (String t : tokens) {
							if (!t.isEmpty() && t.matches("-?\\d+")) {
								try {
									bucket.add(Integer.parseInt(t));
								} catch (NumberFormatException ignore) {
									/* スキップ */ }
							}
						}
					}
					result.add(bucket);
					startInner = -1;
				}
				depth--;
			}
		}
		return result;
	}

	/** UTF-8で全体を1文字列で読み込み（存在しなければ空文字） */
	public String readAsString(String filePath) {
		try {
			Path p = Path.of(filePath);
			if (!Files.exists(p))
				return "";
			return Files.readString(p, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	/**
	 * CSVファイル記入メソッド
	 * @param filename
	 * @param body
	 */
	public void csvWrite(String filename, List<DataEntity> body) {
		final String METHOD_NAME = "csvWrite";
		if (body == null || body.isEmpty())
			return;

		try {
			// DataEntity のうち、static と file を除いた出力対象フィールドを宣言順で取得
			Field[] all = DataEntity.class.getDeclaredFields();
			List<Field> fields = new ArrayList<>();
			for (Field f : all) {
				if (Modifier.isStatic(f.getModifiers()))
					continue; // serialVersionUID など
				if ("file".equals(f.getName()))
					continue; // file は出力対象外
				f.setAccessible(true);
				fields.add(f);
			}

			File out = new File(filename);
			boolean writeHeader = !out.exists() || out.length() == 0L;

			StringBuilder sb = new StringBuilder(body.size() * fields.size() * 8);

			// ヘッダー行（日本語名）— 初回のみ
			if (writeHeader) {
				for (int i = 0; i < fields.size(); i++) {
					String fieldName = fields.get(i).getName();
					String headerJa = JP_HEADER_MAP.getOrDefault(fieldName, fieldName); // マッピング無ければ英名
					String esc = headerJa;
					if (esc.indexOf(',') >= 0 || esc.indexOf('"') >= 0
							|| esc.indexOf('\n') >= 0 || esc.indexOf('\r') >= 0) {
						esc = "\"" + esc.replace("\"", "\"\"") + "\"";
					}
					sb.append(esc);
					if (i < fields.size() - 1)
						sb.append(',');
				}
				sb.append('\n');
			}

			// データ行
			for (DataEntity row : body) {
				for (int i = 0; i < fields.size(); i++) {
					Object v = fields.get(i).get(row);
					String cell = (v == null) ? "" : String.valueOf(v);
					// CSVエスケープ
					if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
							|| cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
						cell = "\"" + cell.replace("\"", "\"\"") + "\"";
					}
					sb.append(cell);
					if (i < fields.size() - 1)
						sb.append(',');
				}
				sb.append('\n');
			}

			// 追記（UTF-8）
			try (BufferedWriter bw = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(out, true), StandardCharsets.UTF_8))) {
				bw.write(sb.toString());
			}
		} catch (IOException | IllegalAccessException e) {
			String messageCd = "ファイル記入エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
		}
	}

}
