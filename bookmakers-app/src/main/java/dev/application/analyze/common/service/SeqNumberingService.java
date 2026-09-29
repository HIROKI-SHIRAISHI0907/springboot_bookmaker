package dev.application.analyze.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.SeqCounterRepository;

/**
 * 「&lt;シーズン&gt;-&lt;枝番&gt;」形式の seq を採番するサービス。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * テーブル×シーズンごとに 1 から振り直す枝番を付けた seq を返す。
 * 例: シーズン "2025-2026" の team_monthly_score_summary の3件目 → "2025-2026-000003"。
 * シーズン終了時にデータを削除しても、次のシーズンは 000001 から振り直されるため、
 * 連番の穴あきがシーズンをまたいで残らない。
 * </p>
 *
 * <h2>トランザクション</h2>
 * <p>
 * {@link Propagation#MANDATORY}: 呼び出し元のトランザクション内でのみ実行できる。
 * 登録（INSERT）が失敗してロールバックされると採番もロールバックされるため、欠番ができない。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>枝番の桁数</b>: 6桁（最大 999,999 件/テーブル/シーズン）。超えると桁が増え、
 *       文字列の並び順と番号順が一致しなくなる。超える見込みがあれば {@link #BRANCH_DIGITS} を増やすこと
 *       （途中で変える場合は既存データも揃える必要がある）。</li>
 *   <li><b>シーズン内での削除</b>: シーズン途中で行を削除した場合の穴は埋めない（次の番号から振る）。</li>
 *   <li><b>シーズン文字列の検証</b>: 空は例外。形式（"2025-2026" / "2025" など）は検証しない。</li>
 *   <li><b>同時実行</b>: 同じテーブル×シーズンの採番は、先に採番したトランザクションが終わるまで待たされる。</li>
 * </ul>
 */
@Service
public class SeqNumberingService {

	/** 枝番の桁数 */
	public static final int BRANCH_DIGITS = 6;

	@Autowired
	private SeqCounterRepository seqCounterRepository;

	/**
	 * seq を採番する。
	 *
	 * @param tableName 対象テーブル名（採番単位）
	 * @param season シーズン（country_league_season_master.season）
	 * @return "&lt;シーズン&gt;-&lt;6桁枝番&gt;"
	 * @throws IllegalArgumentException テーブル名・シーズンが空の場合
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public String nextSeq(String tableName, String season) {
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("tableName is blank.");
		}
		if (season == null || season.isBlank()) {
			throw new IllegalArgumentException("season is blank. table=" + tableName);
		}
		String s = season.trim();
		long no = this.seqCounterRepository.nextNumber(tableName, s);
		return s + "-" + String.format("%0" + BRANCH_DIGITS + "d", no);
	}

	/**
	 * 【追加】seq をまとめて count 個採番する（seq_counter の更新は1回）。
	 *
	 * @param tableName 対象テーブル名（採番単位）
	 * @param season シーズン
	 * @param count 個数（0 以下なら空リスト）
	 * @return 採番した seq（番号の昇順）
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<String> nextSeqBlock(String tableName, String season, int count) {
		List<String> result = new ArrayList<>();
		if (count <= 0) {
			return result;
		}
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("tableName is blank.");
		}
		if (season == null || season.isBlank()) {
			throw new IllegalArgumentException("season is blank. table=" + tableName);
		}
		String s = season.trim();
		long last = this.seqCounterRepository.nextNumberBlock(tableName, s, count);
		for (long no = last - count + 1; no <= last; no++) {
			result.add(s + "-" + String.format("%0" + BRANCH_DIGITS + "d", no));
		}
		return result;
	}
}