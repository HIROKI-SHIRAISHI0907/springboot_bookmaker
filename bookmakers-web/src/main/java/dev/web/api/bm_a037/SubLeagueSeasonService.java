package dev.web.api.bm_a037;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.master.SubLeagueSeasonRepository;
import lombok.RequiredArgsConstructor;

/**
 * サブリーグ保有国リーグ登録設定（BM_A037。2026-11-09 追加）
 *
 * <ul>
 *   <li>{@link #findAll}: country_league_season_master の 国・リーグ ごとに、未設定・サブリーグあり（名前つき）・なし</li>
 *   <li>{@link #save}: 変更した 国・リーグ を保存する。全件 1 トランザクション（1 件でもエラーなら何も更新しない）。</li>
 *   <li>{@link #skipUnset}: 「設定しない」。未設定の 国・リーグ を全部「サブリーグなし」にする（設定済みのものは変えない）。</li>
 * </ul>
 * 未設定の 国・リーグ が残っていると、未対応チェック SUB_LEAGUE_UNSET で他の管理画面が開けない。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class SubLeagueSeasonService {

	/** サブリーグ名の長さの上限 */
	public static final int MAX_NAME_LENGTH = 100;

	/** 1 つの 国・リーグ のサブリーグの数の上限 */
	public static final int MAX_SUB_LEAGUES = 10;

	/** ログインユーザーが取れないときの更新者 */
	private static final String DEFAULT_UPDATE_ID = "ADMIN";

	private final SubLeagueSeasonRepository repository;

	private final DashboardAuthResolver authResolver;

	/** 一覧（国 → リーグ の順） */
	public List<SubLeagueSeasonDTO> findAll() {
		Map<String, SubLeagueSeasonRepository.Setting> settings = repository.findSettings();
		Map<String, List<String>> names = repository.findNames();
		List<SubLeagueSeasonDTO> list = new ArrayList<>();
		for (String[] t : repository.findTargets()) {
			String key = SubLeagueSeasonRepository.key(t[0], t[1]);
			SubLeagueSeasonDTO d = new SubLeagueSeasonDTO();
			d.setCountry(t[0]);
			d.setLeague(t[1]);
			SubLeagueSeasonRepository.Setting s = settings.get(key);
			if (s == null) {
				d.setStatus(SubLeagueSeasonDTO.STATUS_UNSET);
			} else if (SubLeagueSeasonRepository.FLG_HAS.equals(s.getSubLeagueFlg())) {
				d.setStatus(SubLeagueSeasonDTO.STATUS_HAS);
				d.setSubLeagues(new ArrayList<>(names.getOrDefault(key, List.of())));
			} else {
				d.setStatus(SubLeagueSeasonDTO.STATUS_NONE);
			}
			if (s != null) {
				d.setUpdateTime(s.getUpdateTime());
				d.setUpdateId(s.getUpdateId());
			}
			list.add(d);
		}
		return list;
	}

	/**
	 * 保存
	 * @throws IllegalArgumentException 入力が正しくないとき（画面に 400 で返す）
	 */
	public SubLeagueSeasonSaveResponse save(SubLeagueSeasonSaveRequest request) {
		List<SubLeagueSeasonItem> items = request == null || request.getItems() == null ? List.of() : request.getItems();
		if (items.isEmpty()) {
			throw new IllegalArgumentException("変更がありません。");
		}

		// 入力チェック（全部見てからまとめて返す）
		List<String> errors = new ArrayList<>();
		Map<String, SubLeagueSeasonItem> byKey = new LinkedHashMap<>();
		int no = 0;
		for (SubLeagueSeasonItem item : items) {
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
			String label = country + " / " + league;
			if (item.getHasSubLeague() == null) {
				errors.add(label + ": サブリーグの「あり」「なし」を選んでください");
				continue;
			}
			String key = SubLeagueSeasonRepository.key(country, league);
			if (byKey.containsKey(key)) {
				errors.add(label + ": 同じ国・リーグが重複しています");
				continue;
			}
			if (!repository.existsTarget(country, league)) {
				errors.add(label + ": シーズンデータ（country_league_season_master）にありません");
				continue;
			}

			List<String> names = new ArrayList<>();
			if (item.getHasSubLeague()) {
				Set<String> seen = new LinkedHashSet<>();
				boolean ng = false;
				for (String raw : item.getSubLeagues() == null ? List.<String>of() : item.getSubLeagues()) {
					String name = trim(raw);
					if (name == null) {
						continue;
					}
					if (name.length() > MAX_NAME_LENGTH) {
						errors.add(label + ": サブリーグ名は " + MAX_NAME_LENGTH + " 文字以内で入力してください（" + name + "）");
						ng = true;
					} else if (!seen.add(name)) {
						errors.add(label + ": サブリーグ名「" + name + "」が重複しています");
						ng = true;
					}
				}
				if (seen.isEmpty()) {
					errors.add(label + ": サブリーグありのときは、サブリーグ名を 1 つ以上入力してください");
					ng = true;
				} else if (seen.size() > MAX_SUB_LEAGUES) {
					errors.add(label + ": サブリーグは " + MAX_SUB_LEAGUES + " 個までです");
					ng = true;
				}
				if (ng) {
					continue;
				}
				names.addAll(seen);
			}

			SubLeagueSeasonItem v = new SubLeagueSeasonItem();
			v.setCountry(country);
			v.setLeague(league);
			v.setHasSubLeague(item.getHasSubLeague());
			v.setSubLeagues(names);
			byKey.put(key, v);
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(String.join("\n", errors));
		}

		String updateId = updateId();
		repository.inTransaction(() -> {
			for (SubLeagueSeasonItem v : byKey.values()) {
				repository.save(v.getCountry(), v.getLeague(),
						v.getHasSubLeague() ? SubLeagueSeasonRepository.FLG_HAS : SubLeagueSeasonRepository.FLG_NONE,
						v.getSubLeagues(), updateId);
			}
		});
		return new SubLeagueSeasonSaveResponse(byKey.size(), findAll());
	}

	/**
	 * 設定しない（未設定の 国・リーグ を全部「サブリーグなし」にする）
	 */
	public SubLeagueSeasonSaveResponse skipUnset() {
		int[] n = { 0 };
		String updateId = updateId();
		repository.inTransaction(() -> n[0] = repository.saveUnsetAsNone(updateId));
		return new SubLeagueSeasonSaveResponse(n[0], findAll());
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
