package dev.web.api.bm_a015;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.entity.PointSettingEntity;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.master.PointSettingWebRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointSettingsAPIService {

    private final PointSettingWebRepository pointSettingRepository;

    private final CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

    /**
     * 全件取得
     */
    public List<PointSettingEntity> findAll() {
        return pointSettingRepository.findAll();
    }

    /**
     * 複数件登録・更新・削除
     *
     * del_flg は country + league 単位で
     * country_league_season_master と point_setting_master を同期する。
     */
    @Transactional
    public List<PointSettingEntity> save(PointSettingsSaveRequest request) {

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        List<PointSettingEntity> results = new ArrayList<>();

        // 同一 country/league の del_flg は一致させる
        Map<String, String> countryLeagueDelFlgMap = new LinkedHashMap<>();

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

            if (isBlank(entity.getCountry()) || isBlank(entity.getLeague())) {
                continue;
            }

            String countryLeagueKey = buildCountryLeagueKey(entity.getCountry(), entity.getLeague());
            String currentDelFlg = countryLeagueDelFlgMap.get(countryLeagueKey);

            if (currentDelFlg == null) {
                countryLeagueDelFlgMap.put(countryLeagueKey, entity.getDelFlg());
            } else if (!currentDelFlg.equals(entity.getDelFlg())) {
                throw new IllegalArgumentException(
                        "同一 country / league の delFlg は同じ値にしてください: "
                                + entity.getCountry() + " / " + entity.getLeague());
            }

            List<PointSettingEntity> existingList = pointSettingRepository.findData(
                    entity.getCountry(),
                    entity.getLeague(),
                    entity.getRemarks());

            PointSettingEntity existing = existingList.isEmpty() ? null : existingList.get(0);

            if (existing != null) {
                entity.setId(existing.getId());
                pointSettingRepository.update(entity);
            } else {
                entity.setDelFlg("0".equals(entity.getDelFlg()) ? "0" : entity.getDelFlg());
                pointSettingRepository.insert(entity);
            }

            List<PointSettingEntity> savedList = pointSettingRepository.findData(
                    entity.getCountry(),
                    entity.getLeague(),
                    entity.getRemarks());

            if (!savedList.isEmpty()) {
                results.add(savedList.get(0));
            }
        }

        // 最後に country/league 単位で両テーブルの del_flg を同期
        for (Map.Entry<String, String> entry : countryLeagueDelFlgMap.entrySet()) {
            String[] keys = entry.getKey().split("\\|\\|", 2);
            String country = keys[0];
            String league = keys[1];
            String delFlg = entry.getValue();

            pointSettingRepository.updateDelFlgByCountryAndLeague(country, league, delFlg);
            countryLeagueSeasonMasterWebRepository.updateDelFlgByCountryAndLeague(country, league, delFlg);
        }

        return pointSettingRepository.findAll();
    }

    private String buildCountryLeagueKey(String country, String league) {
        return country + "||" + league;
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
