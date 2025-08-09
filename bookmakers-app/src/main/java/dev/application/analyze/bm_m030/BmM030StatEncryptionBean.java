package dev.application.analyze.bm_m030;

import java.lang.reflect.Field;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

import dev.application.domain.repository.StatEncryptionRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

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
	private static final String CLASS_NAME = BmM030StatEncryptionBean.class.getSimpleName();

	/** アルゴリズム */
	@Value("${bmbusiness.hashAlgorithm:AES}")
	private static final String AES = "AES";

	/** アルゴリズム */
	@Value("${bmbusiness.hashAlgorithm:AES/CBC/PKCS5Padding}")
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
		fieldMap.put("homeFinalThirdPassCountInfoOnSuccessCount", BookDataEntity::getHomeFinalThirdPassCount);
		fieldMap.put("awayFinalThirdPassCountInfoOnSuccessCount", BookDataEntity::getAwayFinalThirdPassCount);
		fieldMap.put("homeCrossCountInfoOnSuccessCount", BookDataEntity::getHomeCrossCount);
		fieldMap.put("awayCrossCountInfoOnSuccessCount", BookDataEntity::getAwayCrossCount);
		fieldMap.put("homeTackleCountInfoOnSuccessCount", BookDataEntity::getHomeTackleCount);
		fieldMap.put("awayTackleCountInfoOnSuccessCount", BookDataEntity::getAwayTackleCount);
		fieldMap.put("homeClearCountInfo", BookDataEntity::getHomeClearCount);
		fieldMap.put("awayClearCountInfo", BookDataEntity::getAwayClearCount);
		fieldMap.put("homeInterceptCountInfo", BookDataEntity::getHomeInterceptCount);
		fieldMap.put("awayInterceptCountInfo", BookDataEntity::getAwayInterceptCount);
		FIELDMAP = Collections.unmodifiableMap(fieldMap);
	}

	/** 初期化
	 * @throws Exception */
	@PostConstruct
	public void init() throws Exception {
		final String METHOD_NAME = "init";
		SecretKeySpec keySpec = null;
		try {
			keySpec = new SecretKeySpec(this.bmm030Key.getBytes(), AES);
			// 共通鍵
			this.secretKey = keySpec;
			// 初期化ベクトル
			IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
			this.iv = ivSpec;
		} catch (Exception e) {
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					null);
		}

		// 暗号化データ全取得
		ConcurrentHashMap<String, StatEncryptionEntity> encMap = new ConcurrentHashMap<String, StatEncryptionEntity>();
		List<StatEncryptionEntity> allEntities = this.statEncryptionRepository.findAllEncData();
		for (StatEncryptionEntity entity : allEntities) {
			StatEncryptionEntity newEntity = new StatEncryptionEntity();
			String id = entity.getId();
			// key
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
				String fillErr = "home, away, team Err";
				this.loggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null, fillErr);
				this.loggerComponent.createBusinessException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						null,
						null);
			}
			// エンティティ詰め替え
			newEntity.setUpdFlg(true);
			newEntity.setId(id);
			newEntity.setCountry(country);
			newEntity.setLeague(league);
			newEntity.setHome(home);
			newEntity.setAway(away);
			newEntity.setTeam(team);
			newEntity.setChkBody(chkBody);
			Field[] fields = StatEncryptionEntity.class.getDeclaredFields();
			int i = 0;
			for (Field field : fields) {
				if (i < 9) {
					i++;
					continue;
				}
				field.setAccessible(true); // privateフィールドもアクセス可能にする
				String fieldName = null;
				String value = null;
				try {
					fieldName = field.getName();
					value = (String) field.get(entity);
					if (value == null || value.isBlank()) {
						i++;
						continue;
					}
					// 復号
					String standardData = null;
					try {
						standardData = decrypto(value, this.secretKey, this.iv);
					} catch (Exception e2) {
						String fillChar = "復号エラー: " + fieldName + ", " + value;
						this.loggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, "decrypto", null, e2, fillChar);
						break;
					}
					// 同じフィールド名を新Entityに探す
					Field targetField = StatEncryptionEntity.class.getDeclaredField(field.getName());
					targetField.setAccessible(true);
					targetField.set(newEntity, standardData);
				} catch (IllegalAccessException e) {
					String fillChar = "リフレクションエラー: " + fieldName + ", " + value;
					this.loggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e, fillChar);
					this.loggerComponent.createBusinessException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							null,
							null);
				}
				i++;
			}
			// mapに設定
			encMap.put(key, newEntity);
		}
		this.encMap = encMap;

		// 全フィールド取得（※順序は保証されない可能性あり）
		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		// 分析対象のフィールド範囲（homeExp 〜 awayInterceptCount）
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
			String fillChar = "startEncryptionIdx: " + startEncryptionIdx + ", endEncryptionIdx: " + endEncryptionIdx;
			String messageCd = "初期化エラー: 対象フィールド範囲なし";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		// 開始情報
		this.startEncryptionIdx = startEncryptionIdx;
		// 終了情報
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
		SecretKeySpec key = this.secretKey;
		IvParameterSpec iv = this.iv;
		// 書式:"アルゴリズム/ブロックモード/パディング方式"
		Cipher encrypter;
		byte[] encByte = null;
		try {
			encrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
			encrypter.init(Cipher.ENCRYPT_MODE, key, iv);
			encByte = encrypter.doFinal(plainText.getBytes());
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw e;
		}
		return new String(Base64.getEncoder().encode(encByte));
	}

	/**
	 * 暗号文を復号
	 * @param encText 暗号文
	 * @param key 共通鍵
	 * @param iv 初期化ベクトル
	 * @return
	 * @throws Exception
	 */
	public String decrypto(String encText, SecretKeySpec key, IvParameterSpec iv) throws Exception {
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
