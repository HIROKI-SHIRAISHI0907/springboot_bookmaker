package dev.web.api.bm_a030;

import lombok.Data;

/**
 * リアルタイムデータの更新状況
 * <ul>
 *   <li>currentLiveCount: 直近40分以内に記録された static_data の行数</li>
 *   <li>lastUpdatedAt: 最後に記録された日時（ISO・+09:00）。0件で過去に記録があれば画面は「終了済」</li>
 *   <li>delayStatusType: S3 の延期・遅延情報（delay_postpone_＜日付＞*.json）の status_type（延期 / 遅延）。
 *       記録が無い試合は画面で「更新なし」の代わりにこの値を出す</li>
 *   <li>status: 画面に出す状態（{@link RealtimeStatusResolver} で決める）</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Data
public class FutureMatchRealtimeData {

	/** 直近40分以内に記録された行数 */
	private Integer currentLiveCount = 0;

	/** 最終記録日時（記録が無ければ null） */
	private String lastUpdatedAt;

	/** 延期・遅延の種別（延期 / 遅延。該当しなければ null） */
	private String delayStatusType;

	/** 画面に出す状態（更新中 / 終了済 / 更新なし / 延期 / 中断 / 中止 / 遅延） */
	private String status;

	/** その状態になった理由（例: 試合開始から2時間30分を超えたため。理由が無ければ null） */
	private String statusReason;

	/** 延期・遅延の元の文言（例: 延期 ミジョナリオス オンセ・カルダス - -） */
	private String delayStatusText;
}
