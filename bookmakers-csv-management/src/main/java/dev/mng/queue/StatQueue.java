package dev.mng.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.common.entity.BookDataEntity;

/**
 * CSV作成時に再計算が必要なキュー
 * @author shiraishitoshio
 *
 */
@Component
public class StatQueue {

	/** キュー */
	private Map<String, Map<String, List<BookDataEntity>>> entities = new HashMap<>();

	/**
	 * キュー設定
	 * @param countryLeague
	 * @param team
	 * @param entity
	 */
	public void setQueue(String countryLeague, String team, List<BookDataEntity> entity) {
		if (countryLeague == null || team == null) return;
	    // 外側: リーグごとの Map を用意
	    Map<String, List<BookDataEntity>> byTeam =
	            entities.computeIfAbsent(countryLeague, k -> new HashMap<>());
	    // 内側: チームごとの List を用意
	    List<BookDataEntity> list =
	            byTeam.computeIfAbsent(team, k -> new ArrayList<>());

	    if (entity != null && !entity.isEmpty()) {
	        list.addAll(entity); // 既存キューに追記
	    }
	}

	/**
	 * エンティティを取得
	 * @return
	 */
	public Map<String, Map<String, List<BookDataEntity>>> getEntities() {
		return entities;
	}

}
