package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.RdsDatabaseRef;
import dev.web.api.bm_a013.DashboardDtos.RdsInstance;
import dev.web.api.bm_a013.DashboardDtos.RdsSummary;
import dev.web.api.bm_a013.DashboardDtos.RdsTables;
import dev.web.config.AwsDashboardPropertiesConfig;
import dev.web.repository.bm.RdsTableStatsRepository;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;

/**
 * RDS インスタンス情報（AWS API）＋ DB ごとのテーブル件数（アプリの DataSource 経由）
 */
@Service
public class RdsDashboardService {

	private final RdsClient rds;
	private final RdsTableStatsRepository tableStatsRepository;
	private final AwsDashboardPropertiesConfig props;

	public RdsDashboardService(RdsClient rds, RdsTableStatsRepository tableStatsRepository,
			AwsDashboardPropertiesConfig props) {
		this.rds = rds;
		this.tableStatsRepository = tableStatsRepository;
		this.props = props;
	}

	/** インスタンス一覧 + 接続先 DB 一覧（件数はまだ数えない） */
	public RdsSummary summary() {
		List<RdsInstance> instances = fetchInstances();
		List<RdsDatabaseRef> databases = tableStatsRepository.databases();
		return new RdsSummary(instances.size(), instances, databases);
	}

	/** 接続先 DB 一覧だけ（RDS の AWS API を呼ばない） */
	public List<RdsDatabaseRef> databases() {
		return tableStatsRepository.databases();
	}

	/** 指定 DB の全スキーマのテーブル件数 */
	public RdsTables tables(String databaseKey) {
		return tableStatsRepository.tables(databaseKey, props.getRdsExcludeTables(), props.isRdsExactCount(),
				props.getRdsExactCountMaxRows());
	}

	public List<RdsInstance> fetchInstances() {
		List<RdsInstance> out = new ArrayList<RdsInstance>();
		for (DBInstance i : rds.describeDBInstancesPaginator().dbInstances()) {
			out.add(new RdsInstance(
					i.dbInstanceIdentifier(),
					i.engine(),
					i.engineVersion(),
					i.dbInstanceClass(),
					i.dbInstanceStatus(),
					i.endpoint() == null ? null : i.endpoint().address(),
					i.endpoint() == null ? null : i.endpoint().port(),
					Boolean.TRUE.equals(i.multiAZ()),
					i.allocatedStorage()));
		}
		Collections.sort(out, new Comparator<RdsInstance>() {
			@Override
			public int compare(RdsInstance a, RdsInstance b) {
				return a.getIdentifier().compareTo(b.getIdentifier());
			}
		});
		return out;
	}
}
