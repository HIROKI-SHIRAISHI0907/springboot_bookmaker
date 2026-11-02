package dev.web.api.bm_a036;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 大会出場チーム登録設定（bm_a036）のリクエスト
 * @author shiraishitoshio
 */
public class CupCompetitionRequest {

	private CupCompetitionRequest() {
	}

	/** 大会の登録・更新（id が null なら登録） */
	@Data
	public static class Save {
		private Integer id;
		private String competitionName;
		private String region;
		private String competitionUrl;
		private String seasonYear;
		private Integer dispOrder;
		/** "1" = 日程を取る / "0" = 止める */
		private String scrapeFlg;
	}

	/** 大会の削除 */
	@Data
	public static class Delete {
		private Integer id;
	}

	/** 出場チームの追加 */
	@Data
	public static class AddTeams {
		private Integer competitionId;
		private List<TeamKey> teams = new ArrayList<>();
	}

	/** 追加するチーム（国・リーグ・チーム） */
	@Data
	public static class TeamKey {
		private String country;
		private String league;
		private String team;
	}

	/** 出場チームを外す */
	@Data
	public static class RemoveTeams {
		private Integer competitionId;
		private List<Integer> ids = new ArrayList<>();
	}

	/** 出場選手の取り直し（試合ID） */
	@Data
	public static class Retry {
		private List<String> mids = new ArrayList<>();
	}
}
