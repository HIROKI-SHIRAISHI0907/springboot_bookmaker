package dev.application.analyze.bm_m002;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.ConditionResultDataRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadStat;

/**
 * condition_result_data の初期化用 Bean。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 集計の前準備として、次の3つを用意する。
 * </p>
 * <ol>
 *   <li>条件分岐データ（得点予測モデルの通知条件ファイル）の内容</li>
 *   <li>その内容のハッシュ値（condition_result_data の行を特定するキー）</li>
 *   <li>そのハッシュの既存件数（11項目）と、INSERT/UPDATE どちらにするかのフラグ</li>
 * </ol>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>条件ファイルが読めない（null・空）場合に "dummy" として集計を続けていたのを、例外で止めるよう変更。
 *       到達しない分岐（conditionData == null / hash == null）を削除。</li>
 *   <li>createBusinessException が例外を投げなかった場合に hash=null のまま DB 検索へ進むのを防ぐため、
 *       明示的に例外を投げるよう変更。</li>
 *   <li>ハッシュ計算の文字コードを UTF-8 に固定（環境によってハッシュ値が変わるのを防ぐ）。</li>
 *   <li>件数の読み込みを {@link ConditionResultDataEntity#toCountArray()} 経由に変更（並び順の定義を1か所に集約）。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>UTF-8 固定によるハッシュの変化</b>: これまで既定の文字コードが UTF-8 以外の環境で動かしていた場合、
 *       同じ条件ファイルでもハッシュ値が変わり、新しい行として集計が始まる
 *       （Mac・Java 18 以降は既定が UTF-8 のため通常は変わらない）。</li>
 *   <li><b>既定パスが個人のMacのパス</b>: {@code bmbusiness.aftercopypath} を設定しないと
 *       /Users/shiraishitoshio/... を読みに行く。サーバー環境では必ず設定すること。</li>
 *   <li><b>シングルトンで状態を持つ</b>: updFlg・hash・conditionCountList をフィールドに保持するため、
 *       同時に2本実行するとお互いの値を上書きする。</li>
 *   <li><b>同じハッシュの行が複数ある場合</b>: findByHash の先頭1件だけを使う。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class BmM002ConditionResultDataBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM002ConditionResultDataBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM002ConditionResultDataBean.class.getName();

	/** ハッシュアルゴリズム（既定: SHA-256） */
	@Value("${bmbusiness.hashAlgorithm:SHA-256}")
	private String hashAlgorithm = "SHA-256";

	/**
	 * 条件分岐データファイルのパス。
	 * 懸念: 既定値が個人環境のパス。本番では必ずプロパティで上書きすること。
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/conditiondata.csv}")
	private String findPath = "/Users/shiraishitoshio/bookmaker/conditiondata/conditiondata.csv";

	/** 統計データ読み取りクラス */
	@Autowired
	private ReadStat readStat;

	/** condition_result_dataレポジトリクラス */
	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 更新フラグ（true: 既存行あり→UPDATE / false: 新規→INSERT） */
	private boolean updFlg;

	/** 条件分岐データ（ファイル全文） */
	private String conditionData;

	/** ハッシュ（条件分岐データのダイジェスト） */
	private String hash;

	/** 既存件数（11項目。並び順は ConditionResultDataEntity#toCountArray() と同じ） */
	private String[] conditionCountList;

	/**
	 * 条件ファイルを読み、ハッシュ値と既存件数を取得してフィールドに設定する。
	 *
	 * <p>条件ファイルが読めない・ハッシュが計算できない場合は、誤った条件で集計しないよう例外で止める。</p>
	 *
	 * <p>懸念点: 呼び出しごとにフィールドを書き換えるため、並行実行に対応していない。</p>
	 */
	public void init() {
		final String METHOD_NAME = "init";

		String data;
		String digest;
		try {
			data = getConditionData();
			digest = extractHash(data);
		} catch (Exception e) {
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			String msgCd = MessageCdConst.MCD00002E_BATCH_EXECUTION_SKIP;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
			// 【修正】上の呼び出しが例外を投げない実装でも、hash=null のまま先へ進まないようにする
			throw new IllegalStateException("BM_M002 条件分岐データの初期化に失敗しました: " + fillChar, e);
		}

		this.conditionData = data;
		this.hash = digest;

		// hashを条件にDBから値を取得(1件の想定)
		List<ConditionResultDataEntity> resultDataEntities =
				this.conditionResultDataRepository.findByHash(digest);

		if (resultDataEntities != null && !resultDataEntities.isEmpty()) {
			// 懸念: 複数件ある場合は先頭のみ使用
			this.conditionCountList = resultDataEntities.get(0).toCountArray();
			this.updFlg = true;
		} else {
			String[] zero = new String[ConditionResultDataEntity.COUNT_SIZE];
			Arrays.fill(zero, "0");
			this.conditionCountList = zero;
			this.updFlg = false;
		}
	}

	/**
	 * 条件分岐データをファイルから読み取る。
	 *
	 * <p>【修正】取得できない（null・空）場合は "dummy" を返さず例外にする。
	 * ファイルの配置漏れやパス誤りのまま "dummy" 条件として件数が積み上がるのを防ぐ。</p>
	 *
	 * @return 条件分岐データ（null・空は返さない）
	 * @throws Exception ファイルが取得できない場合、または読み込み時の例外
	 */
	private String getConditionData() throws Exception {
		// TODO: 将来 S3 から取得する場合はここを差し替える
		String data = this.readStat.getConditionDataFileBody(this.findPath);
		if (data == null || data.isBlank()) {
			throw new IllegalStateException("条件分岐データが取得できません: path=" + this.findPath);
		}
		return data;
	}

	/**
	 * 条件分岐データからハッシュ値を導出する。
	 *
	 * <p>【修正】文字コードを UTF-8 に固定（実行環境によってハッシュ値が変わるのを防ぐ）。</p>
	 *
	 * @param conditionData 条件分岐データ
	 * @return ダイジェストを Base64 エンコードした文字列
	 * @throws NoSuchAlgorithmException hashAlgorithm が不正な場合
	 */
	private String extractHash(String conditionData) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(this.hashAlgorithm);
		byte[] cipherBytes = md.digest(conditionData.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(cipherBytes);
	}

	/**
	 * 条件分岐データを返す。
	 * @return 条件分岐データ（init() 前は null）
	 */
	public String getConditionKeyData() {
		return conditionData;
	}

	/**
	 * ハッシュ値を返す。
	 * @return ハッシュ値（init() 前は null）
	 */
	public String getHash() {
		return hash;
	}

	/**
	 * 既存件数を返す（コピーを返すため、呼び出し側で書き換えても Bean には影響しない）。
	 * @return 既存件数（11項目）
	 */
	public String[] getConditionCountList() {
		return conditionCountList == null ? null : conditionCountList.clone();
	}

	/**
	 * 更新フラグを返す。
	 * @return true: UPDATE / false: INSERT
	 */
	public boolean getUpdFlg() {
		return updFlg;
	}

}
