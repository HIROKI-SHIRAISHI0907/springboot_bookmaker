package dev.web.api.bm_a035;

/**
 * 管理画面の「未対応チェック」1つ分。
 * <p>
 * チェックを増やすときは、このインターフェースを実装したクラスを @Component で作り、
 * system_view_master に check_code が同じ行（対応する画面・ブロックするか）を登録するだけでよい。
 * system_view_master に行が無いチェックは、警告だけ（ブロックしない）として扱う。
 * </p>
 * @author shiraishitoshio
 */
public interface SetupCheck {

	/** チェックのコード（system_view_master.check_code と同じ値） */
	String code();

	/** 画面に出すチェック名（例: 新規シーズンの未確認） */
	String name();

	/** チェックを実行する（未対応の件数と内容） */
	SetupCheckResult check();
}
