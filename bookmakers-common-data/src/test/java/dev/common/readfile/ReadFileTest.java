package dev.common.readfile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.readfile.dto.ReadFileOutputDTO;

class ReadFileTest {

  @Test
  void readCsv_noSpring() throws Exception {
    ReadOrigin readOrigin = new ReadOrigin(); // manageLoggerComponentはnullだが、このCSVで例外が出なければ通る

    String path = "dev/common/readfile/data/seq=000001_20260228T003123Z.csv";
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      assertNotNull(is, "テストCSVがclasspathにありません: " + path);

      ReadFileOutputDTO dto = readOrigin.getFileBodyFromStream(is, "fin/seq=000001_20260228T003123Z.csv");
      assertEquals(BookMakersCommonConst.NORMAL_CD, dto.getResultCd());
      assertEquals(1, dto.getDataList().size());

      DataEntity e = dto.getDataList().get(0);

      // 例：主要項目だけ assert（全項目比較版はこの後追加可能）
      assertEquals("11", e.getHomeRank());
      assertEquals("エクアドル: リーガ・プロ - ラウンド 2", e.getDataCategory());
      assertEquals("1:17", e.getTimes());
      assertEquals("ﾃｸﾆｺ･ｳﾆﾍﾞﾙｼﾀﾘｵ", e.getHomeTeamName());
      assertEquals("オレンセ", e.getAwayTeamName());

      assertEquals("https://www.flashscore.co.jp/match/soccer/orense-4MwdGZV3/tecnico-u-Em6vQ0O5/?mid=40MTYan1", e.getGameLink());
      // ★末尾：試合ID=matchId, 通番=seq, ソート用秒=timeSortSeconds の前提
      assertEquals("40MTYan1", e.getMatchId());
      assertEquals("1", e.getSeq());
      assertEquals(0, e.getTimeSortSeconds());
    }
  }
}
