package dev.batch.bm_b013;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import lombok.Data;

/**
 * 経由DTO
 * @author shiraishitoshio
 *
 */
@Data
public class TransactionDTO {

	/** countryLeagueMap */
	private Map<String, String> countryLeagueMap;

	/** フォーマッター */
	private DateTimeFormatter formatter;

	/** now */
	private LocalDateTime now;

}
