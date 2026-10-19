package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * CSV アップロードの反映（B015）用リポジトリ（bm DB・static_data の非表示・再表示）
 *
 * <ul>
 *   <li>キー: ホームチーム + アウェーチーム + データカテゴリ。該当する行（その試合のデータすべて）が対象。</li>
 *   <li>全角・半角の揺れと前後の空白は無視して比べる。del_flg: '0' = 表示 / '1' = 非表示。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Mapper
public interface CsvUploadRealtimeBatchRepository {

	/** キーに該当する行数 */
	@Select("""
			SELECT COUNT(*)
			  FROM static_data
			 WHERE normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(#{home}), NFKC)
			   AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(#{away}), NFKC)
			   AND normalize(BTRIM(data_category), NFKC) = normalize(BTRIM(#{category}), NFKC)
			""")
	int countByKey(@Param("home") String home, @Param("away") String away, @Param("category") String category);

	/** キーに該当する行をまとめて非表示（'1'）/ 再表示（'0'） */
	@Update("""
			UPDATE static_data
			   SET del_flg = #{delFlg}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(#{home}), NFKC)
			   AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(#{away}), NFKC)
			   AND normalize(BTRIM(data_category), NFKC) = normalize(BTRIM(#{category}), NFKC)
			""")
	int updateDelFlgByKey(@Param("home") String home, @Param("away") String away, @Param("category") String category,
			@Param("delFlg") String delFlg, @Param("user") String user);
}
