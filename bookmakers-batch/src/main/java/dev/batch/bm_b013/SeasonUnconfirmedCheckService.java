package dev.batch.bm_b013;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.mail.SmtpMailSender;
import dev.common.util.DateOffsetDecisionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 新規シーズンデータの未確認（シーズンデータ取得情報のモーダルで OK・削除していない行）の期限チェック（B013 の最初。2026-11-24 追加）
 *
 * <ol>
 *   <li>終了日を過ぎた（終了日 &lt; 今日・日本時間）、または開始日を過ぎた（開始日 &lt; 今日。次のシーズンの日程を確認しないまま
 *       開幕を迎えた）未確認の国リーグ → 確認済み（initial_flg = '1'）にする。
 *       日付は B003 が登録した値のまま使う（OK を何も直さずに押したのと同じ）。</li>
 *   <li>開始日か終了日が今日から {@value #NOTICE_DAYS} 日以内の未確認の国リーグ → 管理者にメールで知らせる
 *       （モーダル・「未対応」の表示はそのまま）。</li>
 * </ol>
 * どちらも 1 通のメールにまとめる。メール送信・確認済みへの更新に失敗しても B013 本体は止めない（ログのみ）。
 * 未確認のままでも B013・次シーズン判定は DB の値で動くので、ここで日付は変えない。
 */
@Slf4j
@Service
public class SeasonUnconfirmedCheckService {

	/** 期限が近いとみなす日数（今日を含めて今日〜今日+3日） */
	static final int NOTICE_DAYS = 3;

	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	@Autowired
	private SmtpMailSender smtpMailSender;

	/** 通知先（未設定なら送信元 system アカウント宛。B017 と同じ） */
	@Value("${app.notification.admin-email:${mail.accounts.system.username:}}")
	private String adminEmail;

	public void execute() {
		try {
			LocalDate today = LocalDate.now(DateOffsetDecisionUtil.getZoneId());
			LocalDate limit = today.plusDays(NOTICE_DAYS);

			List<CountryLeagueSeasonMasterEntity> rows = initialMasterCsvRepository.findUnconfirmedSeasons();
			List<CountryLeagueSeasonMasterEntity> autoConfirmed = new ArrayList<>();
			List<CountryLeagueSeasonMasterEntity> dueSoon = new ArrayList<>();

			for (CountryLeagueSeasonMasterEntity r : rows) {
				LocalDate start = parse(r.getStartSeasonDate());
				LocalDate end = parse(r.getEndSeasonDate());
				// 2026-11-24 開始日を過ぎた（新しいシーズンが始まった）場合も、終了日と同じく確認済みにする
				if ((end != null && end.isBefore(today)) || (start != null && start.isBefore(today))) {
					int n = initialMasterCsvRepository.updateInitialFlg(
							MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER, r.getCountry(), r.getLeague(), "1");
					log.info("[B013][未確認チェック] 開始日・終了日を過ぎたため確認済みにしました country={}, league={}, season={}, start={}, end={}, 件数={}",
							r.getCountry(), r.getLeague(), r.getSeasonYear(), r.getStartSeasonDate(), r.getEndSeasonDate(), n);
					autoConfirmed.add(r);
					continue;
				}
				if (within(start, today, limit) || within(end, today, limit)) {
					dueSoon.add(r);
				}
			}

			log.info("[B013][未確認チェック] 未確認={}, 期限が近い={}, 自動で確認済み={}",
					rows.size(), dueSoon.size(), autoConfirmed.size());
			if (dueSoon.isEmpty() && autoConfirmed.isEmpty()) {
				return;
			}

			String subject = "【bm-stats-real】新規シーズンデータの未確認 "
					+ (dueSoon.size() + autoConfirmed.size()) + "件（" + today + "）";
			String to = (adminEmail == null || adminEmail.isBlank()) ? smtpMailSender.fromAddress() : adminEmail;
			smtpMailSender.send(to, subject, buildBody(dueSoon, autoConfirmed));
			log.info("[B013][未確認チェック] メールを送りました to={}", to);
		} catch (Exception e) {
			log.warn("[B013][未確認チェック] 失敗しました（B013 は続けます） e={}", e.toString(), e);
		}
	}

	private String buildBody(List<CountryLeagueSeasonMasterEntity> dueSoon,
			List<CountryLeagueSeasonMasterEntity> autoConfirmed) {
		StringBuilder sb = new StringBuilder();
		sb.append("管理者 様\n\n");
		if (!dueSoon.isEmpty()) {
			sb.append("■ 開始日・終了日が ").append(NOTICE_DAYS).append(" 日以内の未確認のシーズンデータ\n");
			sb.append("管理画面「シーズンデータ取得情報」（/admin/season）のモーダルで日付を確認し、OK を押してください。\n");
			sb.append("（日付が違う場合はセルを直してから OK。直した日付は翌日以降も B003 で上書きされません）\n\n");
			for (CountryLeagueSeasonMasterEntity r : dueSoon) {
				sb.append(line(r));
			}
			sb.append("\n");
		}
		if (!autoConfirmed.isEmpty()) {
			sb.append("■ 開始日または終了日を過ぎたため、自動で確認済みにしたシーズンデータ\n");
			sb.append("登録されていた日付のまま使っています。違う場合はシーズンデータ取得情報の一覧の「日付」で直してください。\n");
			sb.append("（直した日付は、まだ統計に取り込んでいない統計CSVのシーズンにも、翌朝の B011 で反映されます）\n\n");
			for (CountryLeagueSeasonMasterEntity r : autoConfirmed) {
				sb.append(line(r));
			}
			sb.append("\n");
		}
		sb.append("--\nbm-stats-real（B013 自動通知）\n");
		return sb.toString();
	}

	private static String line(CountryLeagueSeasonMasterEntity r) {
		return "  ・" + r.getCountry() + " / " + r.getLeague() + "（" + nvl(r.getSeasonYear()) + "）"
				+ " 開始 " + nvl(r.getStartSeasonDate()) + " 〜 終了 " + nvl(r.getEndSeasonDate()) + "\n";
	}

	private static boolean within(LocalDate d, LocalDate from, LocalDate to) {
		return d != null && !d.isBefore(from) && !d.isAfter(to);
	}

	private static LocalDate parse(String s) {
		if (s == null || s.length() < 10) {
			return null;
		}
		try {
			return LocalDate.parse(s.substring(0, 10).replace('/', '-'));
		} catch (Exception e) {
			return null;
		}
	}

	private static String nvl(String s) {
		return s == null || s.isBlank() ? "未設定" : s;
	}
}
