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
    private final Map<String, TeamMemberMasterEntity> byId = new LinkedHashMap<>();

    /**
     * 完全一致キー: team + member + facePicPath
     */
    private final Map<String, TeamMemberMasterEntity> currentKeyMap = new HashMap<>();

    /**
     * 同一人物候補: member + birth
     */
    private final Map<String, List<TeamMemberMasterEntity>> memberBirthMap = new HashMap<>();

    /**
     * 同一人物候補: member + facePicPath
     */
    private final Map<String, List<TeamMemberMasterEntity>> memberFaceMap = new HashMap<>();

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
     * Step1: 完全一致（team + member + facePicPath）で既存を検索
     */
    public TeamMemberMasterEntity findExactCurrent(TeamMemberMasterEntity incoming) {
        return currentKeyMap.get(currentKey(incoming));
    }

    /**
     * Step2: team を無視して同一人物候補を解決
     * 優先順位:
     *   1. member + birth
     *   2. member + facePicPath
     */
    public TeamMemberMasterEntity resolveSamePerson(TeamMemberMasterEntity incoming) {

        // 1. member + birth
        String bKey = memberBirthKey(incoming);
        if (!isBlank(bKey)) {
            TeamMemberMasterEntity found = pickSingle(memberBirthMap.get(bKey));
            if (found != null) return found;
        }

        // 2. member + facePicPath
        String fKey = memberFaceKey(incoming);
        if (!isBlank(fKey)) {
            TeamMemberMasterEntity found = pickSingle(memberFaceMap.get(fKey));
            if (found != null) return found;
        }

        return null;
    }

    /**
     * insert / update 後に作業マップとインデックスを更新する
     */
    public void putWorking(TeamMemberMasterEntity updated) {
        if (updated == null || isBlank(updated.getId())) return;
        byId.put(updated.getId(), copyOf(updated));
        rebuildIndexes();
    }

    // ─── インデックス管理 ────────────────────────────────────────────────────

    private void rebuildIndexes() {
        currentKeyMap.clear();
        memberBirthMap.clear();
        memberFaceMap.clear();

        for (TeamMemberMasterEntity e : byId.values()) {
            // 完全一致マップ（1件ずつ上書き: 同一キーで最後の1件を使う）
            currentKeyMap.put(currentKey(e), copyOf(e));

            // 候補マップ（複数件を保持）
            addMulti(memberBirthMap, memberBirthKey(e), e);
            addMulti(memberFaceMap, memberFaceKey(e), e);
        }
    }

    private void addMulti(Map<String, List<TeamMemberMasterEntity>> map,
                          String key,
                          TeamMemberMasterEntity e) {
        if (isBlank(key)) return;
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(copyOf(e));
    }

    /**
     * 候補が1件だけなら確定。
     * 複数いて active（retireFlg != "1"）が1件だけなら確定。
     * それ以外は同名別人の可能性があるため null（誤判定防止）。
     */
    private TeamMemberMasterEntity pickSingle(List<TeamMemberMasterEntity> list) {
        if (list == null || list.isEmpty()) return null;

        // id で重複排除
        List<TeamMemberMasterEntity> uniq = new ArrayList<>(
                list.stream()
                    .collect(Collectors.toMap(
                            TeamMemberMasterEntity::getId,
                            e -> e,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ))
                    .values()
        );

        if (uniq.size() == 1) return copyOf(uniq.get(0));

        // active が1件だけなら採用
        List<TeamMemberMasterEntity> active = uniq.stream()
                .filter(x -> !"1".equals(x.getRetireFlg()))
                .collect(Collectors.toList());

        if (active.size() == 1) return copyOf(active.get(0));

        // 曖昧なため null（呼び出し元で新規登録へ）
        return null;
    }

    // ─── キー生成 ────────────────────────────────────────────────────────────

    /**
     * 完全一致キー（Step1用）: team + member + facePicPath
     */
    public static String currentKey(TeamMemberMasterEntity e) {
        return join(clean(e.getTeam()), clean(e.getMember()), clean(e.getFacePicPath()));
    }

    /**
     * 同一人物候補キー: member + birth
     * どちらかが空なら null（インデックス対象外）
     */
    public static String memberBirthKey(TeamMemberMasterEntity e) {
        String member = clean(e.getMember());
        String birth  = clean(e.getBirth());
        if (isBlank(member) || isBlank(birth)) return null;
        return join(member, birth);
    }

    /**
     * 同一人物候補キー: member + facePicPath
     * どちらかが空なら null
     */
    public static String memberFaceKey(TeamMemberMasterEntity e) {
        String member = clean(e.getMember());
        String face   = clean(e.getFacePicPath());
        if (isBlank(member) || isBlank(face)) return null;
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
        if (s == null) return null;
        return s.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
