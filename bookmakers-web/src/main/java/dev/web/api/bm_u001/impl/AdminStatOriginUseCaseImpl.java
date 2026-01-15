package dev.web.api.bm_u001.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_u001.AdminStatOrigin;
import dev.web.api.bm_u001.StatSizeFinalizeRequest;
import dev.web.api.bm_u001.StatSizeFinalizeResponse;
import dev.web.api.bm_u001.StatSizeFinalizeService;
import dev.web.api.bm_u002.LogicFlgResponse;
import dev.web.api.bm_u002.LogicFlgService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatOriginUseCaseImpl implements AdminStatOrigin {

	private final StatSizeFinalizeService statSizeFinalizeService;

    private final LogicFlgService logicFlgService;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public StatSizeFinalizeResponse executeAll(StatSizeFinalizeRequest req) throws Exception {

		// 1) 先にStatSizeFinalize
        StatSizeFinalizeResponse res = statSizeFinalizeService.setStatFinalize(req);

        // 失敗なら例外にしてロールバック
        if (!"200".equals(res.getResponseCode())) {
            throw new IllegalStateException("StatSizeFinalize failed: " + res.getMessage());
        }

        // 2) 次にLogicFlg更新
        LogicFlgResponse logicRes = logicFlgService.execute();

        // 失敗なら例外にしてロールバック
        if (!"200".equals(logicRes.getResponseCode())) {
            throw new IllegalStateException("LogicFlg failed: " + logicRes.getMessage());
        }

        // 両方成功
        return res;
	}

}
