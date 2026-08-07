package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.common.entity.TeamMemberMasterEntity;

public class BmB002TeamMemberMasterBean {

	/** id → Entity の作業マップ */
	private final Map<Integer, TeamMemberMasterEntity> byId = new LinkedHashMap<>();

	/**
	 * 完全一致キー
	 *
	 * 基本方針:
	 * - 同一所属(同一team)の同一人物を探す
	 * - league 差異は無視したい（昇格/降格・リーグ名変更対策）
	 * - country 差異があっても、顔写真 / 生年月日などが強く一致すれば更新候補にしたい
	 *
	 * 実装:
	 * - facePicPath がある場合: team + member + facePicPath
	 * - facePicPath が無く birth がある場合: team + member + birth
	 * - face/birth がどちらも弱い場合のみ: country + team + member
	 */
	private final Map<String, TeamMemberMasterEntity> currentKeyMap = new HashMap<>();

	/**
	 * 同一人物候補（最強）
	 * member + birth + facePicPath
	 */
	private final Map<String, List<TeamMemberMasterEntity>> memberBirthFaceMap = new HashMap<>();

	/**
	 * 同一人物候補
	 * member + facePicPath
	 */
	private final Map<String, List<TeamMemberMasterEntity>> memberFaceMap = new HashMap<>();

	/**
	 * 同一人物候補
	 * member + birth
	 */
	private final Map<String, List<TeamMemberMasterEntity>> memberBirthMap = new HashMap<>();

	public BmB002TeamMemberMasterBean(List<TeamMemberMasterEntity> existingList) {
		if (existingList != null) {
			for (TeamMemberMasterEntity e : existingList) {
				if (!isBlank(e.getId())) {
					byId.put(e.getId(), copyOf(e));
				}
			}
		}
		rebuildIndexes();
	}

	// ─── 公開メソッド ────────────────────────────────────────────────────────

	public List<TeamMemberMasterEntity> getAllWorking() {
		return new ArrayList<>(byId.values());
	}

	/**
	 * Step1:
	 * 同一所属(team)の同一人物を完全一致で検索
	 */
	public TeamMemberMasterEntity findExactCurrent(TeamMemberMasterEntity incoming) {
		return currentKeyMap.get(currentKey(incoming));
	}

	/**
	 * Step2:
	 * team を無視して同一人物候補を解決
	 *
	 * 優先順位:
	 * 1. member + birth + facePicPath
	 * 2. member + facePicPath
	 * 3. member + birth
	 *
	 * ※ country は条件に含めない
	 *    -> country が変わっても same person なら更新扱いにしたいため
	 */
	public TeamMemberMasterEntity resolveSamePerson(TeamMemberMasterEntity incoming) {

		String strongKey = memberBirthFaceKey(incoming);
		if (!isBlank(strongKey)) {
			TeamMemberMasterEntity found = pickSingle(memberBirthFaceMap.get(strongKey));
			if (found != null) {
				return found;
			}
		}

		String faceKey = memberFaceKey(incoming);
		if (!isBlank(faceKey)) {
			TeamMemberMasterEntity found = pickSingle(memberFaceMap.get(faceKey));
			if (found != null) {
				return found;
			}
		}

		String birthKey = memberBirthKey(incoming);
		if (!isBlank(birthKey)) {
			TeamMemberMasterEntity found = pickSingle(memberBirthMap.get(birthKey));
			if (found != null) {
				return found;
			}
		}

		return null;
	}

	/**
	 * insert / update 後に作業マップとインデックスを更新する
	 */
	public void putWorking(TeamMemberMasterEntity updated) {
		if (updated == null || isBlank(updated.getId())) {
			return;
		}
		byId.put(updated.getId(), copyOf(updated));
		rebuildIndexes();
	}

	// ─── インデックス管理 ────────────────────────────────────────────────────

	private void rebuildIndexes() {
		currentKeyMap.clear();
		memberBirthFaceMap.clear();
		memberFaceMap.clear();
		memberBirthMap.clear();

		for (TeamMemberMasterEntity e : byId.values()) {
			currentKeyMap.put(currentKey(e), copyOf(e));
			addMulti(memberBirthFaceMap, memberBirthFaceKey(e), e);
			addMulti(memberFaceMap, memberFaceKey(e), e);
			addMulti(memberBirthMap, memberBirthKey(e), e);
		}
	}

	private void addMulti(Map<String, List<TeamMemberMasterEntity>> map,
			String key,
			TeamMemberMasterEntity e) {
		if (isBlank(key)) {
			return;
		}
		map.computeIfAbsent(key, k -> new ArrayList<>()).add(copyOf(e));
	}

	/**
	 * 候補が1件だけなら確定。
	 * 複数いて active（retireFlg != "1"）が1件だけなら確定。
	 * それ以外は曖昧なため null。
	 */
	private TeamMemberMasterEntity pickSingle(List<TeamMemberMasterEntity> list) {
		if (list == null || list.isEmpty()) {
			return null;
		}

		List<TeamMemberMasterEntity> uniq = new ArrayList<>(
				list.stream()
						.collect(Collectors.toMap(
								TeamMemberMasterEntity::getId,
								e -> e,
								(a, b) -> a,
								LinkedHashMap::new))
						.values());

		if (uniq.size() == 1) {
			return copyOf(uniq.get(0));
		}

		List<TeamMemberMasterEntity> active = uniq.stream()
				.filter(x -> !"1".equals(x.getRetireFlg()))
				.collect(Collectors.toList());

		if (active.size() == 1) {
			return copyOf(active.get(0));
		}

		return null;
	}

	// ─── キー生成 ────────────────────────────────────────────────────────────

	/**
	 * 完全一致キー
	 *
	 * 優先:
	 * 1. team + member + face
	 * 2. team + member + birth
	 * 3. country + team + member（弱一致 fallback）
	 */
	public static String currentKey(TeamMemberMasterEntity e) {
		String country = clean(e.getCountry());
		String team = clean(e.getTeam());
		String member = clean(e.getMember());
		String face = clean(e.getFacePicPath());
		String birth = clean(e.getBirth());

		if (!isBlank(team) && !isBlank(member) && !isBlank(face)) {
			return join(team, member, face);
		}
		if (!isBlank(team) && !isBlank(member) && !isBlank(birth)) {
			return join(team, member, birth);
		}
		return join(country, team, member);
	}

	/**
	 * 同一人物候補キー（最強）
	 * member + birth + facePicPath
	 */
	public static String memberBirthFaceKey(TeamMemberMasterEntity e) {
		String member = clean(e.getMember());
		String birth = clean(e.getBirth());
		String face = clean(e.getFacePicPath());

		if (isBlank(member) || isBlank(birth) || isBlank(face)) {
			return null;
		}
		return join(member, birth, face);
	}

	/**
	 * 同一人物候補キー
	 * member + birth
	 */
	public static String memberBirthKey(TeamMemberMasterEntity e) {
		String member = clean(e.getMember());
		String birth = clean(e.getBirth());

		if (isBlank(member) || isBlank(birth)) {
			return null;
		}
		return join(member, birth);
	}

	/**
	 * 同一人物候補キー
	 * member + facePicPath
	 */
	public static String memberFaceKey(TeamMemberMasterEntity e) {
		String member = clean(e.getMember());
		String face = clean(e.getFacePicPath());

		if (isBlank(member) || isBlank(face)) {
			return null;
		}
		return join(member, face);
	}

	// ─── Entity コピー ────────────────────────────────────────────────────────

	private TeamMemberMasterEntity copyOf(TeamMemberMasterEntity src) {
		TeamMemberMasterEntity e = new TeamMemberMasterEntity();
		e.setId(src.getId());
		e.setFile(src.getFile());
		e.setCountry(src.getCountry());
		e.setLeague(src.getLeague());
		e.setTeam(src.getTeam());
		e.setScore(src.getScore());
		e.setLoanBelong(src.getLoanBelong());
		e.setJersey(src.getJersey());
		e.setMember(src.getMember());
		e.setFacePicPath(src.getFacePicPath());
		e.setBelongList(src.getBelongList());
		e.setHeight(src.getHeight());
		e.setWeight(src.getWeight());
		e.setPosition(src.getPosition());
		e.setBirth(src.getBirth());
		e.setAge(src.getAge());
		e.setMarketValue(src.getMarketValue());
		e.setInjury(src.getInjury());
		e.setVersusTeamScoreData(src.getVersusTeamScoreData());
		e.setRetireFlg(src.getRetireFlg());
		e.setDeadline(src.getDeadline());
		e.setDeadlineContractDate(src.getDeadlineContractDate());
		e.setLatestInfoDate(src.getLatestInfoDate());
		e.setUpdStamp(src.getUpdStamp());
		e.setDelFlg(src.getDelFlg());
		e.setMissingCount(src.getMissingCount());
		return e;
	}

	// ─── 文字列ユーティリティ ─────────────────────────────────────────────────

	private static String join(String... parts) {
		return Arrays.stream(parts)
				.map(s -> s == null ? "" : s)
				.collect(Collectors.joining("||"));
	}

	private static String clean(String s) {
		if (s == null) {
			return null;
		}
		return s.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean isBlank(Integer s) {
		return s == null;
	}
}
