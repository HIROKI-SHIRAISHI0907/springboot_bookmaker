package dev.batch.bm_b016;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.MatchDetailBatchRepository;
import dev.common.entity.MatchDetailMatchEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.MatchDetailDataDTO;

/**
 * BM_B016 試合詳細データDB管理部品
 *
 * <p>1 試合分を 1 トランザクションで登録する。その試合の行を 9 テーブルすべてから消してから入れ直す。
 * 登録件数が想定と違えば例外（ロールバック）。</p>
 * <p>試合ID はフォルダ名の末尾を正とし、全テーブルの行に入れ直す。試合ID が分からない・CSV の試合ID と違う場合は登録しない（例外）。</p>
 * @author shiraishitoshio
 */
@Component
public class MatchDetailDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchDetailDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchDetailDBService.class.getName();

	/** 1 回の INSERT で入れる最大行数（PostgreSQL のパラメータ数上限 65535 を超えないように。選手スタッツ 60 列 × 500 行 = 30000） */
	private static final int MAX_ROWS_PER_INSERT = 500;

	/** MatchDetailBatchRepositoryレポジトリクラス */
	@Autowired
	private MatchDetailBatchRepository matchDetailBatchRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 1 試合分を登録する（消してから入れ直す）
	 *
	 * @param data 1 試合分のデータ（matchList は 1 件以上）
	 * @return 登録した行数（全テーブル合計）
	 * @throws Exception 登録件数が想定と違う・DB エラー（ロールバックされる）
	 */
	@Transactional(rollbackFor = Exception.class)
	public int registerMatch(MatchDetailDataDTO data) throws Exception {
		final String METHOD_NAME = "registerMatch";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		MatchDetailMatchEntity match = data.getMatchList().get(0);

		// 試合ID はフォルダ名の末尾（GetMatchDetailInfo が必ず入れる）を正とする
		String matchId = trimToNull(data.getMatchId());
		if (matchId == null) {
			matchId = trimToNull(match.getMatchId());
		}
		if (matchId == null) {
			throw new Exception("試合IDが分からないため登録しません: folder=" + data.getFolderKey());
		}
		// CSV の試合ID と食い違う（別の試合のファイルが混ざっている）なら登録しない
		String csvMatchId = trimToNull(match.getMatchId());
		if (csvMatchId != null && !csvMatchId.equals(matchId)) {
			throw new Exception("フォルダの試合ID と CSV の試合ID が違います: folder=" + data.getFolderKey()
					+ ", csv=" + csvMatchId);
		}
		// 全テーブルの行に同じ試合ID を入れる（CSV で空の行があっても、消して入れ直す単位がずれないように）
		applyMatchId(data, matchId);

		// 1) この試合の行を消す（子テーブルから）
		this.matchDetailBatchRepository.deleteMemberStat(matchId);
		this.matchDetailBatchRepository.deleteMissing(matchId);
		this.matchDetailBatchRepository.deleteLineup(matchId);
		this.matchDetailBatchRepository.deleteFormation(matchId);
		this.matchDetailBatchRepository.deleteGoal(matchId);
		this.matchDetailBatchRepository.deleteSummary(matchId);
		this.matchDetailBatchRepository.deleteMomentumIncident(matchId);
		this.matchDetailBatchRepository.deleteMomentum(matchId);
		this.matchDetailBatchRepository.deleteMatch(matchId);

		// 2) 入れ直す
		int total = 0;
		int r = this.matchDetailBatchRepository.insertMatch(match, String.join(",", data.getScrapeErrors()),
				data.getFolderKey());
		check(r, 1, "match_detail_match", matchId);
		total += r;
		total += insertAll(data.getMomentumList(), "match_detail_momentum", matchId, list -> this.matchDetailBatchRepository.insertMomentum(list));
		total += insertAll(data.getMomentumIncidentList(), "match_detail_momentum_incident", matchId, list -> this.matchDetailBatchRepository.insertMomentumIncident(list));
		total += insertAll(data.getSummaryList(), "match_detail_summary", matchId, list -> this.matchDetailBatchRepository.insertSummary(list));
		total += insertAll(data.getGoalList(), "match_detail_goal", matchId, list -> this.matchDetailBatchRepository.insertGoal(list));
		total += insertAll(data.getFormationList(), "match_detail_formation", matchId, list -> this.matchDetailBatchRepository.insertFormation(list));
		total += insertAll(data.getLineupList(), "match_detail_lineup", matchId, list -> this.matchDetailBatchRepository.insertLineup(list));
		total += insertAll(data.getMissingList(), "match_detail_missing", matchId, list -> this.matchDetailBatchRepository.insertMissing(list));
		total += insertAll(data.getMemberStatList(), "match_detail_member_stat", matchId, list -> this.matchDetailBatchRepository.insertMemberStat(list));

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return total;
	}

	/**
	 * 1 試合分の一覧をまとめて INSERT する。
	 * row_no は「リスト内の順番 + 1」で入るので、分けずに 1 回で入れる（1 試合で 500 行を超えることは無い想定。超えたら例外）。
	 */
	private <T> int insertAll(List<T> list, String table, String matchId, InsertFunc<T> func) throws Exception {
		if (list == null || list.isEmpty()) {
			return 0;
		}
		if (list.size() > MAX_ROWS_PER_INSERT) {
			throw new Exception(table + " の行数が多すぎます（" + list.size() + " 行 > " + MAX_ROWS_PER_INSERT
					+ "）: matchId=" + matchId);
		}
		int r = func.insert(list);
		check(r, list.size(), table, matchId);
		return r;
	}

	/**
	 * 全テーブルの行に試合ID を入れる
	 */
	private static void applyMatchId(MatchDetailDataDTO data, String matchId) {
		data.getMatchList().forEach(e -> e.setMatchId(matchId));
		data.getMomentumList().forEach(e -> e.setMatchId(matchId));
		data.getMomentumIncidentList().forEach(e -> e.setMatchId(matchId));
		data.getSummaryList().forEach(e -> e.setMatchId(matchId));
		data.getGoalList().forEach(e -> e.setMatchId(matchId));
		data.getFormationList().forEach(e -> e.setMatchId(matchId));
		data.getLineupList().forEach(e -> e.setMatchId(matchId));
		data.getMissingList().forEach(e -> e.setMatchId(matchId));
		data.getMemberStatList().forEach(e -> e.setMatchId(matchId));
	}

	/**
	 * 前後の空白を除き、空なら null
	 */
	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	/**
	 * 登録件数チェック
	 */
	private void check(int actual, int expected, String table, String matchId) throws Exception {
		if (actual != expected) {
			throw new Exception(table + " 登録件数異常（result=" + actual + ", expected=" + expected
					+ ", matchId=" + matchId + "）");
		}
	}

	/**
	 * まとめて INSERT する関数
	 */
	@FunctionalInterface
	private interface InsertFunc<T> {
		int insert(List<T> list);
	}
}
