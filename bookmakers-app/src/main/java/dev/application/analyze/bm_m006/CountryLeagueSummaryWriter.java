package dev.application.analyze.bm_m006;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.CountryLeagueSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M006 登録・更新処理
 */
@Service
public class CountryLeagueSummaryWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSummaryWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSummaryWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M006";

	/** CountryLeagueSummaryRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSummaryRepository countryLeagueSummaryRepository;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 登録・更新
	 * @param country 国
	 * @param league リーグ
	 * @param addCount 加算件数
	 */
	@Transactional
	public void upsert(String country, String league, int addCount) {
		CountryLeagueSummaryOutputDTO dto = getData(country, league);
		saveWithDuplicateFallback(
				dto.isUpdFlg(),
				dto.getSeq(),
				country,
				league,
				dto.getCnt(),
				String.valueOf(addCount));
	}

	/**
	 * DuplicateKey 時は再読込して update に切替
	 * @param updFlg
	 * @param id
	 * @param country
	 * @param league
	 * @param befCnt
	 * @param addCnt
	 */
	private void saveWithDuplicateFallback(
			boolean updFlg,
			String id,
			String country,
			String league,
			String befCnt,
			String addCnt) {

		final String METHOD_NAME = "saveWithDuplicateFallback";

		CountryLeagueSummaryEntity entity = new CountryLeagueSummaryEntity();
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setDataCount("0");

		try {
			if (updFlg) {
				// 既存あり: 加算更新
				int newCnt = parseOrZero(befCnt) + parseOrZero(addCnt);
				entity.setId(id);
				entity.setCsvCount(String.valueOf(newCnt));

				int result = this.countryLeagueSummaryRepository.update(entity);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							String.format("id=%s, country=%s, league=%s", id, country, league));
				}

				String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER + " 更新件数: " + result + "件");
			} else {
				// 新規: insert
				entity.setCsvCount(addCnt);

				int result = this.countryLeagueSummaryRepository.insert(entity);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							null);
				}

				String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER + " 登録件数: " + result + "件");
			}
		} catch (DuplicateKeyException dup) {
			// 競合: 再取得して update
			List<CountryLeagueSummaryEntity> rows =
					this.countryLeagueSummaryRepository.findByCountryLeague(country, league);

			if (!rows.isEmpty()) {
				CountryLeagueSummaryEntity cur = rows.get(0);
				int newCnt = parseOrZero(cur.getCsvCount()) + parseOrZero(addCnt);

				CountryLeagueSummaryEntity updateEntity = new CountryLeagueSummaryEntity();
				updateEntity.setId(cur.getId());
				updateEntity.setCountry(country);
				updateEntity.setLeague(league);
				updateEntity.setDataCount(cur.getDataCount() == null ? "0" : cur.getDataCount());
				updateEntity.setCsvCount(String.valueOf(newCnt));

				int result = this.countryLeagueSummaryRepository.update(updateEntity);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							String.format("id=%s, country=%s, league=%s", cur.getId(), country, league));
				}

				String messageCd = MessageCdConst.MCD00009I_REINSERT_DUE_TO_DUPLICATION_OR_COMPETITION;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER + " 登録件数: " + result + "件");
			} else {
				throw dup;
			}
		}
	}

	/**
	 * 取得データ
	 * @param country 国
	 * @param league リーグ
	 * @return CountryLeagueSummaryOutputDTO
	 */
	private CountryLeagueSummaryOutputDTO getData(String country, String league) {
		CountryLeagueSummaryOutputDTO dto = new CountryLeagueSummaryOutputDTO();
		List<CountryLeagueSummaryEntity> datas =
				this.countryLeagueSummaryRepository.findByCountryLeague(country, league);

		if (!datas.isEmpty()) {
			dto.setUpdFlg(true);
			dto.setSeq(datas.get(0).getId());
			dto.setCnt(datas.get(0).getCsvCount());
		} else {
			dto.setUpdFlg(false);
			dto.setCnt("0");
		}
		return dto;
	}

	/**
	 * null/空文字防止
	 * @param s
	 * @return
	 */
	private static int parseOrZero(String s) {
		if (s == null || s.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
