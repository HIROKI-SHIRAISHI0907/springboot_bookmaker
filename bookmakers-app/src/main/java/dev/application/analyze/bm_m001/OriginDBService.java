package dev.application.analyze.bm_m001;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.main.service.DataCategoryService;
import dev.application.main.service.SeqKeyService;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M001起源データDB管理部品
 *
 * <p>登録前の確認（取得に失敗した行を static_data に入れない）</p>
 * <ul>
 *   <li>ホーム・アウェーのチーム名が空の行は登録しない。
 *       （空のまま登録すると、data_category が「XXX: YYY - ラウンド 0」になり、
 *       さらに seq_key 採番で「チーム名が空の行」同士が同じ試合として扱われ、別の試合の行の match_id が書き換わっていた）</li>
 *   <li>match_id は game_link の mid= を正とする（空なら補い、違っていれば mid= に合わせる）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class OriginDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginDBService.class.getName();

	/** game_link の "?mid=xxxx" */
	private static final Pattern MID = Pattern.compile("[?&]mid=([A-Za-z0-9]+)");

	/** SeqKeyServiceクラス */
	@Autowired
	private SeqKeyService seqKeyService;

	/** DataCategoryServiceクラス */
	@Autowired
	private DataCategoryService dataCategoryService;

	/** BookDataRepositoryレポジトリクラス */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド（登録できない行を除き、登録済みの行を除く）
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<DataEntity> selectInBatch(List<DataEntity> chkEntities,
			String fillChar) {
		final String METHOD_NAME = "selectInBatch";
		List<DataEntity> entities = new ArrayList<DataEntity>();
		for (DataEntity entity : chkEntities) {
			// 取得に失敗した行（チーム名が空など）は登録しない
			String invalid = invalidReason(entity);
			if (invalid != null) {
				this.manageLoggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"登録対象外（" + invalid + "）: " + fillChar
								+ ", times=" + entity.getTimes() + ", gameLink=" + entity.getGameLink());
				continue;
			}
			// match_id を game_link の mid= に合わせる
			alignMatchId(entity, fillChar);
			try {
				int count = this.bookDataRepository.findDataCount(entity);
				if (count == 0) {
					entities.add(entity);
				}
			} catch (Exception e) {
				String messageCd = "DB接続エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
				throw e;
			}
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 * @throws IllegalAccessException
	 */
	public int insertInBatch(List<DataEntity> insertEntities) throws IllegalAccessException {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		int inserted = 0;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<DataEntity> batch = insertEntities.subList(i, end);
			for (DataEntity entity : batch) {
				// 通番を発番
				entity.setSeqKey(seqKeyService.create(entity.getHomeTeamName(),
						entity.getAwayTeamName(), entity.getMatchId()));
				// データカテゴリの再設定（同じ試合の範囲だけを見る）
				entity.setDataCategory(dataCategoryService.create(entity.getHomeTeamName(),
						entity.getAwayTeamName(), entity.getDataCategory(), entity.getMatchId()));
				try {
					int result = this.bookDataRepository.insert(entity);
					if (result != 1) {
						String messageCd = "新規登録エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return -99;
					}
					inserted++;
				} catch (DuplicateKeyException e) {
					String messageCd = "登録済みです: seqKey=" + entity.getSeqKey();
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				} catch (DataIntegrityViolationException e) {
					String messageCd = "データの形式が合わないエラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
					continue;
				}
			}
		}
		String messageCd = "BM_M001 登録件数: " + inserted + " / " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		// 登録できた件数を返す（OriginStat の「登録0件」ログが正しく出るように。以前は常に 0 を返していた）
		return inserted;
	}

	/**
	 * 登録できない理由（登録できるなら null）
	 */
	static String invalidReason(DataEntity entity) {
		String home = trim(entity.getHomeTeamName());
		String away = trim(entity.getAwayTeamName());
		if (home.isEmpty() || away.isEmpty()) {
			return "チーム名が空";
		}
		if (home.equals(away)) {
			return "ホームとアウェーが同じ";
		}
		return null;
	}

	/**
	 * match_id を game_link の mid= に合わせる
	 */
	private void alignMatchId(DataEntity entity, String fillChar) {
		String mid = extractMid(entity.getGameLink());
		if (mid == null) {
			return;
		}
		String matchId = trim(entity.getMatchId());
		if (matchId.isEmpty()) {
			entity.setMatchId(mid);
		} else if (!matchId.equals(mid)) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, "alignMatchId",
					"match_id と game_link の mid が違うため mid に合わせます: matchId=" + matchId
							+ ", mid=" + mid + ", " + fillChar);
			entity.setMatchId(mid);
		}
	}

	/** game_link から mid を取り出す（無ければ null） */
	static String extractMid(String gameLink) {
		if (gameLink == null) {
			return null;
		}
		Matcher m = MID.matcher(gameLink);
		return m.find() ? m.group(1) : null;
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}

}
