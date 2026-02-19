package dev.web.api.bm_a011;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.master.FuturesRepository;


@Service
public class IngestedDataService {

    private final BookDataRepository bookDataRepository;

    private final FuturesRepository futuresRepository;

    public IngestedDataService(BookDataRepository bookDataRepository,
    		FuturesRepository futuresRepository) {
    	this.futuresRepository = futuresRepository;
        this.bookDataRepository = bookDataRepository;
    }

    @Transactional(readOnly = true)
    public IngestedDataReferenceResponse search(IngestedDataReferenceRequest req) {

        OffsetDateTime to = (req.getTo() != null)
                ? req.getTo()
                : OffsetDateTime.now(ZoneId.of("Asia/Tokyo"));

        OffsetDateTime from = (req.getFrom() != null)
                ? req.getFrom()
                : to.minusDays(7);

        List<IngestedRowDTO> merged = new ArrayList<>();
        long total = 0;

        if (req.isIncludeFutureMaster()) {
            var futures = futuresRepository.findFutureMasterByRegisterTime(from, to);
            total += futures.size();

            for (var r : futures) {
                IngestedRowDTO row = new IngestedRowDTO();
                row.setTable(IngestedRowDTO.TableName.FUTURE_MASTER);
                row.setSeq(String.valueOf(r.seq));
                row.setRegisterTime(r.registerTime);
                row.setUpdateTime(r.updateTime);

                FutureMasterIngestSummaryDTO s = new FutureMasterIngestSummaryDTO();
                s.setSeq(r.seq);
                s.setGameTeamCategory(r.gameTeamCategory);
                s.setFutureTime(r.futureTime);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setGameLink(r.gameLink);
                s.setStartFlg(r.startFlg);

                row.setFuture(s);
                merged.add(row);
            }
        }

        if (req.isIncludeData()) {
            var dataRows = bookDataRepository.findDataByRegisterTime(from, to);
            total += dataRows.size();

            for (var r : dataRows) {
                IngestedRowDTO row = new IngestedRowDTO();
                row.setTable(IngestedRowDTO.TableName.DATA);
                row.setSeq(r.seq);
                row.setRegisterTime(r.registerTime);
                row.setUpdateTime(r.updateTime);

                DataIngestSummaryDTO s = new DataIngestSummaryDTO();
                s.setSeq(r.seq);
                s.setDataCategory(r.dataCategory);
                s.setTimes(r.times);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setRecordTime(r.recordTime);

                row.setData(s);
                merged.add(row);
            }
        }

        // 新しい順
        merged.sort(Comparator.comparing(IngestedRowDTO::getRegisterTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        // ページング（mergedは最大でも “直近1週間” なので雑にJava側でOK。件数が多いならSQL側でやる）
        int fromIdx = Math.min(req.getOffset(), merged.size());
        int toIdx = Math.min(fromIdx + req.getLimit(), merged.size());
        List<IngestedRowDTO> paged = merged.subList(fromIdx, toIdx);

        IngestedDataReferenceResponse res = new IngestedDataReferenceResponse();
        res.setFrom(from);
        res.setTo(to);
        res.setRows(paged);
        res.setTotal(total);
        return res;
    }
}
