package dev.batch.service;

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

		return safeCountry + "-" + safeLeague + "-" + safeRound;
	}

	/**
	 * B013用: 国・リーグの削除対象プレフィックスを生成
	 * 例: 日本-J1
	 */
	public String makeFolderPrefix(String country, String league) {
		String safeCountry = sanitizePathToken(defaultIfBlank(country, "unknown"));
		String safeLeague = sanitizePathToken(defaultIfBlank(league, "unknown"));
		return safeCountry + ": " + safeLeague;
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
	 * CSVIDからファイル名に変換
	 */
	public String toPhysicalCsvId(String csvId) {
	    if (csvId == null || csvId.isBlank()) {
	        return csvId;
	    }

	    String normalized = csvId.trim().replace('\\', '/');

	    int slashIndex = normalized.lastIndexOf('/');
	    String folderPart = (slashIndex >= 0) ? normalized.substring(0, slashIndex) : normalized;
	    String filePart = (slashIndex >= 0) ? normalized.substring(slashIndex) : "";

	    // 1) 国名とリーグ名の区切りを、先頭の "-" だけ ":" に変換
	    //    例: 日本-J2 リーグ - ラウンド 18 -> 日本: J2 リーグ - ラウンド 18
	    if (!folderPart.contains(":")) {
	        int firstHyphen = folderPart.indexOf('-');
	        if (firstHyphen >= 0) {
	            String country = folderPart.substring(0, firstHyphen).trim();
	            String rest = folderPart.substring(firstHyphen + 1).trim();
	            folderPart = country + ": " + rest;
	        }
	    }

	    // 2) ラウンド表記を物理ファイル側ルールに寄せる
	    //    " - ラウンド 18" / "- ラウンド 18" / " -ラウンド18" などを "-ラウンド18" に統一
	    folderPart = folderPart.replaceAll("[ 　]*-[ 　]*ラウンド[ 　]*(\\d+)", "-ラウンド$1");

	    // 3) 余分な連続空白を整形（全角スペースは保持しつつ半角空白を圧縮）
	    folderPart = folderPart.replaceAll(" {2,}", " ").trim();

	    return folderPart + filePart;
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
