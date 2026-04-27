package dev.application.analyze.bm_m030;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

/**
 * stat_encryptionのbeanロジック
 * OOM対策版:
 * - 旧実装の全件ロードはしない
 * - 暗号化/復号と fieldMap 管理に専念する
 */
@Component
public class BmM030StatEncryptionBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM030StatEncryptionBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM030StatEncryptionBean.class.getName();

	private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
	private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	/** アルゴリズム */
	private static final String AES = "AES";

	/** アルゴリズム */
	private static final String AES_CBC_PKCS5Padding = "AES/CBC/PKCS5Padding";

	/** 鍵 */
	@Value("${bmbusiness.bmm030:bm030StatEncrypt}")
	private String bmm030Key = "bm030StatEncrypt";

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 共通鍵 */
	private SecretKeySpec secretKey;

	/** 初期化ベクトル */
	private static final byte[] FIXED_IV = new byte[16];

	/** 初期化ベクトル */
	private IvParameterSpec iv;

	/**
	 * 互換維持用Map
	 * 実行中の一時保管用途のみ
	 */
	private final ConcurrentHashMap<String, StatEncryptionEntity> encMap = new ConcurrentHashMap<>();

	/** 開始 */
	private int startEncryptionIdx = -1;

	/** 終了 */
	private int endEncryptionIdx = -1;

	/** フィールドマップ */
	private static final Map<String, Function<BookDataEntity, String>> FIELDMAP;
	static {
		Map<String, Function<BookDataEntity, String>> fieldMap = new LinkedHashMap<>();
		fieldMap.put("homeExpInfo", BookDataEntity::getHomeExp);
		fieldMap.put("awayExpInfo", BookDataEntity::getAwayExp);
		fieldMap.put("homeDonationInfo", BookDataEntity::getHomeBallPossesion);
		fieldMap.put("awayDonationInfo", BookDataEntity::getAwayBallPossesion);
		fieldMap.put("homeShootAllInfo", BookDataEntity::getHomeShootAll);
		fieldMap.put("awayShootAllInfo", BookDataEntity::getAwayShootAll);
		fieldMap.put("homeShootInInfo", BookDataEntity::getHomeShootIn);
		fieldMap.put("awayShootInInfo", BookDataEntity::getAwayShootIn);
		fieldMap.put("homeShootOutInfo", BookDataEntity::getHomeShootOut);
		fieldMap.put("awayShootOutInfo", BookDataEntity::getAwayShootOut);
		fieldMap.put("homeBlockShootInfo", BookDataEntity::getHomeShootBlocked);
		fieldMap.put("awayBlockShootInfo", BookDataEntity::getAwayShootBlocked);
		fieldMap.put("homeBigChanceInfo", BookDataEntity::getHomeBigChance);
		fieldMap.put("awayBigChanceInfo", BookDataEntity::getAwayBigChance);
		fieldMap.put("homeCornerInfo", BookDataEntity::getHomeCornerKick);
		fieldMap.put("awayCornerInfo", BookDataEntity::getAwayCornerKick);
		fieldMap.put("homeBoxShootInInfo", BookDataEntity::getHomeBoxShootIn);
		fieldMap.put("awayBoxShootInInfo", BookDataEntity::getAwayBoxShootIn);
		fieldMap.put("homeBoxShootOutInfo", BookDataEntity::getHomeBoxShootOut);
		fieldMap.put("awayBoxShootOutInfo", BookDataEntity::getAwayBoxShootOut);
		fieldMap.put("homeGoalPostInfo", BookDataEntity::getHomeGoalPost);
		fieldMap.put("awayGoalPostInfo", BookDataEntity::getAwayGoalPost);
		fieldMap.put("homeGoalHeadInfo", BookDataEntity::getHomeGoalHead);
		fieldMap.put("awayGoalHeadInfo", BookDataEntity::getAwayGoalHead);
		fieldMap.put("homeKeeperSaveInfo", BookDataEntity::getHomeKeeperSave);
		fieldMap.put("awayKeeperSaveInfo", BookDataEntity::getAwayKeeperSave);
		fieldMap.put("homeFreeKickInfo", BookDataEntity::getHomeFreeKick);
		fieldMap.put("awayFreeKickInfo", BookDataEntity::getAwayFreeKick);
		fieldMap.put("homeOffsideInfo", BookDataEntity::getHomeOffSide);
		fieldMap.put("awayOffsideInfo", BookDataEntity::getAwayOffSide);
		fieldMap.put("homeFoulInfo", BookDataEntity::getHomeFoul);
		fieldMap.put("awayFoulInfo", BookDataEntity::getAwayFoul);
		fieldMap.put("homeYellowCardInfo", BookDataEntity::getHomeYellowCard);
		fieldMap.put("awayYellowCardInfo", BookDataEntity::getAwayYellowCard);
		fieldMap.put("homeRedCardInfo", BookDataEntity::getHomeRedCard);
		fieldMap.put("awayRedCardInfo", BookDataEntity::getAwayRedCard);
		fieldMap.put("homeSlowInInfo", BookDataEntity::getHomeSlowIn);
		fieldMap.put("awaySlowInInfo", BookDataEntity::getAwaySlowIn);
		fieldMap.put("homeBoxTouchInfo", BookDataEntity::getHomeBoxTouch);
		fieldMap.put("awayBoxTouchInfo", BookDataEntity::getAwayBoxTouch);
		fieldMap.put("homePassCountInfoOnSuccessCount", BookDataEntity::getHomePassCount);
		fieldMap.put("awayPassCountInfoOnSuccessCount", BookDataEntity::getAwayPassCount);
		fieldMap.put("homeLongPassCountInfoOnSuccessCount", BookDataEntity::getHomeLongPassCount);
		fieldMap.put("awayLongPassCountInfoOnSuccessCount", BookDataEntity::getAwayLongPassCount);
		fieldMap.put("homeFinalThirdPassCountInfoOnSuccessCount", BookDataEntity::getHomeFinalThirdPassCount);
		fieldMap.put("awayFinalThirdPassCountInfoOnSuccessCount", BookDataEntity::getAwayFinalThirdPassCount);
		fieldMap.put("homeCrossCountInfoOnSuccessCount", BookDataEntity::getHomeCrossCount);
		fieldMap.put("awayCrossCountInfoOnSuccessCount", BookDataEntity::getAwayCrossCount);
		fieldMap.put("homeTackleCountInfoOnSuccessCount", BookDataEntity::getHomeTackleCount);
		fieldMap.put("awayTackleCountInfoOnSuccessCount", BookDataEntity::getAwayTackleCount);
		fieldMap.put("homeClearCountInfo", BookDataEntity::getHomeClearCount);
		fieldMap.put("awayClearCountInfo", BookDataEntity::getAwayClearCount);
		fieldMap.put("homeDuelCountInfo", BookDataEntity::getHomeDuelCount);
		fieldMap.put("awayDuelCountInfo", BookDataEntity::getAwayDuelCount);
		fieldMap.put("homeInterceptCountInfo", BookDataEntity::getHomeInterceptCount);
		fieldMap.put("awayInterceptCountInfo", BookDataEntity::getAwayInterceptCount);
		FIELDMAP = Collections.unmodifiableMap(fieldMap);
	}

	@PostConstruct
	public void initOnce() {
		final String METHOD_NAME = "initOnce";
		try {
			ensureCryptoInitialized();
			ensureIndexInitialized();
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "BmM030StatEncryptionBean init error");
		}
	}

	/**
	 * 互換維持用
	 * 旧実装ではここで全件ロードしていたが、OOM対策のため現在は何もしない
	 */
	public synchronized void init(String country, String league) throws Exception {
		ensureCryptoInitialized();
		ensureIndexInitialized();
	}

	public void resetForRun() {
		this.encMap.clear();
	}

	public String encrypto(String plainText) throws Exception {
		try {
			ensureCryptoInitialized();
			Cipher encrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			encrypter.init(Cipher.ENCRYPT_MODE, this.secretKey, this.iv);
			byte[] encByte = encrypter.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			return BASE64_ENCODER.encodeToString(encByte);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw e;
		}
	}

	private String decrypto(String encText, Cipher decrypter)
			throws IllegalBlockSizeException, BadPaddingException {
		byte[] byteText = BASE64_DECODER.decode(encText);
		return new String(decrypter.doFinal(byteText), StandardCharsets.UTF_8);
	}

	public String decrypto(String encText, SecretKeySpec key, IvParameterSpec iv) throws Exception {
		try {
			Cipher decrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			decrypter.init(Cipher.DECRYPT_MODE, key, iv);
			return decrypto(encText, decrypter);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw e;
		}
	}

	private void ensureCryptoInitialized() {
		if (this.secretKey == null) {
			this.secretKey = new SecretKeySpec(this.bmm030Key.getBytes(StandardCharsets.UTF_8), AES);
		}
		if (this.iv == null) {
			this.iv = new IvParameterSpec(FIXED_IV);
		}
	}

	private void ensureIndexInitialized() {
		if (this.startEncryptionIdx >= 0 && this.endEncryptionIdx >= 0) {
			return;
		}

		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		int startIdx = -1;
		int endIdx = -1;

		for (int i = 0; i < allFields.length; i++) {
			String name = allFields[i].getName();
			if (name.equals("homeExpInfo")) {
				startIdx = i;
			}
			if (name.equals("awayInterceptCountInfo")) {
				endIdx = i;
			}
		}

		this.startEncryptionIdx = startIdx;
		this.endEncryptionIdx = endIdx;
	}

	public String getBmm030Key() {
		return bmm030Key;
	}

	public SecretKeySpec getSecretKey() {
		return secretKey;
	}

	public ConcurrentHashMap<String, StatEncryptionEntity> getEncMap() {
		return encMap;
	}

	public Map<String, Function<BookDataEntity, String>> getFieldMap() {
		return FIELDMAP;
	}

	public int getStartEncryptionIdx() {
		return startEncryptionIdx;
	}

	public int getEndEncryptionIdx() {
		return endEncryptionIdx;
	}
}
