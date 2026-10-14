package dev.web.api.bm_a025;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.RealTimeDataCategoryRepository;
import dev.web.repository.master.FuturesRepository;
import dev.web.repository.master.TeamLeagueMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RealTimeDataService
 *
 * <p>2026-10 見直し</p>
 * <ul>
 *   <li>一覧に「国・リーグ」の判定（一致 / 不一致 / 混在 / 不明）と候補を付ける
 *       （country_league_master の両チームの候補の共通部分と照合）。</li>
 *   <li>search も一覧と同じ集計を返す（以前は1件だけで、形式の判定も付いていなかった）。</li>
 *   <li>update: 複数件を全部更新する（以前は1件目で return していた）。
 *       static_data が1件でも更新できれば成功（future_master に予定が無い試合でも 404 にしない）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeDataService {

	private final BookDataRepository repo;

	private final RealTimeDataCategoryRepository realTimeDataCategoryRepository;

	private final FuturesRepository futuresRepository;

	private final TeamLeagueMasterRepository teamLeagueMasterRepository;

	/**
	 * dataCategory × home × away でグルーピングしたもの（全件）
	 */
	@Transactional(readOnly = true)
	public List<RealTimeDataDTO> findGroupingAll() {
		return withLeagueCheck(realTimeDataCategoryRepository.findGrouping(null, null));
	}

	/**
	 * home / away で絞り込んだもの（指定された方だけ WHERE に効く）
	 */
	@Transactional(readOnly = true)
	public List<RealTimeDataDTO> search(RealTimeDataSearchCondition cond) {
		return withLeagueCheck(realTimeDataCategoryRepository.findGrouping(cond.getHomeTeamName(), cond.getAwayTeamName()));
	}

	/**
	 * 各行に、その対戦（home / away）の「国・リーグ」の判定と候補を付ける
	 */
	private List<RealTimeDataDTO> withLeagueCheck(List<RealTimeDataDTO> rows) {
		if (rows.isEmpty()) {
			return rows;
		}
		LeagueCandidateResolver resolver = new LeagueCandidateResolver(teamLeagueMasterRepository.findAllTeamLeagues());

		// 対戦ごとの今の dataCategory 一覧
		Map<String, List<String>> categoriesByPair = new HashMap<>();
		for (RealTimeDataDTO r : rows) {
			categoriesByPair.computeIfAbsent(pairKey(r), k -> new ArrayList<>()).add(r.getDataCategory());
		}

		Map<String, LeagueCandidateResolver.Result> resultByPair = new HashMap<>();
		for (RealTimeDataDTO r : rows) {
			LeagueCandidateResolver.Result res = resultByPair.computeIfAbsent(pairKey(r),
					k -> resolver.check(r.getHomeTeamName(), r.getAwayTeamName(), categoriesByPair.get(k)));
			r.setLeagueCheck(res.check);
			r.setLeagueCandidates(res.candidates);
		}
		return rows;
	}

	private static String pairKey(RealTimeDataDTO r) {
		return r.getHomeTeamName() + "\u0000" + r.getAwayTeamName();
	}

	/**
	 * 更新
	 * dataCategory を新しい値にして、同一 home/away の組み合わせを持つ行すべてに上書きする（static_data と future_master）。
	 */
	@Transactional
	public RealTimeDataResponse update(RealTimeDataRequest dto) {
		RealTimeDataResponse res = new RealTimeDataResponse();

		List<RealTimeDataSubDTO> req = dto.getRequestDTO();
		if (req == null || req.isEmpty()) {
			res.setResponseCode("400");
			res.setMessage("更新対象がありません。");
			return res;
		}

		try {
			int dataTotal = 0;
			int futureTotal = 0;
			List<String> notFound = new ArrayList<>();
			for (RealTimeDataSubDTO r : req) {
				if (isBlank(r.getHomeTeamName()) || isBlank(r.getAwayTeamName()) || isBlank(r.getDataCategory())) {
					notFound.add(r.getHomeTeamName() + " vs " + r.getAwayTeamName() + "（入力不足）");
					continue;
				}
				String category = r.getDataCategory().trim();
				int d = repo.updateNewDataCategory(r.getHomeTeamName(), r.getAwayTeamName(), category);
				int f = futuresRepository.updateNewDataCategory(r.getHomeTeamName(), r.getAwayTeamName(), category);
				dataTotal += d;
				futureTotal += f;
				if (d == 0) {
					notFound.add(r.getHomeTeamName() + " vs " + r.getAwayTeamName());
				}
			}

			if (dataTotal == 0) {
				res.setResponseCode("404");
				res.setMessage("更新対象が見つかりませんでした。" + (notFound.isEmpty() ? "" : " 対象: " + String.join(", ", notFound)));
				return res;
			}

			res.setResponseCode("200");
			StringBuilder msg = new StringBuilder("更新しました（static_data " + dataTotal + "件 / future_master " + futureTotal + "件）。");
			if (!notFound.isEmpty()) {
				msg.append(" 見つからなかった対戦: ").append(String.join(", ", notFound));
			}
			res.setMessage(msg.toString());
			return res;
		} catch (Exception e) {
			log.error("dataCategory 更新でエラー", e);
			// 例外を握りつぶすとロールバックされないため、明示的にロールバック指定する
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			res.setResponseCode("500");
			res.setMessage("システムエラーが発生しました。");
			return res;
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
