package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.logger.ManageLoggerComponent;

@Component
public class ReadStat {

	private static final String PROJECT_NAME = ReadStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = ReadStat.class.getSimpleName();

	private static final String EXEC_MODE = "READ_FILE";

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** 既存CSVの「組み合わせ復元」専用：軽量インデックスを作る */
	public StatCsvIndexDTO readIndex(InputStream is, String key) {
		final String METHOD_NAME = "readIndex";

		manageLoggerComponent.init(EXEC_MODE, key);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		StatCsvIndexDTO dto = new StatCsvIndexDTO();
		dto.setKey(key);
		dto.setFileNo(extractFileNo(key));

		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

			// ヘッダ
			String line = br.readLine();
			if (line == null)
				return dto;

			// 先頭データ行（ここだけ split して category/home/away を取る）
			line = br.readLine();
			if (line == null)
				return dto;

			String[] parts = line.split(",", -1);
			if (parts.length >= 9) {
				dto.setCategory(parts[2]);
				dto.setHome(parts[5]);
				dto.setAway(parts[8]);
			}
			addSeq(dto, (parts.length > 0 ? parts[0] : null));

			// 以降は seq（先頭カンマまで）だけ抜く
			while ((line = br.readLine()) != null) {
				int comma = line.indexOf(',');
				String seqStr = (comma >= 0) ? line.substring(0, comma) : line;
				addSeq(dto, seqStr);
			}

			return dto;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e,
					"readIndex failed key=" + key);
			throw new RuntimeException(e);

		} finally {
			manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", "読み取りファイル名: " + key);
			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
		}
	}

	private static void addSeq(StatCsvIndexDTO dto, String s) {
		if (s == null)
			return;
		String t = s.trim();
		if (t.isEmpty())
			return;
		try {
			dto.getSeqs().add(Integer.parseInt(t));
		} catch (NumberFormatException ignore) {
		}
	}

	/** "stats/6770.csv" / "6770.csv" どっちでもOK */
	private static Integer extractFileNo(String key) {
		if (key == null)
			return null;

		String name = key;
		int slash = name.lastIndexOf('/');
		if (slash >= 0)
			name = name.substring(slash + 1);

		int dot = name.lastIndexOf('.');
		if (dot > 0)
			name = name.substring(0, dot);

		try {
			return Integer.parseInt(name);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
