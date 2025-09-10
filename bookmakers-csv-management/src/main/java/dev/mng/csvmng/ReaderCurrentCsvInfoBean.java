package dev.mng.csvmng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;
import jakarta.annotation.PostConstruct;

/**
 * 既存CSV情報読み取り処理
 */
@Component
public class ReaderCurrentCsvInfoBean {

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/** CSV読み取り処理 */
	@Autowired
	private GetStatInfo getStatInfo;

	/** CSV通番情報 */
	private Map<String, List<Integer>> csvInfo;

	/**
	 * 既存CSV情報読み取り
	 */
	@PostConstruct
	public void init() {
	    Map<String, Map<String, List<BookDataEntity>>> data = this.getStatInfo.getData("0", null);
	    this.csvInfo = buildCsvInfoWithVersus(data);  // Map<String, List<Integer>>
	}

	/** キー: "<versus>-<fileNo>", 値: そのCSVの seq を昇順・重複なしで収集。
	 *  返却マップは fileNo の昇順で走査できるよう LinkedHashMap に整列。 */
	private Map<String, List<Integer>> buildCsvInfoWithVersus(
	        Map<String, Map<String, List<BookDataEntity>>> data) {

	    Map<String, Set<Integer>> acc = new HashMap<>();

	    // ★ 外側の「カテゴリ」キーを使う
	    for (Map.Entry<String, Map<String, List<BookDataEntity>>> top : data.entrySet()) {
	        String categoryKey = top.getKey();
	        Map<String, List<BookDataEntity>> vsMap = top.getValue();

	        for (Map.Entry<String, List<BookDataEntity>> e : vsMap.entrySet()) {
	            String versus = e.getKey();
	            List<BookDataEntity> list = e.getValue();
	            if (list == null || list.isEmpty()) continue;

	            Integer fileNo = fileNoOf(list.get(0).getFilePath());
	            if (fileNo == null) continue;

	            String key = categoryKey + "_" + versus + "_" + fileNo;

	            Set<Integer> seqs = acc.computeIfAbsent(key, k -> new TreeSet<>());
	            for (BookDataEntity b : list) {
	                String s = b.getSeq();
	                if (s == null || s.isBlank()) continue;
	                try {
	                    seqs.add(Integer.parseInt(s)); // 重複排除＋昇順
	                } catch (NumberFormatException ignore) {}
	            }
	        }
	    }

	    // fileNo（キー末尾）で昇順ソートして LinkedHashMap へ
	    Map<String, List<Integer>> result = new LinkedHashMap<>();
	    acc.entrySet().stream()
	       .sorted(Comparator.comparingInt(en -> fileNoFromKeyUnderscore(en.getKey())))
	       .forEach(en -> result.put(en.getKey(), new ArrayList<>(en.getValue())));
	    return result;
	}

	/** パス末尾の「<数字>.csv」から数字を取り出す。取れない場合は null */
	private Integer fileNoOf(String path) {
	    if (path == null) return null;
	    int no = Integer.parseInt(
	    		path.replace(this.config.getCsvFolder(), "").replace(BookMakersCommonConst.CSV, ""));
	    return no;
	}

	/** "<versus>-<fileNo>" の末尾 fileNo を取り出す（versus に '-' があっても OK） */
	private static int fileNoFromKeyUnderscore(String key) {
	    int idx = key.lastIndexOf('_');   // ★ '_' に変更
	    if (idx < 0 || idx + 1 >= key.length()) return Integer.MAX_VALUE;
	    try {
	        return Integer.parseInt(key.substring(idx + 1));
	    } catch (NumberFormatException e) {
	        return Integer.MAX_VALUE;
	    }
	}

	/**
	 * CSV情報を取得
	 * @return
	 */
	public Map<String, List<Integer>> getCsvInfo() {
		return csvInfo;
	}

}
