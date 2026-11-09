package dev.web.api.bm_a037;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * サブリーグ保有国リーグ登録設定: 保存のリクエスト（変更した 国・リーグ だけ）
 * @author shiraishitoshio
 */
@Data
public class SubLeagueSeasonSaveRequest {

	private List<SubLeagueSeasonItem> items = new ArrayList<>();
}
