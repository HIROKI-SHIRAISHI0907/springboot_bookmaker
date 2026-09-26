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

	/** テーブル 1件（estimated=true は統計情報からの推定値） */
	@Getter
	@AllArgsConstructor
	public static class TableCount {
		private final String schema;
		private final String table;
		private final long rows;
		private final boolean estimated;
	}

	/** 接続先 DB（アプリに登録されている DataSource ごと） */
	@Getter
	@AllArgsConstructor
	public static class RdsDatabaseRef {
		/** API の db= に渡すキー（= DB 名） */
		private final String key;
		private final String database;
		private final String product;
		private final String beanName;
		/** 接続できなかった場合のエラー */
		private final String error;
	}

	/** RDS タブ上部（インスタンス + 接続先 DB 一覧） */
	@Getter
	@AllArgsConstructor
	public static class RdsSummary {
		private final int instanceCount;
		private final List<RdsInstance> instances;
		private final List<RdsDatabaseRef> databases;
	}

	/** 1 DB 分のテーブル件数（全スキーマ） */
	@Getter
	@AllArgsConstructor
	public static class RdsTables {
		private final String key;
		private final String database;
		private final String product;
		private final List<String> schemas;
		private final int tableCount;
		private final long totalRows;
		/** 推定値で表示しているテーブル数 */
		private final int estimatedTableCount;
		private final boolean exactCountEnabled;
		/** この件数以下のテーブルだけ COUNT(*) */
		private final long maxExactRows;
		private final List<TableCount> tables;
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

	// ===================== EventBridge =====================

	/** EventBridge Scheduler のスケジュール */
	@Getter
	@AllArgsConstructor
	public static class EventBridgeSchedule {
		private final String group;
		private final String name;
		/** ENABLED / DISABLED */
		private final String state;
		/** cron(...) / rate(...) / at(...) */
		private final String expression;
		private final String timezone;
		/** 起動先（例: ecs:cluster/main） */
		private final String target;
		/** 起動先が ECS のときのタスク定義ファミリー */
		private final String taskDefinition;
		/** OFF / FLEXIBLE */
		private final String flexibleWindow;
		private final String description;
		private final String lastModified;
	}

	/** EventBridge ルール */
	@Getter
	@AllArgsConstructor
	public static class EventBridgeRule {
		private final String bus;
		private final String name;
		private final String state;
		/** スケジュール式（イベントパターンのルールは null） */
		private final String scheduleExpression;
		/** イベントパターンで起動するルールか */
		private final boolean eventPattern;
		private final String description;
		/** AWS サービスが管理しているルール（例: ecs.amazonaws.com） */
		private final String managedBy;
		private final List<String> targets;
	}

	@Getter
	@AllArgsConstructor
	public static class EventBridgeSummary {
		private final int scheduleCount;
		private final int enabledScheduleCount;
		private final int busCount;
		private final int ruleCount;
		private final int enabledRuleCount;
		private final List<EventBridgeSchedule> schedules;
		private final List<EventBridgeRule> rules;
		/** Scheduler だけ取得できなかった場合のエラー */
		private final String scheduleError;
		/** ルールだけ取得できなかった場合のエラー */
		private final String ruleError;
	}

	// ===================== VPC =====================

	@Getter
	@AllArgsConstructor
	public static class VpcInfo {
		private final String vpcId;
		private final String name;
		private final String cidr;
		private final String state;
		private final boolean defaultVpc;
		private final int subnetCount;
		private final int securityGroupCount;
		/** アタッチされているインターネットゲートウェイ（無ければ null） */
		private final String internetGatewayId;
	}

	@Getter
	@AllArgsConstructor
	public static class SubnetInfo {
		private final String subnetId;
		private final String name;
		private final String vpcId;
		private final String cidr;
		private final String az;
		private final Integer availableIps;
		/** 起動時にパブリック IP を自動割り当てするか */
		private final boolean publicOnLaunch;
	}

	@Getter
	@AllArgsConstructor
	public static class SecurityGroupInfo {
		private final String groupId;
		private final String name;
		private final String vpcId;
		private final String description;
		/** インバウンドルール（例: "tcp 443 ← 0.0.0.0/0"） */
		private final List<String> inbound;
		private final int outboundRuleCount;
		/** 0.0.0.0/0 または ::/0 から許可しているルールがある */
		private final boolean openToWorld;
	}

	@Getter
	@AllArgsConstructor
	public static class NatGatewayInfo {
		private final String natGatewayId;
		private final String name;
		private final String vpcId;
		private final String subnetId;
		private final String state;
		/** public / private */
		private final String connectivity;
		private final String publicIp;
	}

	@Getter
	@AllArgsConstructor
	public static class VpcEndpointInfo {
		private final String endpointId;
		private final String vpcId;
		private final String serviceName;
		/** Interface / Gateway */
		private final String type;
		private final String state;
	}

	@Getter
	@AllArgsConstructor
	public static class ElasticIpInfo {
		private final String publicIp;
		private final String allocationId;
		private final String name;
		/** false = 未使用（時間課金される） */
		private final boolean associated;
		private final String instanceId;
		private final String networkInterfaceId;
	}

	@Getter
	@AllArgsConstructor
	public static class VpcSummary {
		private final int vpcCount;
		private final int subnetCount;
		private final int securityGroupCount;
		private final int openSecurityGroupCount;
		private final int internetGatewayCount;
		private final int activeNatGatewayCount;
		private final int endpointCount;
		private final int elasticIpCount;
		private final int unassociatedElasticIpCount;
		private final List<VpcInfo> vpcs;
		private final List<SubnetInfo> subnets;
		private final List<SecurityGroupInfo> securityGroups;
		private final List<NatGatewayInfo> natGateways;
		private final List<VpcEndpointInfo> endpoints;
		private final List<ElasticIpInfo> elasticIps;
	}
}
