package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.HostedZone;
import dev.web.api.bm_a013.DashboardDtos.RecordSet;
import dev.web.api.bm_a013.DashboardDtos.Route53Summary;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

@Service
public class Route53DashboardService {

	private final Route53Client route53;

	public Route53DashboardService(Route53Client route53) {
		this.route53 = route53;
	}

	public Route53Summary summary() {
		List<HostedZone> zones = new ArrayList<HostedZone>();
		long total = 0;
		for (software.amazon.awssdk.services.route53.model.HostedZone z : route53.listHostedZonesPaginator()
				.hostedZones()) {
			boolean priv = z.config() != null && Boolean.TRUE.equals(z.config().privateZone());
			String comment = z.config() == null ? null : z.config().comment();
			zones.add(new HostedZone(shortId(z.id()), z.name(), priv, z.resourceRecordSetCount(), comment));
			total += z.resourceRecordSetCount() == null ? 0 : z.resourceRecordSetCount().longValue();
		}
		Collections.sort(zones, new Comparator<HostedZone>() {
			@Override
			public int compare(HostedZone a, HostedZone b) {
				return a.getName().compareTo(b.getName());
			}
		});
		return new Route53Summary(zones.size(), total, zones);
	}

	/** ホストゾーンのレコード一覧（画面でゾーンを選んだときに取得） */
	public List<RecordSet> records(String zoneId) {
		if (zoneId == null || !zoneId.matches("^[A-Z0-9]+$")) {
			throw new IllegalArgumentException("zoneId が不正です: " + zoneId);
		}
		List<RecordSet> out = new ArrayList<RecordSet>();
		ListResourceRecordSetsRequest.Builder req = ListResourceRecordSetsRequest.builder()
				.hostedZoneId(zoneId).maxItems("300");

		while (true) {
			ListResourceRecordSetsResponse res = route53.listResourceRecordSets(req.build());
			for (ResourceRecordSet r : res.resourceRecordSets()) {
				List<String> values = new ArrayList<String>();
				if (r.resourceRecords() != null) {
					for (ResourceRecord rr : r.resourceRecords()) {
						values.add(rr.value());
					}
				}
				String alias = r.aliasTarget() == null ? null : r.aliasTarget().dnsName();
				out.add(new RecordSet(r.name(), r.typeAsString(), r.ttl(), values, alias));
			}
			if (!Boolean.TRUE.equals(res.isTruncated())) {
				break;
			}
			req.startRecordName(res.nextRecordName())
					.startRecordType(res.nextRecordTypeAsString())
					.startRecordIdentifier(res.nextRecordIdentifier());
		}
		return out;
	}

	/** /hostedzone/Z123 → Z123 */
	private static String shortId(String id) {
		return id == null ? null : id.replace("/hostedzone/", "");
	}
}
