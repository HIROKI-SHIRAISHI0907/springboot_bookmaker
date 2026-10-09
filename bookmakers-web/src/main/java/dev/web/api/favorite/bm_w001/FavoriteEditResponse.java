package dev.web.api.favorite.bm_w001;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * お気に入りチームの一覧（登録・削除の後もこれを返す）
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteEditResponse {

	private List<FavoriteTeamDTO> teams;

	/** 登録できる上限 */
	private int maxTeams;

	/** 結果のメッセージ（追加しました・上限です など。無ければ null） */
	private String message;
}
