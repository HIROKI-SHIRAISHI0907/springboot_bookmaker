package dev.common.readfile;

import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * ファイル内容読み込みIF
 * @author shiraishitoshio
 *
 */
public interface ReadFileBodyIF {

	/**
	 * ファイル読み込み
	 * @param fileFullPath パス
	 * @return
	 */
	public ReadFileOutputDTO getFileBody(String fileFullPath);

}
