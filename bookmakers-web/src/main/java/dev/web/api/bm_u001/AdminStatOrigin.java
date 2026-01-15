package dev.web.api.bm_u001;

/**
 * AdminStatOriginインターフェース
 * @author shiraishitoshio
 *
 */
public interface AdminStatOrigin {

	/** 実行ユースケース */
	public StatSizeFinalizeResponse executeAll(StatSizeFinalizeRequest req) throws Exception;

}
