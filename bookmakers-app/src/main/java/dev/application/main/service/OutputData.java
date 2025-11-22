package dev.application.main.service;

import java.lang.reflect.Field;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m021.TeamMatchFinalStatsEntity;
import dev.application.analyze.bm_m031.SurfaceOverviewEntity;
import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.domain.repository.bm.SurfaceOverviewRepository;
import dev.application.domain.repository.bm.TeamMatchFinalStatsRepository;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;

/**
 * dataテーブルCSV出力
 * @author shiraishitoshio
 *
 */
@Component
public class OutputData {

	/** ファイル名 */
	private static final String FILE = "/Users/shiraishitoshio/dumps/soccer_bm_dumps/soccer_bm_data.csv";

	/** ファイル名 */
	private static final String FILE4 = "/Users/shiraishitoshio/dumps/soccer_bm_data2.csv";

	/** BookDataRepository */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** ファイル名 */
	private static final String FILE2 = "/Users/shiraishitoshio/dumps/soccer_bm_surface_overview.csv";

	/** SurfaceOverviewRepository */
	@Autowired
	private SurfaceOverviewRepository surfaceOverviewRepository;

	/** ファイル名 */
	private static final String FILE3 = "/Users/shiraishitoshio/dumps/soccer_bm_team_match_final_stats.csv";

	/** TeamMatchFinalStatsRepository */
	@Autowired
	private TeamMatchFinalStatsRepository teamMatchFinalStatsRepository;

	@Autowired
	private FileMngWrapper mngWrapper;

	/**
	 * 実行
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute() throws IllegalArgumentException, IllegalAccessException {

		List<DataEntity> result = this.bookDataRepository.getData();
		StringBuilder header = new StringBuilder();
		boolean headFlg = true;
		int data = 1;
		for (DataEntity entity : result) {
			StringBuilder body = new StringBuilder();
			Field[] fields = DataEntity.class.getDeclaredFields();

			for (Field field : fields) {
				field.setAccessible(true);

				// フィールド名をスネークケースに変換
				String fieldName = toSnakeCase(field.getName());

				if ("serial_version_uid".equals(fieldName) || "file".equals(fieldName)
						|| "file_count".equals(fieldName)) {
					continue;
				}

				if (headFlg) {
					if (header.length() > 0) {
						header.append(",");
					}
					header.append(fieldName);
				}

				Object value = field.get(entity);
				if (body.length() > 0) {
					body.append(",");
				}
				body.append(value == null ? "" : value.toString());
			}

			if (headFlg) {
				header.append(",logic_flg");
				header.append(",register_id");
				header.append(",register_time");
				header.append(",update_id");
				header.append(",update_time");
				headFlg = false;
			}

			body.append(",0");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");

			// File
			mngWrapper.write(FILE, header.toString(), body.toString());
			System.out.println("filerecord: " + data + " / " + result.size());
			data++;
		}

		List<SurfaceOverviewEntity> result2 = this.surfaceOverviewRepository.getData();
		StringBuilder header2 = new StringBuilder();
		boolean headFlg2 = true;
		int data2 = 1;
		for (SurfaceOverviewEntity entity : result2) {
			StringBuilder body = new StringBuilder();
			Field[] fields = SurfaceOverviewEntity.class.getDeclaredFields();

			for (Field field : fields) {
				field.setAccessible(true);

				// フィールド名をスネークケースに変換
				String fieldName = toSnakeCase(field.getName());

				if ("serial_version_uid".equals(fieldName) || "file".equals(fieldName)
						|| "file_count".equals(fieldName) || "upd".equals(fieldName)
						|| "winFlg".equals(fieldName) || "loseFlg".equals(fieldName)
						|| "scoreFlg".equals(fieldName)) {
					continue;
				}

				if (headFlg2) {
					if (header2.length() > 0) {
						header2.append(",");
					}
					header2.append(fieldName);
				}

				Object value = field.get(entity);
				if (body.length() > 0) {
					body.append(",");
				}
				body.append(value == null ? "" : value.toString());
			}

			if (headFlg2) {
				header2.append(",logic_flg");
				header2.append(",register_id");
				header2.append(",register_time");
				header2.append(",update_id");
				header2.append(",update_time");
				headFlg2 = false;
			}

			body.append(",0");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");

			// File
			mngWrapper.write(FILE2, header2.toString(), body.toString());
			System.out.println("filerecord: " + data2 + " / " + result2.size());
			data2++;
		}

		List<TeamMatchFinalStatsEntity> result3 = this.teamMatchFinalStatsRepository.getData();
		StringBuilder header3 = new StringBuilder();
		boolean headFlg3 = true;
		int data3 = 1;
		for (TeamMatchFinalStatsEntity entity : result3) {
			StringBuilder body = new StringBuilder();
			Field[] fields = TeamMatchFinalStatsEntity.class.getDeclaredFields();

			for (Field field : fields) {
				field.setAccessible(true);

				// フィールド名をスネークケースに変換
				String fieldName = toSnakeCase(field.getName());

				if (headFlg3) {
					if (header3.length() > 0) {
						header3.append(",");
					}
					header3.append(fieldName);
				}

				Object value = field.get(entity);
				if (body.length() > 0) {
					body.append(",");
				}
				body.append(value == null ? "" : value.toString());
			}

			if (headFlg3) {
				header3.append(",logic_flg");
				header3.append(",register_id");
				header3.append(",register_time");
				header3.append(",update_id");
				header3.append(",update_time");
				headFlg3 = false;
			}

			body.append(",0");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");
			body.append(",bm_user");
			body.append(",2025-10-04 09:02:54+09");

			// File
			mngWrapper.write(FILE3, header3.toString(), body.toString());
			System.out.println("filerecord: " + data3 + " / " + result3.size());
			data3++;
		}

		// 結果構造：Map<"JPN-J1", Map<"HOME", List<DataEntity>>>
		List<String> reads = mngWrapper.read(FILE);
		int seq = 1;
		boolean headerFlg = false;
		String headers = "";
		for (String datas : reads) {
			String[] parts = datas.split(",", -1);
			if (!headerFlg) {
				headers = datas;
				headerFlg = true;
				continue;
			}

			String newData = "";
			int chk = 0;
			for (String part : parts) {
				if (chk == 0) {
					chk++;
					newData += seq;
					continue;
				}
				if (newData.length() > 0)
					newData += ",";
				newData += part;
				chk++;
			}
			// File
			mngWrapper.write(FILE4, headers, newData);
			System.out.println("filerecord: " + seq + " / " + reads.size());
			seq++;
		}
	}

	/**
	 * キャメルケースをスネークケースに変換するユーティリティメソッド
	 * 例: "userName" -> "user_name"
	 */
	private String toSnakeCase(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
	}
}
