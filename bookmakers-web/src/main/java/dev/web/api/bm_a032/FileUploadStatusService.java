package dev.web.api.bm_a032;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.s3.S3Operator;
import dev.web.api.bm_a031.CsvUploadRowError;
import dev.web.api.bm_a031.CsvUploadStatus;
import dev.web.util.CsvUtil;
import dev.web.repository.bm.FileUploadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ファイルアップロード状況管理画面
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadStatusService {

	public static final int DEFAULT_SIZE = 50;
	public static final int MAX_SIZE = 200;

	private static final Set<String> MASTER_NAMES = Set.of("season", "team", "future", "realtime");
	private static final Set<String> STATUSES = Set.of(CsvUploadStatus.PENDING, CsvUploadStatus.PROCESSING,
			CsvUploadStatus.SUCCESS, CsvUploadStatus.ERROR);

	private final FileUploadStatusRepository repository;

	private final ObjectMapper objectMapper;

	private final S3Operator s3Operator;

	/**
	 * 異常終了のファイル（error/ の {名前}_error.zip。中の CSV に「エラー詳細」列付き）を S3 から取り出す
	 * <p>ダウンロードできるのは異常終了（ERROR）だけ。判定前・判定中・正常終了は null（Controller で 404）。</p>
	 * @return ファイル名と中身。記録が無い・異常終了でない・S3 に無いときは null
	 */
	public CsvUtil.CsvFile downloadErrorFile(long id) {
		FileUploadStatusDTO d = repository.findById(id);
		if (d == null || !CsvUploadStatus.ERROR.equals(d.getStatus()) || d.getS3Bucket() == null || d.getS3Key() == null) {
			return null;
		}
		try (java.io.InputStream in = s3Operator.download(d.getS3Bucket(), d.getS3Key())) {
			return new CsvUtil.CsvFile(CsvUtil.baseName(d.getS3Key()), in.readAllBytes());
		} catch (Exception e) {
			log.warn("エラーファイルを取り出せません s3://{}/{}", d.getS3Bucket(), d.getS3Key(), e);
			return null;
		}
	}

	/**
	 * 一覧
	 * @throws IllegalArgumentException 条件の形が正しくないとき（画面に 400 で返す）
	 */
	public FileUploadStatusListResponse search(FileUploadStatusSearchCondition c, Integer page, Integer size) {
		validate(c);
		int s = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		int p = page == null || page <= 0 ? 1 : page;

		FileUploadStatusListResponse res = new FileUploadStatusListResponse();
		res.setPage(p);
		res.setSize(s);
		res.setTotal(repository.count(c));
		res.setSummary(repository.summary(c));
		res.setItems(repository.search(c, s, (p - 1) * s));
		return res;
	}

	/** 詳細（エラー行を含む）。無ければ null */
	public FileUploadStatusDTO find(long id) {
		FileUploadStatusDTO d = repository.findById(id);
		if (d == null) {
			return null;
		}
		d.setErrors(parseErrors(d.getErrorDetailJson()));
		return d;
	}

	private List<CsvUploadRowError> parseErrors(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<CsvUploadRowError>>() {});
		} catch (Exception e) {
			log.warn("error_detail の読み込みに失敗", e);
			return new ArrayList<>();
		}
	}

	private static void validate(FileUploadStatusSearchCondition c) {
		if (notEmpty(c.getMasterName()) && !MASTER_NAMES.contains(c.getMasterName())) {
			throw new IllegalArgumentException("種類が正しくありません。（" + c.getMasterName() + "）");
		}
		if (notEmpty(c.getStatus()) && !STATUSES.contains(c.getStatus())) {
			throw new IllegalArgumentException("状態が正しくありません。（" + c.getStatus() + "）");
		}
		LocalDate from = parseDate(c.getDateFrom(), "受付日（から）");
		LocalDate to = parseDate(c.getDateTo(), "受付日（まで）");
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("受付日の「から」が「まで」より後になっています。");
		}
	}

	private static LocalDate parseDate(String v, String label) {
		if (!notEmpty(v)) {
			return null;
		}
		try {
			return LocalDate.parse(v.strip());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(label + "は yyyy-MM-dd で指定してください。（" + v + "）");
		}
	}

	private static boolean notEmpty(String s) {
		return s != null && !s.isBlank();
	}
}
