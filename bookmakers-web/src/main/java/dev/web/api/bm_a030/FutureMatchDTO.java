package dev.web.api.bm_a030;

import lombok.Data;

/**
 * 試合予定1件（future_master 1行 ＋ static_data の集計）
 * <p>項目名は画面（FutureMatchesByDatePage の FutureMatch 型）に合わせている。</p>
 * @author shiraishitoshio
 *
 */
@Data
public class FutureMatchDTO {

	/** id（seq を文字列にしたもの） */
	private String id;

	/** future_master.seq */
	private Long seq;

	/** カテゴリ（game_team_category） */
	private String gameTeamCategory;

	/** 試合開始日時（ISO・+09:00） */
	private String futureTime;

	/** ホームチーム */
	private String homeTeam;

	/** アウェーチーム */
	private String awayTeam;

	/** 試合ページのリンク（game_link） */
	private String link;

	/** ラウンド番号（カテゴリの「ラウンド N」から取得。無ければ null） */
	private Integer roundNo;

	/** start_flg */
	private String status;

	/** システムデータ（static_data の終了済・それ以外の件数） */
	private FutureMatchSystemData systemData = new FutureMatchSystemData();

	/** リアルタイムデータ（状態・static_data の直近40分の件数・最終記録日時・延期遅延） */
	private FutureMatchRealtimeData realtimeData = new FutureMatchRealtimeData();
}
