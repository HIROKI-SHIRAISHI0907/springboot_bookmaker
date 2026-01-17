package dev.web.api.bm_u003;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;

	public FavoriteScope getScope(Long userId) {
		return favoriteRepository.findFavoriteScope(userId);
	}

	/**
	 * お気に入りをまとめて登録（country / league / team を item の内容で判定）
	 */
	@Transactional
	public FavoriteResponse upsert(FavoriteRequest req) {
		FavoriteResponse res = new FavoriteResponse();

		try {
			requireUser(req);
		} catch (Exception e) {
			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		if (req.getItems() == null || req.getItems().isEmpty()) {
			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		String operatorId = normalize(req.getOperatorId());
		if (operatorId.isEmpty())
			operatorId = "system";

		for (FavoriteItem item : req.getItems()) {

			String country = normalize(item.getCountry());
			String league = normalize(item.getLeague());
			String team = normalize(item.getTeam());

			if (country.isEmpty()) {
				res.setResponseCode("400");
				res.setMessage("必須項目が未入力です。（country）");
				return res;
			}

			int level;
			if (!team.isEmpty()) {
				if (league.isEmpty()) {
					res.setResponseCode("400");
					res.setMessage("必須項目が未入力です。(league)");
					return res;
				}
				level = 3;
			} else if (!league.isEmpty()) {
				level = 2;
			} else {
				level = 1;
			}

			// DB保存値：NULLではなく空文字
			String leagueValue = (level >= 2) ? league : "";
			String teamValue = (level == 3) ? team : "";

			try {
				favoriteRepository.insert(
						req.getUserId(),
						level,
						country,
						leagueValue,
						teamValue,
						operatorId);
			} catch (Exception e) {
				// 最低限これで原因がログに出ます
				e.printStackTrace();

				res.setResponseCode("404");
				res.setMessage("登録処理が失敗しました。");
				return res;
			}

		}
		res.setResponseCode("200");
		res.setMessage("登録処理が成功しました。");
		return res;
	}

	@Transactional
	public FavoriteResponse delete(Long userId, Long id) {
		FavoriteResponse res = new FavoriteResponse();
		try {
			favoriteRepository.deleteById(userId, id);
		} catch (Exception e) {
			res.setResponseCode("404");
			res.setMessage("削除処理が失敗しました。");
			return res;
		}
		res.setResponseCode("200");
		res.setMessage("削除処理が成功しました。");
		return res;
	}

	// -------------------
	// validation helpers
	// -------------------
	private static void requireUser(FavoriteRequest req) {
		if (req == null || req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required");
		}
	}

	private static String normalize(String v) {
		return v == null ? "" : v.trim();
	}
}
