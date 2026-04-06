package dev.application.analyze.bm_m030;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

import dev.application.domain.repository.bm.StatEncryptionRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * stat_encryptionのbeanロジック
 * @author shiraishitoshio
 *
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

	private static final Field[] ENCRYPTION_FIELDS = buildEncryptionFields();

	/** アルゴリズム */
	private static final String AES = "AES";

	/** アルゴリズム */
	private static final String AES_CBC_PKCS5Padding = "AES/CBC/PKCS5Padding";

	/** 鍵 */
	@Value("${bmbusiness.bmm030:bm030StatEncrypt}")
	private String bmm030Key = "bm030StatEncrypt";

	/** stat_encryptionレポジトリクラス */
	@Autowired
	private StatEncryptionRepository statEncryptionRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 共通鍵 */
	private SecretKeySpec secretKey;

	/** 初期化ベクトル */
	private static final byte[] FIXED_IV = new byte[16];

	/** 初期化ベクトル */
	private IvParameterSpec iv;

	/** データマップ */
	private ConcurrentHashMap<String, StatEncryptionEntity> encMap;

	/** 開始 */
	private int startEncryptionIdx;

	/** 終了 */
	private int endEncryptionIdx;

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

	/** 初期化
	 * @throws Exception */
	public synchronized void init() throws Exception {
		final String METHOD_NAME = "init";

		// すでに初期化済みなら再読込しない
		if (this.encMap != null && !this.encMap.isEmpty()
				&& this.secretKey != null && this.iv != null) {
			return;
		}

		try {
			this.secretKey = new SecretKeySpec(this.bmm030Key.getBytes(StandardCharsets.UTF_8), AES);
			this.iv = new IvParameterSpec(FIXED_IV);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					null);
		}

		ConcurrentHashMap<String, StatEncryptionEntity> localEncMap = new ConcurrentHashMap<>();

		List<StatEncryptionEntity> allEntities = this.statEncryptionRepository.findAllEncData();

		Cipher decrypter;
		try {
			decrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			decrypter.init(Cipher.DECRYPT_MODE, this.secretKey, this.iv);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException
				| InvalidKeyException | InvalidAlgorithmParameterException e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, e.getMessage());
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					null);
			return;
		}

		for (StatEncryptionEntity entity : allEntities) {
			if (entity == null) {
				continue;
			}

			String key = buildEncKey(entity, METHOD_NAME);
			if (key == null) {
				continue;
			}

			StatEncryptionEntity newEntity = new StatEncryptionEntity();
			newEntity.setUpdFlg(true);
			newEntity.setId(entity.getId());
			newEntity.setCountry(entity.getCountry());
			newEntity.setLeague(entity.getLeague());
			newEntity.setHome(entity.getHome());
			newEntity.setAway(entity.getAway());
			newEntity.setTeam(entity.getTeam());
			newEntity.setChkBody(entity.getChkBody());

			boolean decryptError = false;

			for (Field field : ENCRYPTION_FIELDS) {
				String fieldName = field.getName();
				String value = null;

				try {
					value = (String) field.get(entity);
					if (value == null || value.isBlank()) {
						continue;
					}

					String standardData = decrypto(value, decrypter);
					field.set(newEntity, standardData);

				} catch (IllegalAccessException e) {
					String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
					String fillChar = "リフレクションエラー: " + fieldName + ", " + value;
					this.loggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
					this.loggerComponent.createBusinessException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							messageCd,
							null,
							null);
				} catch (Exception e) {
					String fillChar = "復号エラー: " + fieldName + ", " + value;
					this.loggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, "decrypto", null, e, fillChar);
					decryptError = true;
					break;
				}
			}

			if (!decryptError) {
				localEncMap.put(key, newEntity);
			}
		}

		this.encMap = localEncMap;

		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		int startEncryptionIdx = -1;
		int endEncryptionIdx = -1;
		for (int i = 0; i < allFields.length; i++) {
			String name = allFields[i].getName();
			if (name.equals("homeExpInfo"))
				startEncryptionIdx = i;
			if (name.equals("awayInterceptCountInfo"))
				endEncryptionIdx = i;
		}

		if (startEncryptionIdx == -1 || endEncryptionIdx == -1 || startEncryptionIdx > endEncryptionIdx) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillChar = "初期化エラー: 対象フィールド範囲なし(startEncryptionIdx: "
					+ startEncryptionIdx + ", endEncryptionIdx: " + endEncryptionIdx + ")";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					null);
		}

		this.startEncryptionIdx = startEncryptionIdx;
		this.endEncryptionIdx = endEncryptionIdx;
	}

	/**
	 * 平文を暗号化
	 * @param plainText 平文
	 * @param key 共通鍵
	 * @param iv 初期化ベクトル
	 * @return
	 * @throws Exception
	 */
	public String encrypto(String plainText) throws Exception {
		Cipher encrypter;
		byte[] encByte;
		try {
			encrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			encrypter.init(Cipher.ENCRYPT_MODE, this.secretKey, this.iv);
			encByte = encrypter.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw e;
		}
		return BASE64_ENCODER.encodeToString(encByte);
	}

	/**
	 * 暗号文を復号
	 * @param encText 暗号文
	 * @param key 共通鍵
	 * @param iv 初期化ベクトル
	 * @return
	 * @throws Exception
	 */
	private String decrypto(String encText, Cipher decrypter)
			throws IllegalBlockSizeException, BadPaddingException {
		byte[] byteText = BASE64_DECODER.decode(encText);
		return new String(decrypter.doFinal(byteText), StandardCharsets.UTF_8);
	}

	/**
	 * 暗号文を復号
	 * @param encText
	 * @param key
	 * @param iv
	 * @return
	 * @throws Exception
	 */
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

	/**
	 * 暗号フィールドビルダー
	 * @return
	 */
	private static Field[] buildEncryptionFields() {
		return Arrays.stream(StatEncryptionEntity.class.getDeclaredFields())
				.filter(field -> {
					String name = field.getName();
					return name.endsWith("Info") || name.endsWith("InfoOnSuccessCount");
				})
				.peek(field -> field.setAccessible(true))
				.toArray(Field[]::new);
	}

	/**
	 * 暗号キービルダー
	 * @param entity
	 * @param methodName
	 * @return
	 */
	private String buildEncKey(StatEncryptionEntity entity, String methodName) {
		String country = entity.getCountry();
		String league = entity.getLeague();
		String home = entity.getHome();
		String away = entity.getAway();
		String team = entity.getTeam();
		String chkBody = entity.getChkBody();

		String key = country + "-" + league;
		if (home != null && !home.isBlank() && away != null && !away.isBlank()) {
			key += ("-" + home + "-" + away + "-" + chkBody);
		} else if (team != null && !team.isBlank()) {
			key += ("-" + team + "-" + chkBody);
		} else {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillErr = "home, away, team Err";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, methodName, messageCd, null, fillErr);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					methodName,
					messageCd,
					null,
					null);
			return null;
		}
		return key;
	}

	/**
	 * 共通鍵
	 * @return
	 */
	public String getBmm030Key() {
		return bmm030Key;
	}

	/**
	 * 秘密鍵
	 * @return
	 */
	public SecretKeySpec getSecretKey() {
		return secretKey;
	}

	/**
	 * 標準マップ
	 * @return
	 */
	public ConcurrentHashMap<String, StatEncryptionEntity> getEncMap() {
		return encMap;
	}

	/**
	 * フィールドマップを返却する
	 * @return
	 */
	public Map<String, Function<BookDataEntity, String>> getFieldMap() {
		return FIELDMAP;
	}

	/**
	 * 開始情報を返却
	 * @return startEncryptionIdx
	 */
	public int getStartEncryptionIdx() {
		return startEncryptionIdx;
	}

	/**
	 * 終了情報を返却
	 * @return endEncryptionIdx
	 */
	public int getEndEncryptionIdx() {
		return endEncryptionIdx;
	}

}
