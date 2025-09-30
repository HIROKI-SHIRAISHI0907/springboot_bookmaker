package dev.application.domain.repository;

import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

@Mapper
public interface LogicFlgRepository {

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	#{table};
			""")
	int findDataCount(String table);

	@Lang(XMLLanguageDriver.class)
	@Update("""
	  <script>
	    UPDATE ${table}
	    SET logic_flg = #{logicFlg}
	    WHERE country = #{country}
	      AND league  = #{league}
	  </script>
	""")
	int updateLogicFlgByCountryLeague(
	    @Param("table") String table,
	    @Param("country") String country,
	    @Param("league") String league,
	    @Param("logicFlg") String logicFlg);


	@Lang(XMLLanguageDriver.class)
	@Update("""
	  <script>
	    UPDATE ${table}
	    SET logic_flg = #{logicFlg}
	    WHERE data_category LIKE CONCAT(#{country}, ': ', #{league}, '%')
	  </script>
	""")
	int updateLogicFlgByCategoryLike(
	    @Param("table") String table,
	    @Param("country") String country,
	    @Param("league") String league,
	    @Param("logicFlg") String logicFlg);

	@Lang(XMLLanguageDriver.class)
	@Update("""
	  <script>
	    UPDATE ${table}
	    SET logic_flg = #{logicFlg}
	  </script>
	""")
	int updateAllLogicFlg(
	    @Param("table") String table,
	    @Param("logicFlg") String logicFlg);

}
