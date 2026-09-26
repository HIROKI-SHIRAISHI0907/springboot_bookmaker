package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.S3Bucket;
import dev.web.api.bm_a013.DashboardDtos.S3Summary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;

@Service
public class S3DashboardService {

	private final S3Client s3;

	public S3DashboardService(S3Client s3) {
		this.s3 = s3;
	}

	public S3Summary summary(DateRange fmt) {
		List<S3Bucket> buckets = new ArrayList<S3Bucket>();
		Map<String, Integer> byRegion = new TreeMap<String, Integer>();

		for (Bucket b : s3.listBuckets().buckets()) {
			String region = resolveRegion(b);
			Integer cur = byRegion.get(region);
			byRegion.put(region, cur == null ? 1 : cur + 1);
			buckets.add(new S3Bucket(b.name(), region, fmt.format(b.creationDate())));
		}
		Collections.sort(buckets, new Comparator<S3Bucket>() {
			@Override
			public int compare(S3Bucket a, S3Bucket b) {
				return a.getName().compareTo(b.getName());
			}
		});
		return new S3Summary(buckets.size(), byRegion, buckets);
	}

	private String resolveRegion(Bucket b) {
		// GetBucketLocation でリージョンを取得（古い AWS SDK でも使える方法）
		try {
			String loc = s3.getBucketLocation(GetBucketLocationRequest.builder().bucket(b.name()).build())
					.locationConstraintAsString();
			// us-east-1 のバケットは空文字が返る仕様
			return isBlank(loc) ? "us-east-1" : loc;
		} catch (Exception e) {
			return "(unknown)";
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}