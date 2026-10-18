package dev.web.api.bm_a031;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * チームデータ CSV の 1 行（country_league_master）
 *
 * <p>キー: 国 + リーグ + チーム。削除・非表示・再表示はキーだけで決める（ID は使わない）。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class TeamCsvRow {

	/** 処理区分（1:登録 2:更新 3:削除 4:非表示 5:再表示） */
	@CsvBindByName(column = "処理区分")
	private String operation;

	/** ID（更新のときだけ使う。任意。国・リーグ・チーム名を変える更新で使う。登録・削除・非表示・再表示では空欄） */
	@CsvBindByName(column = "ID")
	private String id;

	/** 国 */
	@CsvBindByName(column = "国")
	private String country;

	/** リーグ */
	@CsvBindByName(column = "リーグ")
	private String league;

	/** チーム */
	@CsvBindByName(column = "チーム")
	private String team;

	/** リンク（チームページのパス） */
	@CsvBindByName(column = "リンク")
	private String link;
}
