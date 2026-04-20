package dev.web.api.bm_a015;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = PointSettingRemarksValidator.class)
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface ValidPointSettingRemarks {

	String message() default "remarksは「PK勝ち=数値,PK負け=数値」の形式で入力してください。";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
