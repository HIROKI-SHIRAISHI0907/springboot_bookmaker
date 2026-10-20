package dev.web.api.bm_a033;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * リーグレベル情報反映設定: 保存（POST /v1/api/admin/league-level）の本文。変更した 国・リーグ だけ送る
 * @author shiraishitoshio
 */
@Data
public class LeagueLevelSaveRequest {

	private List<LeagueLevelItem> items = new ArrayList<>();
}
