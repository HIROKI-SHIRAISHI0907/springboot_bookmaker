package dev.web.api.teamMember.bm_w001;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.teamMemberDTO.TeamMemberDTO;
import dev.web.api.dashboard.teamMemberDTO.TeamMemberRow;
import dev.web.repository.master.TeamMemberMasterRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームメンバー（team_member_master）を画面用に整える。
 * <ul>
 *   <li>ポジションを GK / DF / MF / FW / OTHER にまとめる</li>
 *   <li>並び順: GK → DF → MF → FW → その他、背番号順（無い人は後ろ）、名前順</li>
 *   <li>負傷・レンタル元は「N/A」「-」などを空として扱う</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class TeamMembersService {

	/** 空として扱う値 */
	private static final Set<String> EMPTY = Set.of("N/A", "NA", "-", "--", "0", "なし", "null");

	private static final Map<String, Integer> GROUP_ORDER = Map.of("GK", 0, "DF", 1, "MF", 2, "FW", 3, "OTHER", 4);

	private final TeamMemberMasterRepository repository;

	public TeamMembersResponse getMembers(String country, String league, String team) {
		TeamMembersResponse res = new TeamMembersResponse();
		res.setCountry(country);
		res.setLeague(league);
		res.setTeam(team);

		List<TeamMemberDTO> list = new ArrayList<>();
		String latest = null;
		for (TeamMemberRow r : this.repository.findMembers(country, league, team)) {
			TeamMemberDTO d = new TeamMemberDTO();
			d.setName(r.getMember());
			d.setJersey(toInt(r.getJersey()));
			d.setPosition(blankToNull(r.getPosition()));
			d.setPositionGroup(group(r.getPosition()));
			d.setAge(toInt(r.getAge()));
			d.setHeight(toInt(r.getHeight()));
			d.setMarketValue(blankToNull(r.getMarketValue()));
			d.setInjury(blankToNull(r.getInjury()));
			String loan = blankToNull(r.getLoanBelong());
			d.setLoanFrom(loan != null && !loan.equals(team) ? loan : null);
			d.setFacePicPath(blankToNull(r.getFacePicPath()));
			list.add(d);

			String date = r.getLatestInfoDate();
			if (date != null && (latest == null || date.compareTo(latest) > 0)) {
				latest = date;
			}
		}
		list.sort(Comparator
				.comparing((TeamMemberDTO d) -> GROUP_ORDER.getOrDefault(d.getPositionGroup(), 9))
				.thenComparing(TeamMemberDTO::getJersey, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TeamMemberDTO::getName));

		res.setMembers(list);
		res.setInjuredCount((int) list.stream().filter(d -> d.getInjury() != null).count());
		res.setLatestInfoDate(latest != null && latest.length() >= 10 ? latest.substring(0, 10) : latest);
		return res;
	}

	/** ポジション名 → GK / DF / MF / FW / OTHER */
	static String group(String position) {
		if (position == null) {
			return "OTHER";
		}
		String p = position.trim();
		if (p.contains("キーパー") || p.equalsIgnoreCase("GK") || p.contains("Goalkeeper")) {
			return "GK";
		}
		if (p.contains("ディフェンダー") || p.equalsIgnoreCase("DF") || p.contains("Defender")) {
			return "DF";
		}
		if (p.contains("ミッドフィルダー") || p.equalsIgnoreCase("MF") || p.contains("Midfielder")) {
			return "MF";
		}
		if (p.contains("フォワード") || p.equalsIgnoreCase("FW") || p.contains("Forward")) {
			return "FW";
		}
		return "OTHER";
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() || EMPTY.contains(t) ? null : t;
	}

	/** "18" / "18.0" / "185cm" → 数値（読めなければ null） */
	private static Integer toInt(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.trim().replaceAll("^(\\d+).*$", "$1");
		if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
			return null;
		}
		try {
			int v = Integer.parseInt(digits);
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
