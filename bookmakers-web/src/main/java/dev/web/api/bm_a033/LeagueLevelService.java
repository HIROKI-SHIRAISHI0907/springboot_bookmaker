package dev.web.api.bm_a033;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.master.LeagueLevelWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * リーグレベル情報反映設定（BM_A033）
 *
 * <ul>
 *   <li>{@link #findAll}: 国・リーグ ごとのリーグレベル</li>
 *   <li>{@link #save}: 国・リーグ 単位で country_league_master の全行（削除・非表示の行も）に同じレベルを入れる。
 *       全件 1 トランザクション（1 件でもエラーなら何も更新しない）。</li>
 * </ul>
 * リーグレベルは同じ国の中で 1 = 最上位、数字が大きいほど下位。null = 未設定。
 * 同じ国で同じレベルのリーグが複数あってもよい（地域リーグなど）。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class LeagueLevelService {

	/** レベルの上限 */
	public static final int MAX_LEVEL = 99;

	/** ログインユーザーが取れないときの更新者 */
	private static final String DEFAULT_UPDATE_ID = "ADMIN";

	private final LeagueLevelWebRepository repository;

	private final DashboardAuthResolver authResolver;

	/** 一覧 */
	public List<LeagueLevelDTO> findAll() {
		return repository.findAll();
	}

	/**
	 * 保存
	 * @throws IllegalArgumentException 入力が正しくないとき（画面に 400 で返す）
	 */
	public LeagueLevelSaveResponse save(LeagueLevelSaveRequest request) {
		List<LeagueLevelItem> items = request == null || request.getItems() == null ? List.of() : request.getItems();
		if (items.isEmpty()) {
			throw new IllegalArgumentException("変更がありません。");
		}

		// 入力チェック（全部見てからまとめて返す）
		List<String> errors = new ArrayList<>();
		Map<String, LeagueLevelItem> byKey = new LinkedHashMap<>();
		int no = 0;
		for (LeagueLevelItem item : items) {
			no++;
			if (item == null) {
				continue;
			}
			String country = trim(item.getCountry());
			String league = trim(item.getLeague());
			if (country == null || league == null) {
				errors.add(no + "件目: 国・リーグは必須です");
				continue;
			}
			Integer level = item.getLeagueLevel();
			if (level != null && (level < 1 || level > MAX_LEVEL)) {
				errors.add(country + " / " + league + ": リーグレベルは 1〜" + MAX_LEVEL + " で入力してください");
				continue;
			}
			String key = country + "||" + league;
			if (byKey.containsKey(key)) {
				errors.add(country + " / " + league + ": 同じ国・リーグが重複しています");
				continue;
			}
			if (!repository.exists(country, league)) {
				errors.add(country + " / " + league + ": チームデータ（country_league_master）にありません");
				continue;
			}
			LeagueLevelItem v = new LeagueLevelItem();
			v.setCountry(country);
			v.setLeague(league);
			v.setLeagueLevel(level);
			byKey.put(key, v);
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(String.join("\n", errors));
		}

		String updateId = updateId();
		int[] rows = { 0 };
		int[] leagues = { 0 };
		repository.inTransaction(() -> {
			for (LeagueLevelItem v : byKey.values()) {
				int n = repository.updateLevel(v.getCountry(), v.getLeague(), v.getLeagueLevel(), updateId);
				rows[0] += n;
				if (n > 0) {
					leagues[0]++;
				}
			}
		});
		return new LeagueLevelSaveResponse(leagues[0], rows[0], repository.findAll());
	}

	private String updateId() {
		String name = authResolver.currentUserId();
		if (name == null || name.isBlank()) {
			return DEFAULT_UPDATE_ID;
		}
		String s = name.trim();
		return s.length() > 100 ? s.substring(0, 100) : s;
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String v = s.trim();
		return v.isEmpty() ? null : v;
	}
}
