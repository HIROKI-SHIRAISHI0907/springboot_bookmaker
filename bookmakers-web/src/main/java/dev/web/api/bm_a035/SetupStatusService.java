package dev.web.api.bm_a035;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.SystemViewMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理画面の未対応状況
 * <ul>
 *   <li>SetupCheck を実装した Bean をすべて実行し、system_view_master と check_code で結びつける。</li>
 *   <li>block_flg = '1' のチェックに未対応があれば blocked = true（画面側で、setup_flg = '1' 以外の画面を開けなくする）。</li>
 *   <li>system_view_master に行が無いチェックは警告だけ（ブロックしない）。</li>
 *   <li>チェックの実行に失敗した場合は、そのチェックだけ error を付けてブロックしない（管理画面が全部開けなくなるのを防ぐ）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupStatusService {

	/** 1つのチェックで返す内容の上限 */
	static final int MAX_DETAILS = 50;

	private final List<SetupCheck> checks;

	private final SystemViewMasterRepository systemViewMasterRepository;

	@Transactional(readOnly = true)
	public SetupStatusResponse getStatus() {
		SetupStatusResponse res = new SetupStatusResponse();
		List<SystemViewMasterRepository.Row> views = systemViewMasterRepository.findAll();

		for (SystemViewMasterRepository.Row v : views) {
			if (v.isSetup()) {
				SetupStatusResponse.View sv = new SetupStatusResponse.View();
				sv.setViewCode(v.getViewCode());
				sv.setViewName(v.getViewName());
				sv.setPath(v.getPath());
				res.getSetupViews().add(sv);
			}
		}

		for (SetupCheck c : checks) {
			SetupStatusResponse.Item item = new SetupStatusResponse.Item();
			item.setCheckCode(c.code());
			item.setCheckName(c.name());

			SystemViewMasterRepository.Row view = views.stream()
					.filter(v -> c.code().equals(v.getCheckCode()))
					.findFirst().orElse(null);
			if (view != null) {
				item.setViewCode(view.getViewCode());
				item.setViewName(view.getViewName());
				item.setPath(view.getPath());
				item.setBlock(view.isBlock());
			}

			try {
				SetupCheckResult r = c.check();
				item.setCount(r.getCount());
				List<String> d = r.getDetails();
				item.setDetails(new ArrayList<>(d.size() > MAX_DETAILS ? d.subList(0, MAX_DETAILS) : d));
			} catch (Exception e) {
				log.error("[A035] 未対応チェックに失敗しました: {}", c.code(), e);
				item.setError(e.getMessage());
				item.setBlock(false);
			}

			if (item.isBlock() && item.getCount() > 0) {
				res.setBlocked(true);
			}
			res.getItems().add(item);
		}

		// system_view_master の表示順に並べる（マスタに無いチェックは最後）
		res.getItems().sort((a, b) -> Integer.compare(order(views, a.getCheckCode()), order(views, b.getCheckCode())));
		return res;
	}

	private static int order(List<SystemViewMasterRepository.Row> views, String checkCode) {
		for (SystemViewMasterRepository.Row v : views) {
			if (checkCode.equals(v.getCheckCode())) {
				return v.getDispOrder();
			}
		}
		return Integer.MAX_VALUE;
	}
}
