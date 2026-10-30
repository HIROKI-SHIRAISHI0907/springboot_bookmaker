package dev.web.api.bm_a035;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 未対応チェックの結果
 * @author shiraishitoshio
 */
@Data
public class SetupCheckResult {

	/** 未対応の件数（0 なら対応済み） */
	private int count;

	/** 未対応の内容（例: 「日本 / J1 リーグ」「FC今治 チームが J2 リーグ と J3 リーグ に重複しています。」） */
	private List<String> details = new ArrayList<>();

	public static SetupCheckResult of(List<String> details) {
		SetupCheckResult r = new SetupCheckResult();
		r.setDetails(details);
		r.setCount(details.size());
		return r;
	}
}
