package dev.web.api.bm_a025;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.BookDataRepository.BusinessGroupCountRow;
import dev.web.repository.master.FuturesRepository;
import lombok.RequiredArgsConstructor;

/**
 * RealTimeDataService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class RealTimeDataService {

	private final BookDataRepository repo;

	private final FuturesRepository futuresRepository;

	/**
	 * dataCategoryでグルーピングしたものを取得
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<RealTimeDataDTO> findGroupingAll() {
		return repo.findAllGrouping();
	}

	@Transactional(readOnly = true)
	public List<RealTimeDataDTO> search(RealTimeDataSearchCondition cond) {
		String home = cond.getHomeTeamName();
		String away = cond.getAwayTeamName();
		List<BusinessGroupCountRow> list = repo.findAllByDataCategory(home, away);
		List<RealTimeDataDTO> dto = new ArrayList<>();
		for (BusinessGroupCountRow row : list) {
			RealTimeDataDTO data = new RealTimeDataDTO();
			data.setHomeTeamName(row.homeTeamName);
			data.setAwayTeamName(row.homeTeamName);
			data.setDataCategory(row.dataCategory);
			dto.add(data);
		}
		return dto;
	}

	  /**
     * 更新
     * dataCategoryを新しい値にして、同一 home/away の組み合わせを持つ行すべてに上書きする。
     */
    @Transactional
    public RealTimeDataResponse update(RealTimeDataRequest dto) {
        RealTimeDataResponse res = new RealTimeDataResponse();

        try {
            int updatedData = repo.updateNewDataCategory(dto.getHomeTeamName(),
            		dto.getAwayTeamName(), dto.getDataCategory());
            int updatedFuture = futuresRepository.updateNewDataCategory(dto.getHomeTeamName(),
            		dto.getAwayTeamName(), dto.getDataCategory());
            if (updatedData >= 0 && updatedFuture >= 0) {
                res.setResponseCode("200");
                res.setMessage("更新成功しました。");
                return res;
            }
            res.setResponseCode("404");
            res.setMessage("更新対象が見つかりませんでした。");
            return res;
        } catch (Exception e) {
            res.setResponseCode("500");
            res.setMessage("システムエラーが発生しました。");
            return res;
        }
    }

}
