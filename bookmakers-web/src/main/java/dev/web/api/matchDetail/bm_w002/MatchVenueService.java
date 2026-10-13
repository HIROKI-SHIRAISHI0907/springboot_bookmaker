package dev.web.api.matchDetail.bm_w002;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.web.api.matchDetail.matchVenueDTO.MatchVenueDTO;
import dev.web.repository.master.MatchVenueRepository;
import lombok.RequiredArgsConstructor;

/**
 * 試合詳細: スタジアムの地理情報
 * <ol>
 *   <li>static_data の studium（例: 「アル＝セッカ・アル＝ハディド・スタジアム (カイロ)」）から末尾の「(都市)」を外し、
 *       team_location_master.stadium_name と照合する（全角半角・空白・中黒・＝・ハイフンを無視。完全一致 → 部分一致）。</li>
 *   <li>見つからなければホームチームの team_location_master の行（ホームスタジアム）。
 *       中立地開催の可能性があるため matchedBy = HOME_TEAM として返す。</li>
 * </ol>
 * 有効期間（valid_from〜valid_to）内の行だけ。座標がある行を優先。
 */
@Service
@RequiredArgsConstructor
public class MatchVenueService {

	/** 末尾の「 (カイロ)」「（高知）」 */
	private static final Pattern TRAILING_CITY = Pattern.compile("^(.*?)\\s*[（(]([^（）()]+)[）)]\\s*$");

	private final MatchVenueRepository repository;

	public MatchVenueDTO find(String stadium, String location, String homeTeam, String country) {
		String name = trim(stadium);
		String city = trim(location);
		if (name != null) {
			Matcher m = TRAILING_CITY.matcher(name);
			if (m.matches()) {
				name = trim(m.group(1));
				if (city == null) {
					city = trim(m.group(2));
				}
			}
		}

		MatchVenueDTO dto = null;
		if (name != null) {
			dto = repository.findByStadium(name, country);
			if (dto != null) {
				dto.setMatchedBy("STADIUM");
			}
		}
		if (dto == null && trim(homeTeam) != null) {
			dto = repository.findByTeam(homeTeam.trim(), country);
			if (dto != null) {
				dto.setMatchedBy("HOME_TEAM");
			}
		}
		if (dto == null) {
			return null;
		}
		if (dto.getHomeCity() == null && city != null) {
			dto.setHomeCity(city);
		}
		dto.setMapUrl(mapUrl(dto));
		return dto;
	}

	/**
	 * チームのホームスタジアム（チームページ用）。team_location_master の team_name（NFKC）で探す。無ければ null。
	 */
	public MatchVenueDTO findHomeVenue(String team, String country) {
		if (trim(team) == null) {
			return null;
		}
		MatchVenueDTO dto = repository.findByTeam(team.trim(), country);
		if (dto == null) {
			return null;
		}
		dto.setMatchedBy("HOME_TEAM");
		dto.setMapUrl(mapUrl(dto));
		return dto;
	}

	/** Google マップのリンク（place_id があれば付ける） */
	private static String mapUrl(MatchVenueDTO d) {
		if (d.getLatitude() == null || d.getLongitude() == null) {
			return null;
		}
		String url = "https://www.google.com/maps/search/?api=1&query="
				+ d.getLatitude().toPlainString() + "," + d.getLongitude().toPlainString();
		if (d.getPlaceId() != null) {
			url += "&query_place_id=" + URLEncoder.encode(d.getPlaceId(), StandardCharsets.UTF_8);
		}
		return url;
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
