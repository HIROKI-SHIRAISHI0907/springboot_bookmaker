package dev.web.api.bm_a013;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ダッシュボード API のレスポンス DTO 群。
 * Jackson は getter から JSON を作る（boolean は isXxx() → "xxx"）。
 */
public final class DashboardDtos {

	private DashboardDtos() {
	}

	// ===================== 概要 =====================

	/** 1サービス分の概要カード */
	@Getter
	@AllArgsConstructor
	public static class OverviewItem {
		private final String service;
		private final boolean ok;
		private final Map<String, Object> metrics;
		private final String error;
	}

	@Getter
	@AllArgsConstructor
	public static class Overview {
		private final String date;
		private final String accountId;
		private final String region;
		private final List<OverviewItem> items;
	}

	// ===================== ECS =====================

	@Getter
	@AllArgsConstructor
	public static class EcsCluster {
		private final String name;
		private final String status;
		private final int runningTasks;
		private final int pendingTasks;
		private final int activeServices;
	}

	/** CloudTrail の RunTask イベント 1件 */
	@Getter
	@AllArgsConstructor
	public static class EcsRun {
		private final String eventTime;
		private final String cluster;
		private final String taskDefinition;
		private final String startedBy;
		private final String invokedBy;
		private final int launchedTasks;
		private final int failures;
		private final String errorCode;
	}

	@Getter
	@AllArgsConstructor
	public static class EcsTaskDefCount {
		private final String taskDefinition;
		private final int runs;
		private final int launchedTasks;
		private final int failedRuns;
	}

	@Getter
	@AllArgsConstructor
	public static class EcsSummary {
		private final String date;
		private final int runCount;
		private final int launchedTaskCount;
		private final int failedRunCount;
		private final int[] hourly;
		private final List<EcsTaskDefCount> byTaskDefinition;
		private final List<EcsRun> runs;
		private final List<EcsCluster> clusters;
	}

	// ===================== S3 =====================

	@Getter
	@AllArgsConstructor
	public static class S3Bucket {
		private final String name;
		private final String region;
		private final String creationDate;
	}

	@Getter
	@AllArgsConstructor
	public static class S3Summary {
		private final int bucketCount;
		private final Map<String, Integer> byRegion;
		private final List<S3Bucket> buckets;
	}

	// ===================== RDS =====================

	@Getter
	@AllArgsConstructor
	public static class RdsInstance {
		private final String identifier;
		private final String engine;
		private final String engineVersion;
		private final String instanceClass;
		private final String status;
		private final String endpoint;
		private final Integer port;
		private final boolean multiAz;
		private final Integer allocatedStorageGb;
	}

	@Getter
	@AllArgsConstructor
	public static class TableCount {
		private final String table;
		private final long rows;
	}

	@Getter
	@AllArgsConstructor
	public static class RdsSummary {
		private final int instanceCount;
		private final List<RdsInstance> instances;
		private final String database;
		private final String schema;
		private final boolean exactCount;
		private final int tableCount;
		private final long totalRows;
		private final List<TableCount> tables;
		private final String tableError;
	}

	// ===================== IAM =====================

	@Getter
	@AllArgsConstructor
	public static class IamUser {
		private final String name;
		private final String createDate;
		private final String passwordLastUsed;
	}

	@Getter
	@AllArgsConstructor
	public static class IamRole {
		private final String name;
		private final String path;
		private final String createDate;
		private final String description;
	}

	@Getter
	@AllArgsConstructor
	public static class IamSummary {
		private final Map<String, Integer> accountSummary;
		private final List<IamUser> users;
		private final List<IamRole> roles;
	}

	// ===================== Lambda =====================

	@Getter
	@AllArgsConstructor
	public static class LambdaFunction {
		private final String name;
		private final String runtime;
		private final Integer memoryMb;
		private final Integer timeoutSec;
		private final Long codeSizeBytes;
		private final String lastModified;
		private final String handler;
		private final long invocations;
		private final long errors;
	}

	@Getter
	@AllArgsConstructor
	public static class LambdaSummary {
		private final String date;
		private final int functionCount;
		private final long totalInvocations;
		private final long totalErrors;
		private final List<LambdaFunction> functions;
	}

	// ===================== DynamoDB =====================

	@Getter
	@AllArgsConstructor
	public static class DynamoTable {
		private final String name;
		private final String status;
		private final Long itemCount;
		private final Long sizeBytes;
		private final String billingMode;
		private final String creationDate;
	}

	@Getter
	@AllArgsConstructor
	public static class DynamoSummary {
		private final int tableCount;
		private final long totalItems;
		private final List<DynamoTable> tables;
	}

	// ===================== EC2 =====================

	@Getter
	@AllArgsConstructor
	public static class Ec2Instance {
		private final String instanceId;
		private final String name;
		private final String type;
		private final String state;
		private final String az;
		private final String privateIp;
		private final String publicIp;
		private final String launchTime;
		private final String platform;
	}

	@Getter
	@AllArgsConstructor
	public static class Ec2Summary {
		private final int instanceCount;
		private final Map<String, Integer> byState;
		private final List<Ec2Instance> instances;
	}

	// ===================== Route53 =====================

	@Getter
	@AllArgsConstructor
	public static class HostedZone {
		private final String id;
		private final String name;
		private final boolean privateZone;
		private final Long recordCount;
		private final String comment;
	}

	@Getter
	@AllArgsConstructor
	public static class RecordSet {
		private final String name;
		private final String type;
		private final Long ttl;
		private final List<String> values;
		private final String aliasTarget;
	}

	@Getter
	@AllArgsConstructor
	public static class Route53Summary {
		private final int zoneCount;
		private final long totalRecords;
		private final List<HostedZone> zones;
	}
}
