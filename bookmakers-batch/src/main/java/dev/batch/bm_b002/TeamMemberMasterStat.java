package dev.application.analyze.bm_m028;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.TeamMemberEntityIF;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M028統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TeamMemberMasterStat implements TeamMemberEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M028_TEAM_MEMBER";

	/** 監督 */
	private static final String MANAGER = "監督";

	/** beanクラス */
	@Autowired
	private BmM028TeamMemberMasterBean bean;

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	public void teamMemberStat(Map<String, List<TeamMemberMasterEntity>> entities) throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// メンバーマップ
		Map<String, TeamMemberMasterEntity> memberMap = this.bean.getMemberMap();
		// チームマップ
		Map<String, List<String>> teamMap = this.bean.getTeamMap();

		List<String> insertPath = new ArrayList<String>();
		// 今後の対戦カードを登録する
		for (Map.Entry<String, List<TeamMemberMasterEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			List<TeamMemberMasterEntity> insertEntities = new ArrayList<TeamMemberMasterEntity>();
			List<TeamMemberMasterEntity> updateEntities = new ArrayList<TeamMemberMasterEntity>();
			try {
				List<TeamMemberMasterEntity> editedList = editData(map.getValue());
				for (TeamMemberMasterEntity entity : editedList) {
					// 国,リーグ,チームが一致するもののみ
					String country = entity.getCountry();
					String league = entity.getLeague();
					String team = entity.getTeam();
					String key = country + "-" + league;
					List<String> teams = teamMap.get(key);
					if (!teams.contains(team)) {
						break;
					}
					String member = entity.getMember();
					// 監督はskip
					if (MANAGER.equals(entity.getPosition()))
						continue;
					// insertとupdateで分ける
					if (memberMap.containsKey(member)) {
						TeamMemberMasterEntity oldEntity = memberMap.get(member);
						TeamMemberMasterEntity updData = updateData(entity, oldEntity);
						if (updData != null) {
							updateEntities.add(updData);
						}
					} else {
						insertEntities.add(entity);
					}
				}
				int result = this.teamMemberDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = "新規登録エラー";
					throw new Exception(messageCd);
				}
				result = this.teamMemberDBService.updateInBatch(updateEntities, fillChar);
				if (result == 9) {
					String messageCd = "更新エラー";
					throw new Exception(messageCd);
				}
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		for (String path : insertPath) {
			try {
				Files.deleteIfExists(Paths.get(path));
			} catch (IOException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル削除失敗", e, path);
				// ここでは例外をthrowしないことで、DB登録は保持
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 編集データメソッド
	 *  - team(暫定)        → belongList へ詰め替え（重複は除外、カンマ連結）
	 *  - score(暫定) + versusTeam(暫定) → versusTeamScoreData へ「対戦相手-得点」をカンマ連結
	 *  - loanBelong が空でなければ deadline = "1"、なければ "0"
	 *  - retireFlg は既定で "0"（※退団判定の仕様があればここで分岐）
	 */
	private List<TeamMemberMasterEntity> editData(List<TeamMemberMasterEntity> entities) {
		List<TeamMemberMasterEntity> newDtoList = new ArrayList<>();
		for (TeamMemberMasterEntity e : entities) {

			// ----- belongList：team(暫定)を所属チームリストへ反映（重複除去・カンマ連結） -----
			String team = nz(e.getTeam());
			String belongList = nz(e.getBelongList());
			if (!team.isEmpty()) {
				// 既存 belongList に team が無ければ追加（カンマ区切り）
				Set<String> set = new LinkedHashSet<>();
				if (!belongList.isEmpty()) {
					for (String s : belongList.split(",")) {
						if (!s.isBlank())
							set.add(s.trim());
					}
				}
				set.add(team.trim());
				belongList = String.join(",", set);
				e.setBelongList(belongList);
			} else {
				// 入力 team が空でも null を避けて空文字で保持
				e.setBelongList(belongList);
			}

			// ----- versusTeamScoreData：対戦相手-得点 をカンマ連結で作る -----
			// 想定：versusTeam と score が 1:1、もしくはカンマ区切りで並行
			e.setVersusTeamScoreData(nz(e.getVersusTeamScoreData())); // 何も無ければ現状維持/空

			// ----- deadline：loanBelong が空でなければ "1" -----
			String loanBelong = nz(e.getLoanBelong());
			e.setDeadline(loanBelong.isEmpty() ? "0" : "1");

			// ----- retireFlg：既定で "0"（※退団判定ルールがあればここに実装） -----
			e.setRetireFlg("0");

			newDtoList.add(e);
		}
		return newDtoList;
	}

	// ===== ユーティリティ =====
	private static String nz(String s) {
		return s == null ? "" : s;
	}

	/**
	 * 更新データメソッド(更新が必要なもの(memberが同一のものが存在)する場合が対象)
	 * @param entities
	 * @param selectEntities
	 * @return
	 */
	/**
	 * 更新データメソッド(更新が必要なもの(memberが同一)が対象)
	 */
	private TeamMemberMasterEntity updateData(TeamMemberMasterEntity exDto, TeamMemberMasterEntity oldDto) {
	    // member が異なるなら更新対象外
	    if (oldDto == null || exDto == null || !Objects.equals(oldDto.getMember(), exDto.getMember())) {
	        return null;
	    }

	    // 直前チーム（背番号注記用）
	    final String prevTeam = oldDto.getTeam();

	    TeamMemberMasterEntity newDto = new TeamMemberMasterEntity();
	    newDto.setId(oldDto.getId());
	    newDto.setCountry(oldDto.getCountry());
	    newDto.setLeague(oldDto.getLeague());
	    // team は既存が空なら新を採用、既存があれば維持
	    newDto.setTeam(isBlank(oldDto.getTeam()) ? exDto.getTeam() : oldDto.getTeam());
	    newDto.setMember(oldDto.getMember());

	    // score は数値和（どちらか空ならある方）
	    newDto.setScore(sumIntAsString(oldDto.getScore(), exDto.getScore()));

	    // ローン所属は CSV 集合として結合（重複/空は除去）
	    newDto.setLoanBelong(mergeCsvHistory(oldDto.getLoanBelong(), exDto.getLoanBelong()));

	    // 所属履歴（集合的に保持）：old の belongList があればそれに ex の team を追加
	    // なければ old の team と ex の team をマージ
	    String belongBase = !isBlank(oldDto.getBelongList())
	            ? oldDto.getBelongList()
	            : mergeCsvHistory(oldDto.getTeam(), null);
	    newDto.setBelongList(mergeCsvHistory(belongBase, exDto.getTeam()));

	    // 背番号：変更時は注記(直前チームの先頭4文字)を付けつつ CSV マージ、同じなら維持
	    String jerseyNow;
	    String oldJ = trimToEmpty(oldDto.getJersey());
	    String newJ = trimToEmpty(exDto.getJersey());
	    if (isBlank(oldJ)) {
	        jerseyNow = newJ; // 新入団など
	    } else if (oldJ.equals(newJ) || isBlank(newJ)) {
	        jerseyNow = oldJ; // 同じ/新が空なら維持
	    } else {
	        // 変更された → 注記を付ける（所属が増えている＝移籍を示唆）
	        String noted = newJ;
	        if (!isBlank(prevTeam) && newDto.getBelongList().contains(",")) {
	            String head4 = prevTeam.length() >= 4 ? prevTeam.substring(0, 4) : prevTeam;
	            noted = newJ + "(" + head4 + ")";
	        }
	        jerseyNow = mergeCsvHistory(oldJ, noted);
	    }
	    newDto.setJersey(jerseyNow);

	    // 顔写真は既存優先（なければ新）
	    newDto.setFacePicPath(isBlank(oldDto.getFacePicPath()) ? exDto.getFacePicPath() : oldDto.getFacePicPath());

	    // 生年月日は最新（上書き）
	    newDto.setBirth(exDto.getBirth());
	    // 年齢は最新（上書き）
	    newDto.setAge(exDto.getAge());
	    // けが情報は新規優先（怪我したらそのデータ,治ったら更新データ）
	    newDto.setInjury(exDto.getInjury());

	    // ローン中フラグ（ex にローン所属があれば 1、なければ 0）
	    newDto.setDeadline(!isBlank(exDto.getLoanBelong()) ? "1" : "0");

	    // 引退フラグ：一度 1 なら継続、それ以外は ex を優先（空なら 0）
	    String keepRetire = "1".equals(trimToEmpty(oldDto.getRetireFlg())) ? "1"
	            : (isBlank(exDto.getRetireFlg()) ? "0" : exDto.getRetireFlg());
	    newDto.setRetireFlg(keepRetire);

	    // 履歴系は “→” で追記（末尾重複は抑止）
	    newDto.setDeadlineContractDate(mergeHistory(oldDto.getDeadlineContractDate(), exDto.getDeadlineContractDate()));
	    newDto.setHeight(mergeHistory(oldDto.getHeight(), exDto.getHeight()));
	    newDto.setWeight(mergeHistory(oldDto.getWeight(), exDto.getWeight()));
	    newDto.setPosition(mergeHistory(oldDto.getPosition(), exDto.getPosition()));
	    newDto.setMarketValue(mergeHistory(oldDto.getMarketValue(), exDto.getMarketValue()));

	    // 最終更新日（最新で上書き）
	    newDto.setLatestInfoDate(exDto.getLatestInfoDate());

	    // 更新スタンプ："更新済みN" として +1
	    newDto.setUpdStamp(incrementUpdStamp(oldDto.getUpdStamp()));

	    return newDto;
	}

	/* ===== ユーティリティ（クラス内 private でOK） ===== */

	private static boolean isBlank(String s) {
	    return s == null || s.isBlank();
	}
	private static String trimToEmpty(String s) {
	    return s == null ? "" : s.trim();
	}

	/** 数値文字列の和を返す（どちらか空ならもう一方、両方空なら空） */
	private static String sumIntAsString(String a, String b) {
	    String aa = trimToEmpty(a), bb = trimToEmpty(b);
	    if (aa.isEmpty() && bb.isEmpty()) return "";
	    if (aa.isEmpty()) return bb;
	    if (bb.isEmpty()) return aa;
	    try {
	        return Integer.toString(Integer.parseInt(aa) + Integer.parseInt(bb));
	    } catch (NumberFormatException e) {
	        // 数値でない場合は新優先で連結しない（ポリシー次第で調整）
	        return bb;
	    }
	}

	/** "更新済みN" を +1。空なら "更新済み1"。フォーマット異常は 1 に戻す。 */
	private static String incrementUpdStamp(String old) {
	    String v = trimToEmpty(old);
	    if (v.isEmpty()) return "更新済み1";
	    if (v.startsWith("更新済み")) {
	        String nStr = v.substring("更新済み".length()).trim();
	        try {
	            int n = Integer.parseInt(nStr);
	            return "更新済み" + (n + 1);
	        } catch (NumberFormatException ignore) {
	            return "更新済み1";
	        }
	    }
	    return "更新済み1";
	}

	/** 値の履歴を連結（末尾が同じなら追加しない）。区切り文字は既定「→」。 */
	private static String mergeHistory(String oldValue, String newValue) {
	    return mergeHistory(oldValue, newValue, "→");
	}

	/**
	 *  値の履歴を "old→new→newest" のように連結
	 * @param oldValue
	 * @param newValue
	 * @return
	 */
	private static String mergeHistory(String oldValue, String newValue, String sep) {
	    sep = (sep == null || sep.isEmpty()) ? "→" : sep;

	    String oldV = oldValue == null ? "" : oldValue.trim();
	    String newV = newValue == null ? "" : newValue.trim();

	    // 追加なし条件
	    if (newV.isEmpty()) return oldV;
	    if (oldV.isEmpty()) return newV;

	    // oldV が履歴なら最後の要素を取り出す
	    String last = oldV;
	    int idx = oldV.lastIndexOf(sep);
	    if (idx >= 0) last = oldV.substring(idx + sep.length()).trim();

	    // 末尾と同じなら付けない（完全一致のみ。必要なら大文字小文字無視も可）
	    if (last.equals(newV)) return oldV;

	    return oldV + sep + newV;
	}

	/** CSV（デリミタ: ","）の履歴をマージする（順序維持・重複除去・空要素除外） */
	private static String mergeCsvHistory(String oldValue, String newValue) {
	    // 正規化（null→"", 全角カンマ→半角）
	    String oldNorm = normalizeCsv(oldValue);
	    String newNorm = normalizeCsv(newValue);

	    // どちらも空なら空
	    if (oldNorm.isEmpty() && newNorm.isEmpty()) return "";
	    // 片方だけならそれ
	    if (oldNorm.isEmpty()) return newNorm;
	    if (newNorm.isEmpty()) return oldNorm;

	    // 順序維持しつつ重複排除
	    LinkedHashSet<String> merged = new LinkedHashSet<>();

	    // 既存→新規の順で追加
	    for (String t : oldNorm.split(",")) {
	        String s = t.trim();
	        if (!s.isEmpty()) merged.add(s);
	    }
	    for (String t : newNorm.split(",")) {
	        String s = t.trim();
	        if (!s.isEmpty()) merged.add(s);
	    }
	    return String.join(",", merged);
	}

	/** CSV 文字列の軽い正規化（null→空、全角カンマ/読点→半角カンマ、前後空白除去） */
	private static String normalizeCsv(String s) {
	    if (s == null) return "";
	    String x = s.trim();
	    if (x.isEmpty()) return "";
	    // 全角カンマ（，）/読点（、）を半角カンマに寄せる
	    x = x.replace('，', ',').replace('、', ',');
	    // 連続カンマを 1 個に潰す（",," → ","）
	    x = x.replaceAll("\\s*,\\s*", ",").replaceAll(",{2,}", ",");
	    // 先頭/末尾のカンマ除去
	    if (x.startsWith(",")) x = x.substring(1);
	    if (x.endsWith(",")) x = x.substring(0, x.length() - 1);
	    return x.trim();
	}


}
