package dev.batch.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.entity.CountryLeagueMasterEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvFileNameService {

	private static final Pattern ROUND_NAME_PATTERN = Pattern.compile("(ラウンド\\s*[0-9０-９]+)");

	private final CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	/**
	 * B011用: 国・リーグ・ラウンドからフォルダ名を生成
	 * 例: 日本-J1-ラウンド12
	 */
	public String makeFolderName(String country, String league, String round) {
		String safeCountry = sanitizePathToken(defaultIfBlank(country, "unknown"));
		String safeLeague = sanitizePathToken(defaultIfBlank(league, "unknown"));
		String safeRound = sanitizePathToken(normalizeRoundName(round));

		return canonicalizeFolderSegment(safeCountry + "-" + safeLeague + "-" + safeRound);
	}

	/**
	 * B013用: 国・リーグの削除対象プレフィックスを生成
	 * 例: 日本-J1
	 *
	 * 方針: csv_id はハイフンで統一する
	 */
	public String makeFolderPrefix(String country, String league) {
		String safeCountry = sanitizePathToken(defaultIfBlank(country, "unknown"));
		String safeLeague = sanitizePathToken(defaultIfBlank(league, "unknown"));
		return canonicalizeFolderSegment(safeCountry + "-" + safeLeague);
	}

	/**
	 * home/away から国・リーグを引いて「国-リーグ」を返す
	 * 例: 日本-J1
	 */
	public String makeFolderNameFromTeams(String homeTeamName, String awayTeamName) {
		CountryLeagueName name = resolveCountryLeagueByTeams(homeTeamName, awayTeamName);
		return makeFolderPrefix(name.getCountry(), name.getLeague());
	}

	/**
	 * dataCategory からラウンド名を抽出
	 * 例: ラウンド12
	 */
	public String extractRoundName(String dataCategory) {
		if (dataCategory == null || dataCategory.isBlank()) {
			return "ラウンド不明";
		}

		Matcher matcher = ROUND_NAME_PATTERN.matcher(dataCategory);
		if (!matcher.find()) {
			return "ラウンド不明";
		}

		return normalizeRoundName(matcher.group(1));
	}

	/**
	 * csv_id / 物理ファイル相対パスをハイフン形式へ統一する。
	 *
	 * 例:
	 * - 日本: J2リーグ-ラウンド18/1.csv -> 日本-J2リーグ-ラウンド18/1.csv
	 * - 日本-J2リーグ - ラウンド 18/1.csv -> 日本-J2リーグ-ラウンド18/1.csv
	 */
	public String toPhysicalCsvId(String csvId) {
		return canonicalizeCsvId(csvId);
	}

	/**
	 * 外部からも使える csv_id 正規化メソッド
	 * 方針: フォルダ部分はハイフン、区切りは /、ファイル名はそのまま
	 */
	public String canonicalizeCsvId(String csvId) {
		if (csvId == null || csvId.isBlank()) {
			return csvId;
		}

		String normalized = Normalizer.normalize(csvId, Normalizer.Form.NFKC)
				.trim()
				.replace('\\', '/');

		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		normalized = normalized.replaceAll("/+", "/");

		if (normalized.isEmpty()) {
			return normalized;
		}

		String[] parts = normalized.split("/");
		List<String> normalizedParts = new ArrayList<>();

		for (int i = 0; i < parts.length; i++) {
			String part = safe(parts[i]).trim();
			if (part.isEmpty()) {
				continue;
			}

			// 最後のパートはファイル名なのでそのまま
			if (i == parts.length - 1) {
				normalizedParts.add(part);
			} else {
				normalizedParts.add(canonicalizeFolderSegment(part));
			}
		}

		return String.join("/", normalizedParts);
	}

	private CountryLeagueName resolveCountryLeagueByTeams(String homeTeamName, String awayTeamName) {
		String home = safe(homeTeamName).trim();
		String away = safe(awayTeamName).trim();

		try {
			CountryLeagueMasterEntity common =
					countryLeagueMasterBatchRepository.findCommonCountryLeagueByTeams(home, away);

			if (common != null) {
				return new CountryLeagueName(
						defaultIfBlank(common.getCountry(), "unknown"),
						defaultIfBlank(common.getLeague(), "unknown"));
			}

			CountryLeagueMasterEntity homeEntity =
					countryLeagueMasterBatchRepository.findActiveByTeam(home);

			if (homeEntity != null) {
				return new CountryLeagueName(
						defaultIfBlank(homeEntity.getCountry(), "unknown"),
						defaultIfBlank(homeEntity.getLeague(), "unknown"));
			}

			CountryLeagueMasterEntity awayEntity =
					countryLeagueMasterBatchRepository.findActiveByTeam(away);

			if (awayEntity != null) {
				return new CountryLeagueName(
						defaultIfBlank(awayEntity.getCountry(), "unknown"),
						defaultIfBlank(awayEntity.getLeague(), "unknown"));
			}
		} catch (Exception ignore) {
		}

		return new CountryLeagueName("unknown", "unknown");
	}

	private String normalizeRoundName(String round) {
		String value = safe(round).trim();
		if (value.isEmpty()) {
			return "ラウンド不明";
		}

		value = toHalfWidthDigits(value);
		value = value.replaceAll("\\s+", "");

		if (!value.startsWith("ラウンド")) {
			value = "ラウンド" + value;
		}

		return value;
	}

	/**
	 * フォルダセグメントをハイフン形式へ統一
	 * 例:
	 * - 日本: J1リーグ - ラウンド 12 -> 日本-J1リーグ-ラウンド12
	 */
	private String canonicalizeFolderSegment(String segment) {
		String s = Normalizer.normalize(safe(segment), Normalizer.Form.NFKC).trim();
		if (s.isEmpty()) {
			return "";
		}

		// 国: リーグ を 国-リーグ に統一
		s = s.replaceAll("\\s*:\\s*", "-");

		// ラウンド表記を統一
		s = s.replaceAll("[ 　]*-[ 　]*ラウンド[ 　]*([0-9]+)", "-ラウンド$1");

		// ハイフン前後空白を除去
		s = s.replaceAll("\\s*-\\s*", "-");

		// 連続ハイフンを圧縮
		s = s.replaceAll("-{2,}", "-");

		// 連続半角空白を圧縮
		s = s.replaceAll(" {2,}", " ").trim();

		return s;
	}

	private static String sanitizePathToken(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.trim()
				.replace("/", "_")
				.replace("\\", "_")
				.replace(":", "_")
				.replace("*", "_")
				.replace("?", "_")
				.replace("\"", "_")
				.replace("<", "_")
				.replace(">", "_")
				.replace("|", "_");
	}

	private static String toHalfWidthDigits(String in) {
		StringBuilder sb = new StringBuilder(in.length());
		for (char ch : in.toCharArray()) {
			if (ch >= '０' && ch <= '９') {
				sb.append((char) ('0' + (ch - '０')));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String defaultIfBlank(String value, String defaultValue) {
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Data
	private static final class CountryLeagueName {
		private final String country;
		private final String league;

		private CountryLeagueName(String country, String league) {
			this.country = country;
			this.league = league;
		}
	}
}
