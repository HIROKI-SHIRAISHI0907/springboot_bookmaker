package dev.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainsCountryLeagueUtil {

	/**
	 * 除去リスト
	 */
	private static final List<String> CONTAINS_LIST;
	static {
		List<String> list = new ArrayList<>();
		list.add("アルゼンチン: トルネオ・ベターノ");
		list.add("イタリア: セリエ A");
		list.add("イタリア: セリエ B");
		list.add("イングランド: EFL リーグ 1");
		list.add("イングランド: EFL チャンピオンシップ");
		list.add("イングランド: プレミアリーグ");
		list.add("インドネシア: リーガ 1");
		list.add("ウガンダ: プレミアリーグ");
		list.add("ウクライナ: プレミアリーグ");
		list.add("エクアドル: リーガ・プロ");
		list.add("エストニア: メスタリリーガ");
		list.add("エチオピア: プレミアリーグ");
		list.add("オーストラリア: A リーグ・メン");
		list.add("オランダ: エールディビジ");
		list.add("カメルーン: エリート 1");
		list.add("ケニア: プレミアリーグ");
		list.add("コスタリカ: リーガ FPD");
		list.add("コロンビア: プリメーラ A");
		list.add("ジャマイカ: プレミアリーグ");
		list.add("セルビア: スーペルリーガ");
		list.add("スコットランド: プレミアシップ");
		list.add("スペイン: ラ・リーガ");
		list.add("スペイン: ラ・リーガ 2部");
		list.add("タンザニア: プレミアリーグ");
		list.add("チュニジア: チュニジア･プロリーグ");
		list.add("ドイツ: ブンデスリーガ");
		list.add("トルコ: スュペル・リグ");
		list.add("ブラジル: セリエ A ベターノ");
		list.add("ブラジル: セリエ B");
		list.add("フランス: リーグ・アン");
		list.add("ブルガリア: パルヴァ・リーガ");
		list.add("ペルー: リーガ 1");
		list.add("ベルギー: ジュピラー･プロリーグ");
		list.add("ボリビア: LFPB");
		list.add("ポルトガル: リーガ・ポルトガル");
		list.add("メキシコ: リーガ MX");
		list.add("韓国: K リーグ 1");
		list.add("日本: J1 リーグ");
		list.add("日本: J2 リーグ");
		list.add("日本: J3 リーグ");
		list.add("日本: YBC ルヴァンカップ");
		list.add("日本: 天皇杯");
		list.add("中国: 中国スーパーリーグ");
		CONTAINS_LIST = Collections.unmodifiableList(list);
	}

	// コンストラクタ生成禁止
	private ContainsCountryLeagueUtil() {

	}

	/**
	 * 国とリーグが含まれているか
	 * @param country
	 * @param league
	 * @return
	 */
	public static boolean containsCountryLeague(String country, String league) {
		for (String list : CONTAINS_LIST) {
			if (list.contains(country) && list.contains(league)) {
				return true;
			}
		}
		return false;
	}

}
