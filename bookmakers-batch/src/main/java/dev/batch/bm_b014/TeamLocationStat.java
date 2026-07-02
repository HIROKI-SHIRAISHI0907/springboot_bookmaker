package dev.batch.bm_b014;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.TeamLocationEntityIF;
import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.TeamLocationRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import dev.common.util.ExecuteMainUtil.StadiumSplitResult;

/**
 * TeamLocationStat登録ロジック
 * @author shiraishitoshio
 *
 */
@Service
public class TeamLocationStat implements TeamLocationEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamLocationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamLocationStat.class.getName();

	/** データテーブル取得件数 */
	private static final int LIMIT = 200;

	@Autowired
	private BookDataRepository bookDataRepository; // bm
	@Autowired
	private TeamLocationRepository teamLocationRepository;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void teamLocationStat() throws Exception {
		final String METHOD_NAME = "teamLocationStat";
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// dataテーブルの全件数を取得
		int total = bookDataRepository.countStadium();
		for (int offset = 0; offset < total; offset += LIMIT) {
			List<DataEntity> list = bookDataRepository.findStadium(LIMIT, offset);

			manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, "範囲" + offset + "~" + (offset + LIMIT) + ", "
							+ "データテーブル取得サイズ: " + list.size());

			if (list == null || list.isEmpty()) {
				break;
			}

			int countAll = 0;
			for (DataEntity entity : list) {
				String dataCategory = entity.getDataCategory();
				String homeTeamName = entity.getHomeTeamName();
				List<String> countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
				if (countryLeague.size() == 1)
					continue;

				String location = ExecuteMainUtil.normalizeText(entity.getLocation());

				StadiumSplitResult splitResult = ExecuteMainUtil.splitStadiumAndCity(entity.getStudium());
				String studium = splitResult.getStadiumName();

				// location が空なら、studium末尾の都市名を採用
				if (location == null || location.isBlank()) {
					location = splitResult.getCityName();
				}

				TeamLocationEntity insertEntity = new TeamLocationEntity();
				insertEntity.setCountry(countryLeague.get(0));
				insertEntity.setTeamName(homeTeamName);
				insertEntity.setHomeCity(location);
				insertEntity.setStadiumName(studium);
				int counts = teamLocationRepository.count(insertEntity);
				if (counts > 0) continue;

				insertEntity.setGeocodeSource("B014_batch");

				int rows = teamLocationRepository.insert(insertEntity);
				if (rows != 1) {
					throw new RuntimeException(
							"team_location_master insert affected rows=" + rows
									+ " country=" + countryLeague.get(0)
									+ " homeCity=" + location
									+ " stadium=" + studium);
				}

				String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "登録件数: " + rows + "件, (国: " +
								countryLeague.get(0) + ", チーム: " + homeTeamName + ", 都市: " + location + ", スタジアム: " + studium);
				countAll += rows;
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "全体登録件数: " + countAll + "件");

			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

}