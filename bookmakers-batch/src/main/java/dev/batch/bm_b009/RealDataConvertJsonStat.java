package dev.batch.bm_b009;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.repository.master.AllLeagueMasterBatchRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * RealDataConvertJsonStatロジック
 * <ul>
 *   <li>スクレイピング対象（all_league_scrape_master.disp_flg = '0'）の国・リーグを b001_country_league.json にして、
 *       各スクレイパーのバケットの json/ に置く。</li>
 *   <li>大会のバケット（cupSchedule）には、同じキー json/b001_country_league.json に「チーム名つき」を置く（2026-11-02 追加）。
 *       中身はスクレイピング対象の国リーグに紐づくチーム。大会の結果タブのスクレイパー（CupResultData_aws_S3.py）は、
 *       このチームのどちらかが出た試合だけを取る（例: ACL のガンバ大阪-ブリーラムは取る。両方サウジのアルナスル-アルヒラルは取らない）。
 *       <pre>
 * {"日本": [{"league": "J1 リーグ",
 *            "teams": [{"team": "ガンバ大阪", "link": "/team/gamba-osaka/zLQAGOBK/"}, ...]}, ...], ...}
 *       </pre></li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class RealDataConvertJsonStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataConvertJsonStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataConvertJsonStat.class.getName();

	/** チームのリンクのチームID（/team/gamba-osaka/zLQAGOBK/ → zLQAGOBK） */
	private static final Pattern TEAM_ID = Pattern.compile("/team/[^/]+/([A-Za-z0-9]+)/?");

	/** 大会のバケットに置くチーム名つき b001（キーはほかのバケットと同じ json/b001_country_league.json） */
	public static final String CUP_TEAM_FILE = "b001_country_league.json";

	/** 実行モード */
	private static final String EXEC_MODE = "REAL_DATA_CONVERT_JSON";

	/** AllLeagueMasterBatchRepository */
	@Autowired
	private AllLeagueMasterBatchRepository allLeagueMasterBatchRepository;

	/** CountryLeagueMasterBatchRepository（チーム名つき b001 用） */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	/** JSON 生成に利用する ObjectMapper。 */
	@Autowired
	private ObjectMapper objectMapper;

	/** パスや外部実行設定（Python/S3等）を保持する設定クラス。 */
	@Autowired
	private PathConfig pathConfig;

	/** S3Operator。 */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// バケット名取得
		String outputBucket = pathConfig.getS3BucketsOutputs();
		String seasonBucket = pathConfig.getS3BucketsTeamSeasonDateData();
		String teamBucket = pathConfig.getS3BucketsTeamData();
		String teamMemberBucket = pathConfig.getS3BucketsTeamMemberData();
		String futureBucket = pathConfig.getS3BucketsFuture();
		String noEcsBucket = pathConfig.getS3NoEcs();
		String delayPostponeBucket = pathConfig.getS3BucketsOutputsDelayPostpone();
		String nextSeasonBucket = pathConfig.getS3BucketsOutputsNextSeason();

		final String jsonFolder = pathConfig.getB001JsonFolder(); // /tmp/json/
		final String jsonPath = jsonFolder + "b001_country_league.json";
		final Path jsonFilePath = Paths.get(jsonPath);
		final String s3Key = "json/" + jsonFilePath.getFileName().toString();

		// disp_flgが0のものだけ取得
		List<AllLeagueMasterEntity> entities = allLeagueMasterBatchRepository.
				findActiveByCountryAndLeagueAtJson();

		// entities -> Map<String, Set<String>> に変換
		Map<String, Set<String>> countryLeagueMap = buildCountryLeagueMap(entities);

		// リクエストで受け取ったデータをObjectMapperで変換
		Files.createDirectories(jsonFilePath.getParent());
		makeJson(jsonPath, countryLeagueMap);

		// upload
		upload(outputBucket, s3Key, jsonFilePath);
		upload(seasonBucket, s3Key, jsonFilePath);
		upload(teamBucket, s3Key, jsonFilePath);
		upload(teamMemberBucket, s3Key, jsonFilePath);
		upload(futureBucket, s3Key, jsonFilePath);
		upload(noEcsBucket, s3Key, jsonFilePath);
		upload(delayPostponeBucket, s3Key, jsonFilePath);
		upload(nextSeasonBucket, s3Key, jsonFilePath);

		// 大会のバケットには「チーム名つき」を同じキーで置く（失敗してもほかのバケットの出力はそのまま）
		String cupBucket = pathConfig.getS3BucketsCupSchedule();
		final String cupS3Key = "json/" + CUP_TEAM_FILE;
		try {
			final Path cupJsonFilePath = Paths.get(jsonFolder, "cup", CUP_TEAM_FILE);
			Files.createDirectories(cupJsonFilePath.getParent());
			List<CountryLeagueMasterEntity> teams = countryLeagueMasterBatchRepository.findActiveTeamsAtCupJson();
			this.objectMapper.writerWithDefaultPrettyPrinter()
					.writeValue(cupJsonFilePath.toFile(), buildCountryLeagueTeamMap(countryLeagueMap, teams));
			upload(cupBucket, cupS3Key, cupJsonFilePath);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e,
					"チーム名つき b001 を作れませんでした bucket: " + cupBucket + ", key: " + cupS3Key);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 国・リーグの組み合わせを {@code Map<国, Set<リーグ>>} 形式に変換する。
	 *
	 * <p>
	 * JSON出力用の内部表現として利用する。リーグ順序を保持したい場合に備えて
	 * {@link LinkedHashSet} を使用する。
	 * </p>
	 *
	 * @param entities 変換元（国・リーグ情報を持つエンティティリスト）
	 * @return 国をキー、リーグ集合を値とするマップ
	 */
	private Map<String, Set<String>> buildCountryLeagueMap(List<AllLeagueMasterEntity> entities) {
		Map<String, Set<String>> map = new HashMap<>();
		for (AllLeagueMasterEntity entity : entities) {
			map.computeIfAbsent(entity.getCountry(), k -> new LinkedHashSet<>())
					.add(entity.getLeague());
		}
		return map;
	}

	/**
	 * 国・リーグ・チームを「チーム名つき b001」の形に変換する。
	 *
	 * <p>国・リーグは b001 と同じ（スクレイピング対象）。チームがまだ無いリーグも teams = [] で出す。
	 * 国・リーグ・チームは名前順。</p>
	 * <p>同じリーグに同じチームが複数行あるとき（リンクのチームIDが同じ。リンクが無ければチーム名が同じ）は 1 行にまとめる。
	 * 残すのは id の大きい（新しい）行（teams は id の大きい順に来る）。</p>
	 *
	 * @param countryLeagueMap 国 → リーグ（b001 と同じもの）
	 * @param teams スクレイピング対象のリーグにいる有効なチーム
	 * @return 国 → [{league, teams: [{team, link}]}]
	 */
	private Map<String, List<Map<String, Object>>> buildCountryLeagueTeamMap(
			Map<String, Set<String>> countryLeagueMap, List<CountryLeagueMasterEntity> teams) {
		Map<String, Map<String, List<Map<String, String>>>> tmp = new TreeMap<>();
		for (Map.Entry<String, Set<String>> e : countryLeagueMap.entrySet()) {
			Map<String, List<Map<String, String>>> leagues = tmp.computeIfAbsent(e.getKey(), k -> new TreeMap<>());
			for (String league : e.getValue()) {
				leagues.computeIfAbsent(league, k -> new ArrayList<>());
			}
		}
		Set<String> seen = new java.util.HashSet<>();
		for (CountryLeagueMasterEntity t : teams) {
			Map<String, List<Map<String, String>>> leagues = tmp.get(t.getCountry());
			if (leagues == null || !leagues.containsKey(t.getLeague()) || t.getTeam() == null || t.getTeam().isBlank()) {
				continue;
			}
			if (!seen.add(t.getCountry() + "\t" + t.getLeague() + "\t" + sameTeamKey(t))) {
				continue; // 同じリーグに同じチームが 2 行目以降
			}
			Map<String, String> tm = new LinkedHashMap<>();
			tm.put("team", t.getTeam());
			tm.put("link", t.getLink() == null ? "" : t.getLink());
			leagues.get(t.getLeague()).add(tm);
		}
		Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, List<Map<String, String>>>> c : tmp.entrySet()) {
			List<Map<String, Object>> list = new ArrayList<>();
			for (Map.Entry<String, List<Map<String, String>>> l : c.getValue().entrySet()) {
				Map<String, Object> lm = new LinkedHashMap<>();
				lm.put("league", l.getKey());
				lm.put("teams", l.getValue());
				list.add(lm);
			}
			out.put(c.getKey(), list);
		}
		return out;
	}

	/** 同じチームかの比較キー（リンクのチームID、無ければチーム名を NFKC・空白除去・小文字にしたもの） */
	private static String sameTeamKey(CountryLeagueMasterEntity t) {
		if (t.getLink() != null) {
			Matcher m = TEAM_ID.matcher(t.getLink().trim());
			if (m.find()) {
				return "id:" + m.group(1);
			}
		}
		return "name:" + Normalizer.normalize(t.getTeam(), Normalizer.Form.NFKC).replaceAll("\\s+", "").toLowerCase();
	}

	/**
	 * 指定のパスへ {@code b001_country_league.json} を作成する。
	 *
	 * <p>
	 * 出力形式は pretty print とし、Python 側が読みやすい形式で保存する。
	 * </p>
	 *
	 * @param jsonPath 作成先JSONパス（ファイルパス）
	 * @param countryLeagueMap 国をキー、リーグ集合を値とするマップ
	 * @throws StreamWriteException JSON書き込みに失敗した場合
	 * @throws DatabindException    変換に失敗した場合
	 * @throws IOException          ファイルI/Oで失敗した場合
	 */
	private void makeJson(String jsonPath, Map<String, Set<String>> countryLeagueMap)
			throws StreamWriteException, DatabindException, IOException {
		this.objectMapper.writerWithDefaultPrettyPrinter()
				.writeValue(new File(jsonPath), countryLeagueMap);
	}

	/** upload */
	private void upload(String bucket, String key, Path file) {
		final String METHOD_NAME = "upload";
		try {
			s3Operator.uploadFile(bucket, key, file);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"bucket: " + bucket + ", key: " + key + ", file: " + file);
		}

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
			    "bucket: " + bucket + ", key: " + key + ", file: " + file);
	}

}
