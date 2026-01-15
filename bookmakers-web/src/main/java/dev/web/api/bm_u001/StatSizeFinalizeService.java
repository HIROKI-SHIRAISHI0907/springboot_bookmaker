package dev.web.api.bm_u001;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.StatSizeFinalizeMasterRepository;
import lombok.RequiredArgsConstructor;

/**
 * StatSizeFinalizeService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class StatSizeFinalizeService {

	/** StatSizeFinalizeMasterRepositoryクラス */
	private final StatSizeFinalizeMasterRepository statSizeFinalizeMasterRepository;

	/**
	 * 実行メソッド
	 */
	@Transactional
	public StatSizeFinalizeResponse setStatFinalize(StatSizeFinalizeRequest req) {
		StatSizeFinalizeResponse res = new StatSizeFinalizeResponse();

		// 必須チェック
		if (req.getSubList() == null) {
			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		for (SubInput input : req.getSubList()) {
			if (isBlank(input.getOptionNum())
					|| isBlank(input.getOptions())
					|| isBlank(input.getFlg())) {

				res.setResponseCode("400");
				res.setMessage("必須項目が未入力です。");
				return res;
			}
		}

		// リスト
		List<SubInput> list = req.getSubList();
		for (SubInput sub : list) {
			StatSizeFinalizeDTO entity = new StatSizeFinalizeDTO();
			entity.setOptionNum(sub.getOptionNum());
			entity.setOptions(sub.getOptions());
			entity.setFlg(sub.getFlg());
			List<StatSizeFinalizeDTO> data = this.statSizeFinalizeMasterRepository
					.findData(sub.getOptionNum(), sub.getOptions());
			if (!data.isEmpty()) {
				entity.setId(data.get(0).getId());
				int result = this.statSizeFinalizeMasterRepository.update(entity);
				if (result != 1) {
					res.setResponseCode("404");
					res.setMessage("処理が失敗しました。");
					return res;
				}
			} else {
				int result = this.statSizeFinalizeMasterRepository.insert(entity);
				if (result != 1) {
					res.setResponseCode("404");
					res.setMessage("処理が失敗しました。");
					return res;
				}
			}

		}
		res.setResponseCode("200");
		res.setMessage("処理が成功しました。");
		return res;
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
