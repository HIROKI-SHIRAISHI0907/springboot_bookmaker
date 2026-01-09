package dev.batch.util;

import java.security.SecureRandom;

/**
 * job_id_util
 * @author shiraishitoshio
 *
 */
public class JobIdUtil {

	/** ランダム */
    private static final SecureRandom RND = new SecureRandom();

    /** アルファベット羅列 */
    private static final char[] ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /** コンストラクタ禁止 */
    private JobIdUtil(){}

    /**
     * ランダム生成文字列
     * @param prefix
     * @return
     */
    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(10);
        sb.append(prefix).append("-");
        for (int i = 0; i < 5; i++) {
            sb.append(ALNUM[RND.nextInt(ALNUM.length)]);
        }
        return sb.toString();
    }
}
