package dev.application.analyze.bm_m001;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.PostponedDataPurgeRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * 延期になった試合（中断 → 延期 など）の途中までのデータを退避してから削除する（BM_M001。2026-10 追加）。
 *
 * <h2>なぜ消すか</h2>
 * <p>延期になった試合は後日やり直しになるため、中断前までに static_data に登録された行が残ると、
 * 統計 CSV の作成や画面の件数の邪魔になる。BM_M029 の real_data_process（1試合1行の差分）も同じ試合の行を消す。
 * それ以外の統計テーブルは統計 CSV ができてから登録されるので、延期の試合のデータは入っていない。</p>
 *
 * <h2>いつ動くか</h2>
 * <p>{@link FutureStartFlgService} が延期の試合の start_flg を 0 に戻すとき（延期のあいだ毎回。2回目以降は対象が無ければ何もしない）。
 * 延期のあとに試合時間 = 延期 の行が登録されても、次の実行で同じように消える。</p>
 *
 * <h2>消さない場合</h2>
 * <ul>
 *   <li>終了済の行がある（試合が終わっている＝延期の情報が古い）。</li>
 *   <li>元の試合開始日時（baseTime）が分からない。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>1試合 = 1トランザクション（REQUIRES_NEW）。退避と削除はまとめてコミット／ロールバックされる。</p>
 * @author shiraishitoshio
 */
@Component
public class PostponedDataPurger {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = PostponedDataPurger.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = PostponedDataPurger.class.getName();

	/** 退避理由 */
	static final String REASON = "延期";

	@Autowired
	private PostponedDataPurgeRepository purgeRepository;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 1試合分を退避して削除する。
	 *
	 * @param mid game_link の mid（null 可）
	 * @param home ホーム
	 * @param away アウェー
	 * @param baseTime 元の試合開始日時（future_master.future_time。"yyyy-MM-dd HH:mm:ss"・日本時間）
	 * @return static_data から削除した行数（消さなかった場合は 0）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int purge(String mid, String home, String away, String baseTime) {
		final String METHOD_NAME = "purge";
		String fill = "mid=" + mid + ", home=" + home + ", away=" + away + ", 試合開始日時=" + baseTime;
		if (baseTime == null || baseTime.isBlank() || home == null || away == null) {
			log(METHOD_NAME, "延期データの削除をしない（試合開始日時・チーム名が不明）: " + fill);
			return 0;
		}

		int finished = this.purgeRepository.countFinished(mid, home, away, baseTime);
		if (finished > 0) {
			log(METHOD_NAME, "終了済の行があるため延期データの削除をしない: " + fill);
			return 0;
		}

		List<String> matchIds = this.purgeRepository.findMatchIds(mid, home, away, baseTime);
		if (mid != null && !mid.isBlank() && !matchIds.contains(mid.trim())) {
			matchIds.add(mid.trim());
		}

		int backup = this.purgeRepository.backupStaticData(mid, home, away, baseTime, REASON);
		int deleted = this.purgeRepository.deleteStaticData(mid, home, away, baseTime);
		if (backup != deleted) {
			// 退避と削除の間に行が増えた・減った → ロールバックして次の実行でやり直す
			throw new IllegalStateException("static_data の退避件数と削除件数が違います（退避: " + backup
					+ "件, 削除: " + deleted + "件, " + fill + "）");
		}

		int rdpBackup = 0;
		int rdpDeleted = 0;
		if (!matchIds.isEmpty()) {
			rdpBackup = this.purgeRepository.backupRealDataProcess(matchIds, REASON);
			rdpDeleted = this.purgeRepository.deleteRealDataProcess(matchIds);
			if (rdpBackup != rdpDeleted) {
				throw new IllegalStateException("real_data_process の退避件数と削除件数が違います（退避: " + rdpBackup
						+ "件, 削除: " + rdpDeleted + "件, " + fill + "）");
			}
		}

		if (deleted > 0 || rdpDeleted > 0) {
			log(METHOD_NAME, "延期の試合のデータを退避して削除しました: static_data " + deleted + "件, real_data_process "
					+ rdpDeleted + "件 (" + fill + ", matchIds=" + matchIds + ")");
		}
		return deleted;
	}

	private void log(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}
}
