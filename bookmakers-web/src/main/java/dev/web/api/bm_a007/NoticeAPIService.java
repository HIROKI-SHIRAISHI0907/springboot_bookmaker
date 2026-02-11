package dev.web.api.bm_a007;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.FuturesRepository;
import dev.web.repository.user.NoticeRepository;
import lombok.RequiredArgsConstructor;

/**
 * NoticeAPIサービスクラス用
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class NoticeAPIService {

    private final NoticeRepository noticeRepository;

    private final FuturesRepository futuresRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final String OP = "system";

    @Transactional
    public NoticeResponse create(NoticeRequest req) throws NotFoundException {
        validateDisplayRange(req.getDisplayFrom(), req.getDisplayTo());

        Long id = noticeRepository.insert(
        		req.getFeatureMatchId(),
                req.getTitle(),
                req.getBody(),
                req.getDisplayFrom(),
                req.getDisplayTo(),
                OP
        );

        var row = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("notice not found after insert: " + id));
        return NoticeResponse.from(row);
    }

    @Transactional
    public NoticeResponse update(NoticeRequest req) throws NotFoundException {
        if (req.getId() == null) {
            throw new IllegalArgumentException("更新にはIDが必要です。");
        }
        validateDisplayRange(req.getDisplayFrom(), req.getDisplayTo());

        int updated = noticeRepository.update(
                req.getId(),
                req.getTitle(),
                req.getBody(),
                req.getDisplayFrom(),
                req.getDisplayTo(),
                OP
        );
        if (updated == 0) throw new NotFoundException("notice not found: " + req.getId());

        var row = noticeRepository.findById(req.getId())
                .orElseThrow(() -> new NotFoundException("notice not found after update: " + req.getId()));
        return NoticeResponse.from(row);
    }


    @Transactional
    public NoticeResponse publish(Long id) throws NotFoundException {
        int updated = noticeRepository.publish(id, OP);
        if (updated == 0) throw new NotFoundException("notice not found: " + id);

        var row = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("notice not found after publish: " + id));
        return NoticeResponse.from(row);
    }

    @Transactional
    public NoticeResponse archive(Long id) throws NotFoundException {
        int updated = noticeRepository.archive(id, OP);
        if (updated == 0) throw new NotFoundException("notice not found: " + id);

        var row = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("notice not found after archive: " + id));
        return NoticeResponse.from(row);
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> listAll() {
        return noticeRepository.findAllOrderByUpdateTimeDesc()
                .stream().map(NoticeResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> listActiveForFront() {
        OffsetDateTime now = OffsetDateTime.now(ZONE);

        // 1) userDBから表示対象を取得
        List<NoticeRepository.NoticeRow> rows = noticeRepository.findActiveForFront(now);

        // 2) FEATURED_MATCH の feature_match_id を集める
        List<Long> ids = rows.stream()
                .filter(r -> "FEATURED_MATCH".equals(r.noticeType))
                .map(r -> r.featureMatchId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 3) masterDBからまとめて取得（存在チェック）
        Map<Long, FuturesRepository.FutureMatchRow> matchMap =
        		futuresRepository.findByIds(ids).stream()
                        .collect(Collectors.toMap(m -> m.id, m -> m));

        // 4) レスポンス化（FEATURED_MATCH は title/body を生成して上書き）
        List<NoticeResponse> result = new ArrayList<>();

        for (var r : rows) {
            // 基本はDBの値でレスポンス化
            NoticeResponse res = NoticeResponse.from(r);

            if ("FEATURED_MATCH".equals(r.noticeType) && r.featureMatchId != null) {
                var m = matchMap.get(r.featureMatchId);

                if (m != null) {
                    // ★存在するので生成
                    res.setTitle("注目！！" + m.homeTeamName + " vs " + m.awayTeamName);
                    // bodyも自由に生成（例：開始時刻を入れる）
                    if (m.matchStartTime != null) {
                        res.setBody("キックオフ: " + m.matchStartTime.atZoneSameInstant(ZONE).toLocalDateTime());
                    } else {
                        res.setBody("本日の注目対戦です！");
                    }
                } else {
                    // ★存在しない（masterに無い）場合の扱い
                    // 選択肢A: 表示しない（推奨：ゴミデータを表に出さない）
                    continue;

                    // 選択肢B: title/bodyはDBのまま出す（運用方針により）
                    // （その場合は continue を消す）
                }
            }

            result.add(res);
        }

        // 5) 0件ならデフォルト注目対戦（フロント要件）
        if (result.isEmpty()) {
            NoticeResponse def = new NoticeResponse();
            def.setId(0L);
            def.setTitle("注目！！鹿島 vs 広島");
            def.setBody("本日の注目対戦です！");
            def.setStatus("DEFAULT");
            result.add(def);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> listPublishedForFront() {
        return noticeRepository.findPublishedOrderByPublishedAtDesc()
                .stream().map(NoticeResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NoticeResponse getPublishedDetailForFront(Long id) throws NotFoundException {
        var row = noticeRepository.findPublishedById(id)
                .orElseThrow(() -> new NotFoundException("published notice not found: " + id));
        return NoticeResponse.from(row);
    }

    private static void validateDisplayRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("表示期間（FROM）は表示期間（TO）より前の日付である必要があります。");
        }
    }
}
