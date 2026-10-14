package dev.web.api.bm_a030;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a030.DelayPostponeS3Reader.DelayPostponeIndex;
import dev.web.api.bm_a030.DelayPostponeS3Reader.DelayPostponeInfo;

import dev.web.repository.bm.FutureMatchDataStatusRepository;
import dev.web.repository.bm.FutureMatchDataStatusRepository.MatchKey;
import dev.web.repository.bm.FutureMatchDataStatusRepository.MatchStatusRow;
import dev.web.repository.master.FuturesRepository;
import dev.web.repository.master.FuturesRepository.FutureMatchDateRow;
import lombok.RequiredArgsConstructor;

/**
 * 指定日の試合予定一覧（管理画面）
 *
 * <ol>
 *   <li>future_master（webMasterJdbcTemplate）から指定日の試合を1ページ分取得</li>
 *   <li>その試合の static_data（bmJdbcTemplate）の登録状況をまとめて取得
 *       （DB が別なので JOIN せず、2回に分けて Java 側で突き合わせる）</li>
 *   <li>S3 の延期・遅延情報（delay_postpone_＜日付＞*.json）を mid / チーム名で突き合わせる</li>
 *   <li>リアルタイムデータの状態を {@link RealtimeStatusResolver} で決める</li>
 * </ol>
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class FutureMatchAdminService {

	public static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final int DEFAULT_LIMIT = 10;
	private static final int MAX_LIMIT = 100;

	private final FuturesRepository futuresRepository;

	private final FutureMatchDataStatusRepository futureMatchDataStatusRepository;

	private final DelayPostponeS3Reader delayPostponeS3Reader;

	public FutureMatchesResponse findByDate(LocalDate date, int offset, int limit) {
		int safeOffset = Math.max(0, offset);
		int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		List<FutureMatchDateRow> rows = futuresRepository.findByFutureDate(date, safeOffset, safeLimit);
		long total = futuresRepository.countByFutureDate(date);

		// static_data の登録状況（seq → 集計）
		List<MatchKey> keys = new ArrayList<>();
		for (FutureMatchDateRow r : rows) {
			MatchKey k = new MatchKey();
			k.setSeq(r.getSeq());
			k.setHomeTeamName(r.getHomeTeamName());
			k.setAwayTeamName(r.getAwayTeamName());
			k.setMatchId(r.getMid());
			k.setFutureTime(r.getFutureTimeLocal());
			keys.add(k);
		}
		Map<Long, MatchStatusRow> statusBySeq = futureMatchDataStatusRepository.findStatus(keys);

		// S3 の延期・遅延情報（その日のファイル全部。試合が無いページでは読まない）
		DelayPostponeIndex delays = rows.isEmpty() ? new DelayPostponeIndex() : delayPostponeS3Reader.load(date);

		// リアルタイムデータの状態の判定に使う現在日時（日本時間）
		LocalDateTime now = LocalDateTime.now(JST);

		List<FutureMatchDTO> matches = new ArrayList<>();
		for (FutureMatchDateRow r : rows) {
			FutureMatchDTO dto = new FutureMatchDTO();
			dto.setId(String.valueOf(r.getSeq()));
			dto.setSeq(r.getSeq());
			dto.setGameTeamCategory(r.getGameTeamCategory());
			dto.setFutureTime(r.getFutureTime());
			dto.setHomeTeam(r.getHomeTeamName());
			dto.setAwayTeam(r.getAwayTeamName());
			dto.setLink(r.getGameLink());
			dto.setRoundNo(r.getRoundNo());
			dto.setStatus(r.getStartFlg());

			MatchStatusRow s = statusBySeq.get(r.getSeq());
			if (s != null) {
				dto.getSystemData().setFinishedCount(s.getFinishedCount());
				dto.getSystemData().setLiveCount(s.getLiveCount());
				dto.getRealtimeData().setCurrentLiveCount(s.getCurrentLiveCount());
				dto.getRealtimeData().setLastUpdatedAt(s.getLastUpdatedAt());
			}

			DelayPostponeInfo delay = delays.find(r.getMid(), r.getHomeTeamName(), r.getAwayTeamName());
			if (delay != null) {
				dto.getRealtimeData().setDelayStatusType(delay.getStatusType());
				dto.getRealtimeData().setDelayStatusText(delay.getStatusText());
			}

			RealtimeStatusResolver.Result rs = RealtimeStatusResolver.resolve(
					r.getFutureTimeLocal(),
					dto.getRealtimeData().getLastUpdatedAt(),
					dto.getRealtimeData().getDelayStatusType(),
					now);
			dto.getRealtimeData().setStatus(rs.status);
			dto.getRealtimeData().setStatusReason(rs.reason);
			matches.add(dto);
		}

		FutureMatchesResponse res = new FutureMatchesResponse();
		res.setDate(date.toString());
		res.setOffset(safeOffset);
		res.setLimit(safeLimit);
		res.setTotal(total);
		res.setMatches(matches);
		return res;
	}
}
