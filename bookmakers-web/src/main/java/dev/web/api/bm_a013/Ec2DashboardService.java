package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.Ec2Instance;
import dev.web.api.bm_a013.DashboardDtos.Ec2Summary;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Tag;

@Service
public class Ec2DashboardService {

	private final Ec2Client ec2;

	public Ec2DashboardService(Ec2Client ec2) {
		this.ec2 = ec2;
	}

	public Ec2Summary summary(DateRange fmt) {
		List<Ec2Instance> out = new ArrayList<Ec2Instance>();
		Map<String, Integer> byState = new TreeMap<String, Integer>();

		for (Reservation r : ec2.describeInstancesPaginator().reservations()) {
			for (Instance i : r.instances()) {
				String state = i.state() == null ? "unknown" : i.state().nameAsString();
				Integer cur = byState.get(state);
				byState.put(state, cur == null ? 1 : cur + 1);
				out.add(new Ec2Instance(
						i.instanceId(),
						nameTag(i.tags()),
						i.instanceTypeAsString(),
						state,
						i.placement() == null ? null : i.placement().availabilityZone(),
						i.privateIpAddress(),
						i.publicIpAddress(),
						fmt.format(i.launchTime()),
						i.platformDetails()));
			}
		}

		// 状態順 → インスタンスID順
		Collections.sort(out, new Comparator<Ec2Instance>() {
			@Override
			public int compare(Ec2Instance a, Ec2Instance b) {
				int c = a.getState().compareTo(b.getState());
				return c != 0 ? c : a.getInstanceId().compareTo(b.getInstanceId());
			}
		});
		return new Ec2Summary(out.size(), byState, out);
	}

	private static String nameTag(List<Tag> tags) {
		if (tags == null) {
			return null;
		}
		for (Tag t : tags) {
			if ("Name".equals(t.key())) {
				return t.value();
			}
		}
		return null;
	}
}