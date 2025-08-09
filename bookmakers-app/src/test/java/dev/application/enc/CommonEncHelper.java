package dev.application.enc;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;

/**
 * 共通鍵暗号化復号共通
 * @author shiraishitoshio
 *
 */
public abstract class CommonEncHelper {

	/** アルゴリズム */
	private static final String AES = "AES";

	/** アルゴリズム */
	private static final String AES_CBC_PKCS5Padding = "AES/CBC/PKCS5Padding";

	/** 初期化ベクトル */
	private static final byte[] FIXED_IV = new byte[16];

	/** 鍵 */
	@Value("${bmbusiness.bmm030:bm030StatEncrypt}")
	private String bmm030Key = "bm030StatEncrypt";

	/**
	 * チェックエラー
	 * @param value 暗号化データ(共通鍵のみ)
	 * @return
	 * @throws Exception
	 */
	public String decChk(String value) throws Exception {
		if (value == null || value.isBlank()) return value;
		SecretKeySpec keySpec = null;
		try {
			keySpec = new SecretKeySpec(this.bmm030Key.getBytes(), AES);
			// 初期化ベクトル
			IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
			// 復号
			return decrypto(value, keySpec, ivSpec);
		} catch (Exception e) {
			throw new Exception("初期化ベクトル, 復号エラー: " + e.getCause());
		}
	}

	/**
	 * 暗号文を復号
	 * @param encText 暗号文
	 * @param key 共通鍵
	 * @param iv 初期化ベクトル
	 * @return
	 * @throws Exception
	 */
	private String decrypto(String encText, SecretKeySpec key, IvParameterSpec iv) throws Exception {
		byte[] byteText = Base64.getDecoder().decode(encText.getBytes());
		String decText = null;
		// 書式:"アルゴリズム/ブロックモード/パディング方式"
		try {
			Cipher decrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			decrypter.init(Cipher.DECRYPT_MODE, key, iv);
			decText = new String(decrypter.doFinal(byteText));
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw e;
		}
		return decText;
	}
}
