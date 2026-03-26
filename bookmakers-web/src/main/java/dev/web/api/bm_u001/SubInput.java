package dev.web.api.bm_u001;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * サービス共通用
 * @author shiraishitoshio
 */
@Data
public class SubInput {

	/** 選択肢No. */
	@NotBlank(message = "optionNumは必須です。")
	@Pattern(regexp = "^[12]$", message = "optionNumは 1 または 2 を指定してください。")
	private String optionNum;

	/** 選択肢 */
	@NotBlank(message = "optionsは必須です。")
	private String options;

	/** フラグ(0:有効,1:無効) */
	@NotBlank(message = "validFlgは必須です。")
	@Pattern(regexp = "^[01]$", message = "validFlgは 0 または 1 を指定してください。")
	private String validFlg;

	/**
	 * optionNum ごとの options 形式チェック
	 * optionNum=1 -> "1-2" のようなスコア形式
	 * optionNum=2 -> "日本:J1リーグ" のような 国:リーグ形式
	 */
	@AssertTrue(message = "optionsの形式が不正です。optionNum=1 は '数値-数値'、optionNum=2 は '国:リーグ' 形式で指定してください。")
	public boolean isOptionsFormatValid() {
		if (optionNum == null || optionNum.isBlank() ||
			options == null || options.isBlank()) {
			// null / blank 自体は各フィールドの @NotBlank に任せる
			return true;
		}

		String opNum = optionNum.trim();
		String opt = options.trim();

		switch (opNum) {
		case "1":
			// 例: 1-2, 0-0, 10-3
			return opt.matches("^\\d+\\s*-\\s*\\d+$");

		case "2":
			// 例: 日本:J1 リーグ, イングランド:プレミアリーグ
			return opt.matches("^[^:]+\\s*:\\s*.+$");

		default:
			return false;
		}
	}
}
