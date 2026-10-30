package dev.web.api.bm_a035;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 管理画面の未対応状況（GET /v1/api/admin/setup-status）
 * @author shiraishitoshio
 */
@Data
public class SetupStatusResponse {

	/** ブロック中か（block = true のチェックに未対応が1件でもある） */
	private boolean blocked;

	/** 全チェックの結果（件数 0 のものも含む） */
	private List<Item> items = new ArrayList<>();

	/** 未対応があっても開ける画面（system_view_master の setup_flg = '1'） */
	private List<View> setupViews = new ArrayList<>();

	@Data
	public static class Item {
		private String checkCode;
		private String checkName;
		private int count;
		/** 未対応の内容（多いときは先頭 {@value SetupStatusService#MAX_DETAILS} 件まで） */
		private List<String> details = new ArrayList<>();
		/** 解消する画面（system_view_master に無ければ null） */
		private String viewCode;
		private String viewName;
		/** 管理画面のパス（/admin/ より後ろ） */
		private String path;
		/** 未対応が残っていたら、ほかの画面を開けなくするか */
		private boolean block;
		/** チェックの実行に失敗したとき */
		private String error;
	}

	@Data
	public static class View {
		private String viewCode;
		private String viewName;
		private String path;
	}
}
