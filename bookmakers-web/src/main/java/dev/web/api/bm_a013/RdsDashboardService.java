package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.RdsInstance;
import dev.web.api.bm_a013.DashboardDtos.RdsSummary;
import dev.web.api.bm_a013.DashboardDtos.TableCount;
import dev.web.config.AwsDashboardPropertiesConfig;
import dev.web.repository.bm.TableCountRepository;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;

/**
 * RDS インスタンス情報（AWS API）＋ テーブル件数（Spring の DataSource 経由）
 */
@Service
public class RdsDashboardService {

	private final RdsClient rds;
	private final TableCountRepository tableCountRepository;
	private final AwsDashboardPropertiesConfig props;

	public RdsDashboardService(RdsClient rds, TableCountRepository tableCountRepository,
			AwsDashboardPropertiesConfig props) {
		this.rds = rds;
		this.tableCountRepository = tableCountRepository;
		this.props = props;
	}

	public RdsSummary summary() {
		List<RdsInstance> instances = fetchInstances();

		// テーブル件数は DB 接続なので、失敗してもインスタンス情報は返す
		String database = null;
		String schema = null;
		List<TableCount> tables = new ArrayList<TableCount>();
		String tableError = null;
		try {
			TableCountRepository.DbInfo info = tableCountRepository.dbInfo(props.getRdsSchema());
			database = info.getDatabase();
			schema = info.getSchema();
			List<String> names = tableCountRepository.findTableNames(props.getRdsSchema(),
					props.getRdsExcludeTables());
			tables = props.isRdsExactCount()
					? tableCountRepository.countExact(props.getRdsSchema(), names)
					: tableCountRepository.countEstimated(props.getRdsSchema(), names);
		} catch (Exception e) {
			tableError = e.getClass().getSimpleName() + ": " + e.getMessage();
		}

		List<TableCount> sorted = new ArrayList<TableCount>(tables);
		Collections.sort(sorted, new Comparator<TableCount>() {
			@Override
			public int compare(TableCount a, TableCount b) {
				return Long.compare(b.getRows(), a.getRows());
			}
		});
		long total = 0;
		for (TableCount t : sorted) {
			total += t.getRows();
		}

		return new RdsSummary(instances.size(), instances, database, schema, props.isRdsExactCount(),
				sorted.size(), total, sorted, tableError);
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
