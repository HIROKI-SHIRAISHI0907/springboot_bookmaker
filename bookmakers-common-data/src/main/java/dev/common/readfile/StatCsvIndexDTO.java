package dev.common.readfile;

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

}
