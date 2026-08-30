// dev/web/api/bm_u004/ResetPasswordConfirmRequest.java
package dev.web.api.bm_u004;

import lombok.Data;

@Data
public class ResetPasswordConfirmRequest {
	private String key;
	private String newPassword;
}