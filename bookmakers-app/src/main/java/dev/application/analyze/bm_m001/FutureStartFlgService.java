package dev.application.analyze.bm_m001;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * 未来データ更新サービスクラス（future_master.start_flg を 1 = 開始済みにする）
 *
 * <p>start_flg = 1 にするタイミング</p>
 * <ol>
 *   <li>リアルタイムデータ（CSV）が入った試合 … 同じ試合の予定を 1 にする。</li>
 *   <li>試合開始日時（future_time）を過ぎた試合 … CSV の有無に関係なく 1 にする。</li>
 * </ol>
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>「同じ試合の予定」の探し方: 以前はチーム名の完全一致だけ（日付・並び順の指定なし）で、
 *       同じカードの別の日の予定を 1 にしてしまうことがあった。
 *       → game_link の mid が CSV の match_id と一致する予定を優先し、無ければ同じカード（NFKC 正規化）で
 *       記録時刻の前後1日のうち試合開始日時が一番近い予定にした（{@link FutureMasterRepository#findStartFlgTarget}）。</li>
 *   <li>試合開始日時を過ぎた試合の更新: DB が UTC のため、future_time（日本時間の壁時計）と now() の比較が
 *       9時間ずれていた（9時間遅れて 1 になっていた）。→ 日本時間に直して比較する。
 *       また start_flg = '0' の行だけを更新する（以前は毎回、過去の全行を更新していた）。</li>
 *   <li>更新件数が想定と違う場合の例外: 検査例外（Exception）はそのままではロールバックされないため、
 *       rollbackFor = Exception.class を付け、投げる例外も内容の分かる IllegalStateException にした。</li>
 *   <li>延期: S3 の延期情報（delay_postpone。前日分＋当日分で最後の状態が「延期」の試合）を読み、
 *       その予定を start_flg = 0 に戻す。また「試合開始日時を過ぎたら 1」の更新から外す
 *       （外さないと、戻しても次の実行で 1 になってしまう）。
 *       今回の CSV に入っている試合（＝実際に試合中）は延期扱いにしない。</li>
 *   <li>CSV の最新の試合時間が「延期」「中止」の試合は、CSV があっても start_flg = 1 にしない
 *       （延期は SuspensionStatusPublisher が delay_postpone に出力し、上の処理で 0 に戻す）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class FutureStartFlgService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureStartFlgService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureStartFlgService.class.getName();

	/** 試合開始有効 */
	private static final String STRAT_FLG_1 = "1";

	/**
	 * 未来データレポジトリ
	 */
	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/** 延期中の試合（S3 の delay_postpone） */
	@Autowired
	private PostponedMatchReader postponedMatchReader;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 * @param csvMap アプリケーション実行時にdataテーブルに登録されたデータ
	 * @return 処理結果
	 * @throws Exception
	 */
	public int execute(Map<String, List<DataEntity>> csvMap) throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// futureデータがあるか
		if (this.futureMasterRepository.findAll() == 0) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					String.format("データが存在しません（%s）", "future_master"));

			// endLog
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);

			return 0;
		}

		// 延期中の試合を start_flg = 0 に戻す（今回の CSV に入っている試合は除く）
		List<String> postponedSeqs = resetPostponed(csvMatchIds(csvMap));

		// app実行時に登録されたデータに関する更新
		if (csvMap != null && !csvMap.isEmpty()) {
			for (Map.Entry<String, List<DataEntity>> map : csvMap.entrySet()) {

				List<DataEntity> list = map.getValue();

				if (list == null || list.isEmpty()) {
					continue;
				}

				// 今の状態が延期・中止の試合は開始扱いにしない（延期は start_flg = 0 のまま）
				String status = CsvMatchStatus.statusOf(list);
				if (CsvMatchStatus.POSTPONED.equals(status) || CsvMatchStatus.CANCELLED.equals(status)) {
					continue;
				}

				// 1つのCSVにつき、有効な試合データを1件更新
				for (DataEntity dto : list) {

					if (dto == null) {
						continue;
					}

					String home = dto.getHomeTeamName();
					String away = dto.getAwayTeamName();

					if (home != null && away != null) {
						startFlgUpdate(home, away, dto.getMatchId(), dto.getRecordTime(), STRAT_FLG_1);

						// 1つのCSVにつき1試合を更新
						break;
					}
				}
			}
		}

		/*
		 * future_timeを過ぎた試合は、
		 * CSVの有無に関係なくstart_flg=1に更新する（延期中の試合は除く）。
		 */
		startFlgUpdate(STRAT_FLG_1, postponedSeqs);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		return 0;
	}

	/**
	 * 同じ試合の予定（start_flg = 0）の試合開始フラグを更新する。
	 * mid（match_id）が一致する予定を優先し、無ければ同じカードで記録時刻の前後1日のうち試合開始日時が一番近い予定。
	 *
	 * @param home ホームチーム
	 * @param away アウェーチーム
	 * @param matchId マッチID（null 可）
	 * @param recordTime 記録時刻（日本時間。null 可。null なら現在時刻）
	 * @param flg 試合開始フラグ
	 */
	private synchronized void startFlgUpdate(
			String home,
			String away,
			String matchId,
			String recordTime,
			String flg) {

		final String METHOD_NAME = "startFlgUpdate";

		String fillChar = setLoggerFillChar(home, away, matchId, recordTime);

		FutureEntity target = this.futureMasterRepository.findStartFlgTarget(home, away, matchId, recordTime);

		String messageCdLog = MessageCdConst.MCD00099I_LOG;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCdLog,
				fillChar,
				"更新対象: " + (target == null ? 0 : 1) + "件");

		if (target == null) {
			return;
		}

		int result = this.futureMasterRepository.updateStartFlg(target.getSeq(), flg);

		if (result != 1) {

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCdLog,
					fillChar + ", seq: " + target.getSeq() + ", 試合予定時間: " + target.getFutureTime());

			String messageCd =
					MessageCdConst.MCD00008E_UPDATE_FAILED;

			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);

			throw new IllegalStateException("future_master の start_flg 更新件数が想定と違います（想定: 1件, 実際: "
					+ result + "件, seq: " + target.getSeq() + ", " + fillChar + "）");
		}

		String messageCd =
				MessageCdConst.MCD00006I_UPDATE_SUCCESS;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCd,
				fillChar,
				"更新件数: 1件（seq: " + target.getSeq() + ", 試合予定時間: " + target.getFutureTime() + "）");
	}

	/**
	 * 試合開始日時を過ぎた試合（start_flg = 0）の試合開始フラグを更新する。
	 *
	 * @param flg 試合開始フラグ
	 * @param excludeSeqs 対象外にする seq（延期中の試合）
	 */
	private synchronized void startFlgUpdate(String flg, List<String> excludeSeqs) {

		final String METHOD_NAME = "startFlgUpdate";

		int result =
				this.futureMasterRepository.updateFutureTimeFlg(flg, excludeSeqs);

		String messageCd =
				MessageCdConst.MCD00006I_UPDATE_SUCCESS;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCd,
				"更新件数: " + result + "件");
	}

	/**
	 * 今回の CSV に入っている試合の match_id（実際に試合中の試合。今の状態が延期・中止の試合は除く）。
	 */
	private static Set<String> csvMatchIds(Map<String, List<DataEntity>> csvMap) {
		Set<String> ids = new HashSet<>();
		if (csvMap == null) {
			return ids;
		}
		for (List<DataEntity> list : csvMap.values()) {
			if (list == null) {
				continue;
			}
			String status = CsvMatchStatus.statusOf(list);
			if (CsvMatchStatus.POSTPONED.equals(status) || CsvMatchStatus.CANCELLED.equals(status)) {
				continue;
			}
			for (DataEntity e : list) {
				if (e != null && e.getMatchId() != null && !e.getMatchId().isBlank()) {
					ids.add(e.getMatchId().trim());
				}
			}
		}
		return ids;
	}

	/**
	 * 延期中の試合の予定を start_flg = 0 に戻す。
	 *
	 * @param playingMatchIds 今回の CSV に入っている試合の match_id（延期扱いにしない）
	 * @return 延期中の試合の seq（「試合開始日時を過ぎたら 1」の更新から外す）
	 */
	private List<String> resetPostponed(Set<String> playingMatchIds) {
		final String METHOD_NAME = "resetPostponed";
		Set<String> seqs = new LinkedHashSet<>();
		for (PostponedMatchReader.PostponedMatch m : this.postponedMatchReader.loadPostponed(LocalDate.now(ZoneId.of("Asia/Tokyo")))) {
			if (m.mid != null && playingMatchIds.contains(m.mid)) {
				continue;
			}
			if (m.mid == null && (m.home == null || m.away == null)) {
				continue;
			}
			List<String> found = this.futureMasterRepository.findPostponeTargets(m.home, m.away, m.mid, m.start);
			if (found != null) {
				seqs.addAll(found);
			}
		}
		List<String> list = new ArrayList<>(seqs);
		if (!list.isEmpty()) {
			int n = this.futureMasterRepository.resetStartFlg(list);
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"延期中の試合: " + list.size() + "件, start_flg を 0 に戻した件数: " + n + "件 (seq=" + list + ")");
		}
		return list;
	}

	/**
	 * 埋め字設定
	 *
	 * @param home ホーム
	 * @param away アウェー
	 * @param matchId マッチID
	 * @param recordTime 記録時刻
	 * @return ログ表示文字列
	 */
	private String setLoggerFillChar(String home, String away, String matchId, String recordTime) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away + ", ");
		stringBuilder.append("matchId: " + matchId + ", ");
		stringBuilder.append("記録時刻: " + recordTime);
		return stringBuilder.toString();
	}

}
