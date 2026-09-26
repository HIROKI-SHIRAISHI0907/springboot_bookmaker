package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.IamRole;
import dev.web.api.bm_a013.DashboardDtos.IamSummary;
import dev.web.api.bm_a013.DashboardDtos.IamUser;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.User;

@Service
public class IamDashboardService {

	/** GetAccountSummary の中から画面に出すキー */
	private static final List<String> SUMMARY_KEYS = Collections.unmodifiableList(Arrays.asList(
			"Users", "Roles", "Groups", "Policies", "MFADevices", "MFADevicesInUse",
			"AccessKeysPerUserQuota", "AccountMFAEnabled", "Providers", "InstanceProfiles"));

	private final IamClient iam;

	public IamDashboardService(IamClient iam) {
		this.iam = iam;
	}

	public IamSummary summary(DateRange fmt) {
		Map<String, Integer> all = accountSummary();

		List<IamUser> users = new ArrayList<IamUser>();
		for (User u : iam.listUsersPaginator().users()) {
			users.add(new IamUser(u.userName(), fmt.format(u.createDate()), fmt.format(u.passwordLastUsed())));
		}
		Collections.sort(users, new Comparator<IamUser>() {
			@Override
			public int compare(IamUser a, IamUser b) {
				return a.getName().compareTo(b.getName());
			}
		});

		List<IamRole> roles = new ArrayList<IamRole>();
		for (Role r : iam.listRolesPaginator().roles()) {
			roles.add(new IamRole(r.roleName(), r.path(), fmt.format(r.createDate()), r.description()));
		}
		Collections.sort(roles, new Comparator<IamRole>() {
			@Override
			public int compare(IamRole a, IamRole b) {
				return a.getName().compareTo(b.getName());
			}
		});

		return new IamSummary(all, users, roles);
	}

	public Map<String, Integer> accountSummary() {
		Map<String, Integer> raw = iam.getAccountSummary().summaryMapAsStrings();
		Map<String, Integer> out = new LinkedHashMap<String, Integer>();
		for (String k : SUMMARY_KEYS) {
			if (raw.containsKey(k)) {
				out.put(k, raw.get(k));
			}
		}
		return out;
	}
}