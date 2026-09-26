package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.DynamoSummary;
import dev.web.api.bm_a013.DashboardDtos.DynamoTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * DynamoDB テーブル一覧。
 * itemCount / tableSizeBytes は AWS 側で約6時間ごとに更新される概算値。
 */
@Service
public class DynamoDbDashboardService {

	private final DynamoDbClient dynamo;

	public DynamoDbDashboardService(DynamoDbClient dynamo) {
		this.dynamo = dynamo;
	}

	public DynamoSummary summary(DateRange fmt) {
		List<DynamoTable> out = new ArrayList<DynamoTable>();
		long total = 0;
		for (String name : dynamo.listTablesPaginator().tableNames()) {
			TableDescription t = dynamo.describeTable(DescribeTableRequest.builder().tableName(name).build()).table();
			String billing = t.billingModeSummary() == null ? "PROVISIONED"
					: t.billingModeSummary().billingModeAsString();
			out.add(new DynamoTable(t.tableName(), t.tableStatusAsString(), t.itemCount(), t.tableSizeBytes(),
					billing, fmt.format(t.creationDateTime())));
			total += t.itemCount() == null ? 0 : t.itemCount().longValue();
		}
		Collections.sort(out, new Comparator<DynamoTable>() {
			@Override
			public int compare(DynamoTable a, DynamoTable b) {
				return a.getName().compareTo(b.getName());
			}
		});
		return new DynamoSummary(out.size(), total, out);
	}
}
