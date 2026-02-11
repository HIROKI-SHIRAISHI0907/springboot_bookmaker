package dev.common.readfile.dto;

import java.util.List;

import dev.common.dto.AbstractResultErrDetailOutputDTO;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.BookDataEntity;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.entity.TeamMemberMasterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ブック読み取りoutputDTO
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ReadFileOutputDTO extends AbstractResultErrDetailOutputDTO {

	/**
	 * 結果コード(終了コード)
	 */
	private String resultCd;

	/**
	 * 読み取り結果リスト
	 */
	private List<DataEntity> dataList;

	/**
	 * 読み取り結果リスト
	 */
	private List<BookDataEntity> readHoldDataList;

	/**
	 * 読み取り結果リスト
	 */
	private List<BookDataEntity> readDataList;

	/**
	 * 未来データ結果リスト
	 */
	private List<FutureEntity> futureList;

	/**
	 * 選手データ結果リスト
	 */
	private List<TeamMemberMasterEntity> memberList;

	/**
	 * シーズンデータ結果リスト
	 */
	private List<CountryLeagueSeasonMasterEntity> countryLeagueSeasonList;

	/**
	 * マスタデータ結果リスト
	 */
	private List<CountryLeagueMasterEntity> countryLeagueMasterList;

	/**
	 * 全容マスタデータ結果リスト
	 */
	private List<AllLeagueMasterEntity> allLeagueMasterList;

}
