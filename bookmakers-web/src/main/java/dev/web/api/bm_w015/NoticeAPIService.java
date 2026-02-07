package dev.web.api.bm_w015;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final String OP = "system";

    @Transactional
    public NoticeResponse create(NoticeRequest req) throws NotFoundException {
        validateDisplayRange(req.getDisplayFrom(), req.getDisplayTo());

        Long id = noticeRepository.insert(
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
        return noticeRepository.findActiveForFront(now)
                .stream().map(NoticeResponse::from).collect(Collectors.toList());
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
