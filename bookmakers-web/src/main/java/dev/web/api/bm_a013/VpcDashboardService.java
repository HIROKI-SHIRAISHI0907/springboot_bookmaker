package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.ElasticIpInfo;
import dev.web.api.bm_a013.DashboardDtos.NatGatewayInfo;
import dev.web.api.bm_a013.DashboardDtos.SecurityGroupInfo;
import dev.web.api.bm_a013.DashboardDtos.SubnetInfo;
import dev.web.api.bm_a013.DashboardDtos.VpcEndpointInfo;
import dev.web.api.bm_a013.DashboardDtos.VpcInfo;
import dev.web.api.bm_a013.DashboardDtos.VpcSummary;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Address;
import software.amazon.awssdk.services.ec2.model.InternetGateway;
import software.amazon.awssdk.services.ec2.model.InternetGatewayAttachment;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.Ipv6Range;
import software.amazon.awssdk.services.ec2.model.NatGateway;
import software.amazon.awssdk.services.ec2.model.NatGatewayAddress;
import software.amazon.awssdk.services.ec2.model.PrefixListId;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.ec2.model.VpcEndpoint;

/**
 * VPC まわりのネットワーク構成。
 *
 * VPC / サブネット / セキュリティグループ（インバウンドルール要約・全開放の検出）/
 * NAT ゲートウェイ / インターネットゲートウェイ / VPC エンドポイント / Elastic IP
 *
 * ※ NAT ゲートウェイと未使用の Elastic IP は時間課金されるので、画面で目立つようにしている。
 */
@Service
public class VpcDashboardService {

	private final Ec2Client ec2;

	public VpcDashboardService(Ec2Client ec2) {
		this.ec2 = ec2;
	}

	public VpcSummary summary() {
		List<Vpc> vpcs = new ArrayList<Vpc>();
		for (Vpc v : ec2.describeVpcsPaginator().vpcs()) {
			vpcs.add(v);
		}

		// ----- サブネット -----
		List<SubnetInfo> subnets = new ArrayList<SubnetInfo>();
		Map<String, Integer> subnetCountByVpc = new HashMap<String, Integer>();
		for (Subnet s : ec2.describeSubnetsPaginator().subnets()) {
			subnets.add(new SubnetInfo(s.subnetId(), nameTag(s.tags()), s.vpcId(), s.cidrBlock(),
					s.availabilityZone(), s.availableIpAddressCount(), Boolean.TRUE.equals(s.mapPublicIpOnLaunch())));
			inc(subnetCountByVpc, s.vpcId());
		}
		Collections.sort(subnets, new Comparator<SubnetInfo>() {
			@Override
			public int compare(SubnetInfo a, SubnetInfo b) {
				int c = nz(a.getVpcId()).compareTo(nz(b.getVpcId()));
				if (c != 0) {
					return c;
				}
				c = nz(a.getAz()).compareTo(nz(b.getAz()));
				return c != 0 ? c : nz(a.getSubnetId()).compareTo(nz(b.getSubnetId()));
			}
		});

		// ----- セキュリティグループ -----
		List<SecurityGroupInfo> groups = new ArrayList<SecurityGroupInfo>();
		Map<String, Integer> sgCountByVpc = new HashMap<String, Integer>();
		int openToWorldCount = 0;
		for (SecurityGroup g : ec2.describeSecurityGroupsPaginator().securityGroups()) {
			List<String> inbound = new ArrayList<String>();
			boolean openToWorld = false;
			if (g.ipPermissions() != null) {
				for (IpPermission p : g.ipPermissions()) {
					String port = portLabel(p);
					for (String src : sources(p)) {
						inbound.add(port + " ← " + src);
						if ("0.0.0.0/0".equals(src) || "::/0".equals(src)) {
							openToWorld = true;
						}
					}
				}
			}
			if (openToWorld) {
				openToWorldCount++;
			}
			int outbound = g.ipPermissionsEgress() == null ? 0 : g.ipPermissionsEgress().size();
			groups.add(new SecurityGroupInfo(g.groupId(), g.groupName(), g.vpcId(), g.description(), inbound,
					outbound, openToWorld));
			inc(sgCountByVpc, g.vpcId());
		}
		Collections.sort(groups, new Comparator<SecurityGroupInfo>() {
			@Override
			public int compare(SecurityGroupInfo a, SecurityGroupInfo b) {
				int c = nz(a.getVpcId()).compareTo(nz(b.getVpcId()));
				return c != 0 ? c : nz(a.getName()).compareTo(nz(b.getName()));
			}
		});

		// ----- インターネットゲートウェイ（VPC ごとの有無） -----
		Map<String, String> igwByVpc = new HashMap<String, String>();
		int igwCount = 0;
		for (InternetGateway igw : ec2.describeInternetGatewaysPaginator().internetGateways()) {
			igwCount++;
			if (igw.attachments() != null) {
				for (InternetGatewayAttachment a : igw.attachments()) {
					igwByVpc.put(a.vpcId(), igw.internetGatewayId());
				}
			}
		}

		// ----- NAT ゲートウェイ -----
		List<NatGatewayInfo> nats = new ArrayList<NatGatewayInfo>();
		int activeNat = 0;
		for (NatGateway n : ec2.describeNatGatewaysPaginator().natGateways()) {
			String state = n.stateAsString();
			if ("deleted".equals(state)) {
				continue; // 削除済みは表示しない
			}
			if ("available".equals(state) || "pending".equals(state)) {
				activeNat++;
			}
			String publicIp = null;
			if (n.natGatewayAddresses() != null) {
				for (NatGatewayAddress a : n.natGatewayAddresses()) {
					if (a.publicIp() != null) {
						publicIp = a.publicIp();
						break;
					}
				}
			}
			nats.add(new NatGatewayInfo(n.natGatewayId(), nameTag(n.tags()), n.vpcId(), n.subnetId(), state,
					n.connectivityTypeAsString(), publicIp));
		}

		// ----- VPC エンドポイント -----
		List<VpcEndpointInfo> endpoints = new ArrayList<VpcEndpointInfo>();
		for (VpcEndpoint e : ec2.describeVpcEndpointsPaginator().vpcEndpoints()) {
			endpoints.add(new VpcEndpointInfo(e.vpcEndpointId(), e.vpcId(), e.serviceName(),
					e.vpcEndpointTypeAsString(), e.stateAsString()));
		}
		Collections.sort(endpoints, new Comparator<VpcEndpointInfo>() {
			@Override
			public int compare(VpcEndpointInfo a, VpcEndpointInfo b) {
				return nz(a.getServiceName()).compareTo(nz(b.getServiceName()));
			}
		});

		// ----- Elastic IP -----
		List<ElasticIpInfo> eips = new ArrayList<ElasticIpInfo>();
		int unassociated = 0;
		for (Address a : ec2.describeAddresses().addresses()) {
			boolean associated = a.associationId() != null && !a.associationId().isEmpty();
			if (!associated) {
				unassociated++;
			}
			eips.add(new ElasticIpInfo(a.publicIp(), a.allocationId(), nameTag(a.tags()), associated,
					a.instanceId(), a.networkInterfaceId()));
		}

		// ----- VPC 一覧（件数を付けて） -----
		List<VpcInfo> vpcInfos = new ArrayList<VpcInfo>();
		for (Vpc v : vpcs) {
			vpcInfos.add(new VpcInfo(v.vpcId(), nameTag(v.tags()), v.cidrBlock(), v.stateAsString(),
					Boolean.TRUE.equals(v.isDefault()), get(subnetCountByVpc, v.vpcId()),
					get(sgCountByVpc, v.vpcId()), igwByVpc.get(v.vpcId())));
		}
		Collections.sort(vpcInfos, new Comparator<VpcInfo>() {
			@Override
			public int compare(VpcInfo a, VpcInfo b) {
				// デフォルト VPC は後ろ
				if (a.isDefaultVpc() != b.isDefaultVpc()) {
					return a.isDefaultVpc() ? 1 : -1;
				}
				return nz(a.getVpcId()).compareTo(nz(b.getVpcId()));
			}
		});

		return new VpcSummary(vpcInfos.size(), subnets.size(), groups.size(), openToWorldCount, igwCount,
				activeNat, endpoints.size(), eips.size(), unassociated,
				vpcInfos, subnets, groups, nats, endpoints, eips);
	}

	// =====================================================================
	// helpers
	// =====================================================================

	/** "tcp 443" / "tcp 8000-8080" / "all" / "icmp" */
	private static String portLabel(IpPermission p) {
		String proto = p.ipProtocol();
		if (proto == null || "-1".equals(proto)) {
			return "all";
		}
		Integer from = p.fromPort();
		Integer to = p.toPort();
		if (from == null || from.intValue() == -1) {
			return proto;
		}
		if (to == null || from.equals(to)) {
			return proto + " " + from;
		}
		return proto + " " + from + "-" + to;
	}

	/** 許可元（CIDR / IPv6 / 別SG / プレフィックスリスト） */
	private static List<String> sources(IpPermission p) {
		List<String> out = new ArrayList<String>();
		if (p.ipRanges() != null) {
			for (IpRange r : p.ipRanges()) {
				out.add(r.cidrIp());
			}
		}
		if (p.ipv6Ranges() != null) {
			for (Ipv6Range r : p.ipv6Ranges()) {
				out.add(r.cidrIpv6());
			}
		}
		if (p.userIdGroupPairs() != null) {
			for (UserIdGroupPair g : p.userIdGroupPairs()) {
				out.add(g.groupId());
			}
		}
		if (p.prefixListIds() != null) {
			for (PrefixListId pl : p.prefixListIds()) {
				out.add(pl.prefixListId());
			}
		}
		if (out.isEmpty()) {
			out.add("(なし)");
		}
		return out;
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

	private static void inc(Map<String, Integer> m, String key) {
		if (key == null) {
			return;
		}
		Integer cur = m.get(key);
		m.put(key, cur == null ? 1 : cur + 1);
	}

	private static int get(Map<String, Integer> m, String key) {
		Integer v = m.get(key);
		return v == null ? 0 : v.intValue();
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
