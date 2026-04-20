package dev.web.api.bm_a015;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.entity.PointSettingEntity;
import dev.web.repository.master.PointSettingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointSettingsAPIService {

    private final PointSettingRepository pointSettingRepository;

    /**
     * 全件取得
     */
    public List<PointSettingEntity> findAll() {
        return pointSettingRepository.findAll();
    }

    /**
     * 複数件登録・更新・削除
     */
    @Transactional
    public List<PointSettingEntity> save(PointSettingsSaveRequest request) {

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        List<PointSettingEntity> results = new ArrayList<>();

        for (PointSettingItem item : request.getItems()) {
            if (item == null) {
                continue;
            }

            PointSettingEntity entity = new PointSettingEntity();
            entity.setCountry(trim(item.getCountry()));
            entity.setLeague(trim(item.getLeague()));
            entity.setWin(item.getWin());
            entity.setLose(item.getLose());
            entity.setDraw(item.getDraw());
            entity.setRemarks(normalizeRemarks(item.getRemarks()));
            entity.setDelFlg(normalizeDelFlg(item.getDelFlg()));

            // 必須チェック
            if (isBlank(entity.getCountry()) || isBlank(entity.getLeague())) {
                continue;
            }

            List<PointSettingEntity> existingList = pointSettingRepository.findData(
                    entity.getCountry(),
                    entity.getLeague(),
                    entity.getRemarks());

            PointSettingEntity existing = existingList.isEmpty() ? null : existingList.get(0);

            // 削除指定
            if ("1".equals(entity.getDelFlg())) {
                if (existing != null) {
                    pointSettingRepository.logicalDelete(existing.getId());
                }
                continue;
            }

            // 既存あり → update
            if (existing != null) {
                entity.setId(existing.getId());
                pointSettingRepository.update(entity);

                List<PointSettingEntity> updatedList = pointSettingRepository.findData(
                        entity.getCountry(),
                        entity.getLeague(),
                        entity.getRemarks());

                if (!updatedList.isEmpty()) {
                    results.add(updatedList.get(0));
                }
                continue;
            }

            // 新規登録
            entity.setDelFlg("0");
            pointSettingRepository.insert(entity);

            List<PointSettingEntity> insertedList = pointSettingRepository.findData(
                    entity.getCountry(),
                    entity.getLeague(),
                    entity.getRemarks());

            if (!insertedList.isEmpty()) {
                results.add(insertedList.get(0));
            }
        }

        return results;
    }

    private String normalizeRemarks(String remarks) {
        return remarks == null ? "" : remarks.trim();
    }

    private String normalizeDelFlg(String delFlg) {
        return (delFlg == null || delFlg.isBlank()) ? "0" : delFlg.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
