package dev.application.analyze.bm_m002;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 読み込んだデータから結果マスタにマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ConditionResultDataEntity extends MetaEntity {

	/** まとめ通番 */
	private String dataSeq;

	/** メール通知対象数 */
	private String mailTargetCount;

	/** メール非通知対象数 */
	private String mailAnonymousTargetCount;

	/** メール通知成功数 */
	private String mailTargetSuccessCount;

	/** メール通知失敗数 */
	private String mailTargetFailCount;

	/** 前メール通知失敗結果不明数 */
	private String exMailTargetToNoResultCount;

	/** 前終了済データ無し結果不明数 */
	private String exNoFinDataToNoResultCount;

	/** ゴール取り消し */
	private String goalDelete;

	/** ゴール取り消しによる通知非通知変更 */
	private String alterTargetMailAnonymous;

	/** ゴール取り消しによる成功失敗変更 */
	private String alterTargetMailFail;

	/** 結果不明 */
	private String noResultCount;

	/** 予期せぬエラーデータ数 */
	private String errData;

	/** 条件分岐データ */
	private String conditionData;

	/** ハッシュ値データ */
	private String hash;

}
