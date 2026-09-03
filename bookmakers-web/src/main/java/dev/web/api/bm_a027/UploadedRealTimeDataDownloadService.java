package dev.web.api.bm_a027;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;
import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.BookDataRepository.DataIngestRow;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * RealTimeDataService
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class UploadedRealTimeDataDownloadService {

	private final BookDataRepository repo;

	private final PathConfig config;

	private final S3Operator s3Operator;

	/**
	 * 初期表示用に、検索条件なしで一覧を取得する。
	 * S3にアップロードされている全zipを対象に、static_dataで補完できるものは補完して返す
	 * （中身は search に空の条件を渡した場合と同じ）。
	 *
	 * @return 検索条件なしの一覧
	 */
	@Transactional(readOnly = true)
	public List<UploadedRealTimeDataDownloadSearchResponse> init() {
		return search(new UploadedRealTimeDataDownloadSearchCondition());
	}

	@Transactional(readOnly = true)
	public List<UploadedRealTimeDataDownloadSearchResponse> search(UploadedRealTimeDataDownloadSearchCondition cond) {
		String country = cond.getCountry();
		String league = cond.getLeague();
		boolean finFlg = cond.isFinFlg();
		LocalDate uploadDate = cond.getUploadDate();

		// 1) まずS3（バックアップ用バケット）にアップロードされているzip（<matchId>.zip）の情報を取得する
		String bucket = config.getS3BucketsOutputsBackUp();
		List<S3Object> uploadedZipObjects = s3Operator.listObjectsBySuffix(bucket, ".zip");
		if (uploadedZipObjects.isEmpty()) {
			return Collections.emptyList();
		}

		// 2) zipファイル名の先頭（拡張子を除いた部分）がそのままmatchId候補になる。
		//    uploadDateが指定されていれば、zipの最終更新日時（JST日付）がuploadDateと
		//    一致するものだけに絞る（＝アップロードしている最新取得日時での絞り込み）。
		Map<String, S3Object> zipObjectsByCandidateMatchId = new LinkedHashMap<>();
		for (S3Object obj : uploadedZipObjects) {
			String key = obj.key();
			if (key == null) {
				continue;
			}
			String fileName = key.substring(key.lastIndexOf('/') + 1);
			if (!fileName.endsWith(".zip")) {
				continue;
			}
			if (uploadDate != null && !uploadDate.equals(toJstDate(obj))) {
				continue;
			}
			String candidateMatchId = fileName.substring(0, fileName.length() - ".zip".length());
			if (!candidateMatchId.isEmpty()) {
				zipObjectsByCandidateMatchId.put(candidateMatchId, obj);
			}
		}
		if (zipObjectsByCandidateMatchId.isEmpty()) {
			return Collections.emptyList();
		}

		// 3) static_dataは件数が多く重いため、S3に実在するmatchId一覧に絞ってから取得する
		//    （country/leagueのLIKE検索で全件スキャンしない）
		List<DataIngestRow> staticDataList = repo.findByMatchIds(zipObjectsByCandidateMatchId.keySet());

		// matchId単位で1件（seq_keyが最大＝最新のもの）に集約
		Map<String, DataIngestRow> staticDataByMatchId = new LinkedHashMap<>();
		for (DataIngestRow row : staticDataList) {
			if (row.matchId == null) {
				continue;
			}
			staticDataByMatchId.merge(row.matchId, row,
					(existing, next) -> existing.seq != null && next.seq != null
							&& existing.seq.compareTo(next.seq) >= 0 ? existing : next);
		}

		boolean hasCountry = country != null && !country.isBlank();
		boolean hasLeague = league != null && !league.isBlank();
		boolean hasSearchCondition = hasCountry || hasLeague || finFlg;

		// 最終更新日時（lastModified）が新しい順に並ぶようにソートする（無いものは末尾）
		List<Map.Entry<String, S3Object>> sortedEntries = new ArrayList<>(zipObjectsByCandidateMatchId.entrySet());
		sortedEntries.sort(Comparator.comparing(
				(Map.Entry<String, S3Object> e) -> e.getValue().lastModified(),
				Comparator.nullsLast(Comparator.reverseOrder())));

		List<UploadedRealTimeDataDownloadSearchResponse> responseList = new ArrayList<>();
		for (Map.Entry<String, S3Object> entry : sortedEntries) {
			String candidateMatchId = entry.getKey();
			S3Object obj = entry.getValue();
			DataIngestRow row = staticDataByMatchId.get(candidateMatchId);

			if (hasSearchCondition) {
				if (row == null) {
					// static_dataに該当が無ければ対象外
					continue;
				}
				if (hasCountry && !matchesCountry(row.dataCategory, country)) {
					continue;
				}
				if (hasLeague && !matchesLeague(row.dataCategory, league)) {
					continue;
				}
				if (finFlg && !BookMakersCommonConst.FIN.equals(row.times)) {
					continue;
				}
			}

			String matchId = row != null ? row.matchId : candidateMatchId;
			responseList.add(buildResponse(matchId, obj, row));
		}

		return responseList;
	}

	/**
	 * アップロード済みのreal-time-data（zip）をダウンロードする。
	 * fileNameは "matchId" / "matchId.zip" のどちらでも受け付ける。
	 *
	 * @param cond ダウンロード対象のファイル名を保持するリクエスト
	 * @return zipファイルをそのままボディに持つレスポンス（該当が無ければ404）
	 */
	public ResponseEntity<Resource> download(UploadedRealTimeDataDownloadRequest cond) {
		String fileName = cond.getFileName();
		if (fileName == null || fileName.isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		String zipFileName = fileName.endsWith(".zip") ? fileName : fileName + ".zip";

		String bucket = config.getS3BucketsOutputsBackUp();
		InputStream in;
		try {
			in = s3Operator.download(bucket, zipFileName);
		} catch (NoSuchKeyException e) {
			return ResponseEntity.notFound().build();
		}

		Resource resource = new InputStreamResource(in);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(resource);
	}

	/** data_categoryが "country:xxx" 形式で始まっているか（元のSQLの LIKE country:% 相当） */
	private boolean matchesCountry(String dataCategory, String country) {
		return dataCategory != null && dataCategory.startsWith(country.trim() + ":");
	}

	/** data_categoryに ": league" が含まれているか（元のSQLの LIKE %: league% 相当） */
	private boolean matchesLeague(String dataCategory, String league) {
		return dataCategory != null && dataCategory.contains(": " + league.trim());
	}

	private UploadedRealTimeDataDownloadSearchResponse buildResponse(String matchId, S3Object obj, DataIngestRow row) {
		UploadedRealTimeDataDownloadSearchResponse response = new UploadedRealTimeDataDownloadSearchResponse();
		response.setFileName(matchId + ".zip");
		response.setGameTeamName(row == null ? "" : row.homeTeamName + " vs " + row.awayTeamName);
		response.setGameProcess(row != null && BookMakersCommonConst.FIN.equals(row.times) ? "0" : "1");
		response.setSize(formatSize(obj.size()));
		response.setLastUpdateDate(toJstDate(obj));
		return response;
	}

	/** S3オブジェクトのlastModified（UTC）をJSTの日付に変換する。lastModifiedが無ければnull。 */
	private LocalDate toJstDate(S3Object obj) {
		return obj.lastModified() == null
				? null
				: obj.lastModified().atZone(DateOffsetDecisionUtil.getZoneId()).toLocalDate();
	}

	/**
	 * バイト数を "1.2 MB" のような表示用文字列に整形する。
	 */
	private String formatSize(Long bytes) {
		if (bytes == null) {
			return "";
		}
		double size = bytes;
		String[] units = { "B", "KB", "MB", "GB" };
		int unitIndex = 0;
		while (size >= 1024 && unitIndex < units.length - 1) {
			size /= 1024;
			unitIndex++;
		}
		return String.format("%.1f %s", size, units[unitIndex]);
	}


}