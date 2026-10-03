package dev.web.api.bm_a099;

import java.util.List;

import lombok.Data;

/**
 * 登録できなかった試合の検索条件（空の項目は条件にしない）
 * @author shiraishitoshio
 *
 */
@Data
public class AnalyzeErrorSearchCondition {

	/** unresolved: 未対応 / resolved: 対応済み / all: すべて */
	private String status;

	/** BM 番号（完全一致） */
	private String bmNumber;

	/** エラー種別（完全一致） */
	private String errorType;

	/** 国（完全一致） */
	private String country;

	/** リーグ（完全一致） */
	private String league;

	/** 原因の項目名（例: homeScore。カンマ区切りの中のどれかと一致） */
	private String errorField;

	/** キーワード（チーム名・キー・エラー内容・マッチID・値の部分一致） */
	private String keyword;

	/** seq の指定（ダウンロードで「選択した行」「この明細」を出すとき。指定があれば他の条件と AND） */
	private List<String> seqs;
}
