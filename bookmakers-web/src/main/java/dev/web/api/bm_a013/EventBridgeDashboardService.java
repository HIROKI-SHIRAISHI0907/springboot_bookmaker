package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.EventBridgeRule;
import dev.web.api.bm_a013.DashboardDtos.EventBridgeSchedule;
import dev.web.api.bm_a013.DashboardDtos.EventBridgeSummary;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.EventBus;
import software.amazon.awssdk.services.eventbridge.model.ListEventBusesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListEventBusesResponse;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.Rule;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.GetScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.GetScheduleResponse;
import software.amazon.awssdk.services.scheduler.model.ListSchedulesRequest;
import software.amazon.awssdk.services.scheduler.model.ListSchedulesResponse;
import software.amazon.awssdk.services.scheduler.model.ScheduleSummary;

/**
 * EventBridge の定期実行・イベント連携の一覧。
 *
 * - EventBridge Scheduler（スケジュール）: cron / rate / at と、起動先（ECS タスク定義など）
 * - EventBridge ルール: スケジュール式 or イベントパターンと、ターゲット
 *
 * Scheduler とルールは権限が別なので、片方が取れなくてももう片方は表示する。
 */
@Service
public class EventBridgeDashboardService {

	private final EventBridgeClient events;
	private final SchedulerClient scheduler;

	public EventBridgeDashboardService(EventBridgeClient events, SchedulerClient scheduler) {
		this.events = events;
		this.scheduler = scheduler;
	}

	public EventBridgeSummary summary(DateRange fmt) {
		List<EventBridgeSchedule> schedules = new ArrayList<EventBridgeSchedule>();
		String scheduleError = null;
		try {
			schedules = fetchSchedules(fmt);
		} catch (Exception e) {
			scheduleError = e.getClass().getSimpleName() + ": " + e.getMessage();
		}

		List<EventBridgeRule> rules = new ArrayList<EventBridgeRule>();
		int busCount = 0;
		String ruleError = null;
		try {
			List<String> buses = fetchEventBusNames();
			busCount = buses.size();
			rules = fetchRules(buses);
		} catch (Exception e) {
			ruleError = e.getClass().getSimpleName() + ": " + e.getMessage();
		}

		int enabledSchedules = 0;
		for (EventBridgeSchedule s : schedules) {
			if ("ENABLED".equals(s.getState())) {
				enabledSchedules++;
			}
		}
		int enabledRules = 0;
		for (EventBridgeRule r : rules) {
			if ("ENABLED".equals(r.getState())) {
				enabledRules++;
			}
		}

		return new EventBridgeSummary(schedules.size(), enabledSchedules, busCount, rules.size(), enabledRules,
				schedules, rules, scheduleError, ruleError);
	}

	// =====================================================================
	// EventBridge Scheduler
	// =====================================================================

	private List<EventBridgeSchedule> fetchSchedules(DateRange fmt) {
		List<EventBridgeSchedule> out = new ArrayList<EventBridgeSchedule>();
		String token = null;
		do {
			ListSchedulesResponse res = scheduler.listSchedules(
					ListSchedulesRequest.builder().nextToken(token).maxResults(100).build());
			for (ScheduleSummary s : res.schedules()) {
				out.add(toSchedule(s, fmt));
			}
			token = res.nextToken();
		} while (token != null && !token.isEmpty());

		Collections.sort(out, new Comparator<EventBridgeSchedule>() {
			@Override
			public int compare(EventBridgeSchedule a, EventBridgeSchedule b) {
				int c = nz(a.getGroup()).compareTo(nz(b.getGroup()));
				return c != 0 ? c : nz(a.getName()).compareTo(nz(b.getName()));
			}
		});
		return out;
	}

	/** 一覧 API にはスケジュール式が無いので GetSchedule で補う（失敗しても一覧の情報は出す） */
	private EventBridgeSchedule toSchedule(ScheduleSummary s, DateRange fmt) {
		String expression = null;
		String timezone = null;
		String description = null;
		String targetArn = s.target() == null ? null : s.target().arn();
		String taskDefinition = null;
		String flexibleWindow = null;

		try {
			GetScheduleResponse d = scheduler.getSchedule(
					GetScheduleRequest.builder().name(s.name()).groupName(s.groupName()).build());
			expression = d.scheduleExpression();
			timezone = d.scheduleExpressionTimezone();
			description = d.description();
			if (d.target() != null) {
				targetArn = d.target().arn();
				if (d.target().ecsParameters() != null) {
					taskDefinition = EcsDashboardService.taskFamily(d.target().ecsParameters().taskDefinitionArn());
				}
			}
			if (d.flexibleTimeWindow() != null) {
				flexibleWindow = d.flexibleTimeWindow().modeAsString();
			}
		} catch (Exception ignore) {
			// scheduler:GetSchedule が無い場合など。一覧の情報だけ返す
		}

		return new EventBridgeSchedule(s.groupName(), s.name(), s.stateAsString(), expression, timezone,
				shortArn(targetArn), taskDefinition, flexibleWindow, description,
				fmt.format(s.lastModificationDate()));
	}

	// =====================================================================
	// EventBridge ルール
	// =====================================================================

	private List<String> fetchEventBusNames() {
		List<String> out = new ArrayList<String>();
		String token = null;
		do {
			ListEventBusesResponse res = events.listEventBuses(
					ListEventBusesRequest.builder().nextToken(token).limit(100).build());
			for (EventBus b : res.eventBuses()) {
				out.add(b.name());
			}
			token = res.nextToken();
		} while (token != null && !token.isEmpty());
		if (out.isEmpty()) {
			out.add("default");
		}
		return out;
	}

	private List<EventBridgeRule> fetchRules(List<String> buses) {
		List<EventBridgeRule> out = new ArrayList<EventBridgeRule>();
		for (String bus : buses) {
			String token = null;
			do {
				ListRulesResponse res = events.listRules(
						ListRulesRequest.builder().eventBusName(bus).nextToken(token).limit(100).build());
				for (Rule r : res.rules()) {
					out.add(new EventBridgeRule(bus, r.name(), r.stateAsString(), r.scheduleExpression(),
							r.eventPattern() != null && !r.eventPattern().isEmpty(), r.description(),
							r.managedBy(), fetchTargets(bus, r.name())));
				}
				token = res.nextToken();
			} while (token != null && !token.isEmpty());
		}
		Collections.sort(out, new Comparator<EventBridgeRule>() {
			@Override
			public int compare(EventBridgeRule a, EventBridgeRule b) {
				int c = nz(a.getBus()).compareTo(nz(b.getBus()));
				return c != 0 ? c : nz(a.getName()).compareTo(nz(b.getName()));
			}
		});
		return out;
	}

	/** ルールのターゲット（"ecs:cluster/xxx (task=family)" のような表示用文字列） */
	private List<String> fetchTargets(String bus, String rule) {
		List<String> out = new ArrayList<String>();
		try {
			String token = null;
			do {
				ListTargetsByRuleResponse res = events.listTargetsByRule(ListTargetsByRuleRequest.builder()
						.eventBusName(bus).rule(rule).nextToken(token).limit(100).build());
				for (Target t : res.targets()) {
					String label = shortArn(t.arn());
					if (t.ecsParameters() != null && t.ecsParameters().taskDefinitionArn() != null) {
						label += " (task=" + EcsDashboardService.taskFamily(t.ecsParameters().taskDefinitionArn()) + ")";
					}
					out.add(label);
				}
				token = res.nextToken();
			} while (token != null && !token.isEmpty());
		} catch (Exception e) {
			out.add("(取得失敗: " + e.getClass().getSimpleName() + ")");
		}
		return out;
	}

	// =====================================================================
	// helpers
	// =====================================================================

	/** arn:aws:ecs:ap-northeast-1:123:cluster/main → ecs:cluster/main */
	static String shortArn(String arn) {
		if (arn == null) {
			return null;
		}
		String[] p = arn.split(":", 6);
		if (p.length < 6) {
			return arn;
		}
		return p[2] + ":" + p[5];
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
