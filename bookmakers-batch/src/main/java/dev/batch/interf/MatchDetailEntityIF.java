package dev.batch.interf;

import java.util.Map;

import dev.common.readfile.dto.MatchDetailDataDTO;

/**
 * MatchDetail（試合詳細データ）共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface MatchDetailEntityIF {

	/**
	 * 試合詳細データ登録処理メソッド
	 * @param map key = S3 のフォルダ（試合）、value = その試合のデータ
	 * @throws Exception 1 試合でも登録に失敗した場合（成功した試合は登録済み）
	 */
	public void matchDetailStat(Map<String, MatchDetailDataDTO> map) throws Exception;

}
