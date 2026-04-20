package dev.web.api.bm_a015;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PointSettingRemarksValidator implements ConstraintValidator<ValidPointSettingRemarks, String> {

	/**
	 * 許可する形式:
	 * PK勝ち=2,PK負け=1
	 * PK勝ち = 2, PK負け = 1
	 *
	 * null / 空文字は許可
	 */
	private static final Pattern REMARKS_PATTERN = Pattern.compile(
			"^PK勝ち\\s*=\\s*\\d+\\s*,\\s*PK負け\\s*=\\s*\\d+$"
	);

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true;
		}
		return REMARKS_PATTERN.matcher(value.trim()).matches();
	}
}
