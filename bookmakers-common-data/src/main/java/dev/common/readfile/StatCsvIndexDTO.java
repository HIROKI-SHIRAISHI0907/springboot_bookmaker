package dev.common.readfile;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import lombok.Data;

@Data
public class StatCsvIndexDTO {

    private String key;         // 6770.csv or stats/6770.csv

    private Integer fileNo;     // 6770

    private String category;    // parts[2]

    private String home;        // parts[5]

    private String away;        // parts[8]

    private Set<Integer> seqs = new TreeSet<>(); // 昇順＋重複排除

    /**
     * 2026-11-22: 1列目（seq_key）を文字列のまま持つ（例: "zRLNYw4L-37"。旧形式の "65525" もそのまま）。
     * seq_key は数値ではないため {@link #seqs} には入らない。B011 の作成済み判定はこちらを使う。
     */
    private Set<String> seqKeys = new LinkedHashSet<>(); // 出現順＋重複排除

}
