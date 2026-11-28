-- static_data の「国・リーグ ＋ 記録時間」の式インデックス（2026-11-28 追加）
-- 統計がまだ無いリーグで、終了済の試合から作るデータ（StaticOverviewSource）・試合予定の詳細・アイコン・ランキングが遅い対策。
-- SQL の WHERE と同じ式にすること（式が違うとインデックスが使われない）:
--   normalize(BTRIM(split_part(d.data_category, ':', 1)), NFKC)                          … 国
--   normalize(BTRIM(split_part(split_part(d.data_category, ':', 2), ' - ', 1)), NFKC)    … リーグ
-- CONCURRENTLY: 書き込みを止めずに作る（トランザクションの中では実行できないので BEGIN を付けない）。
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_static_data_country_league_time
  ON static_data (
    (normalize(BTRIM(split_part(data_category, ':', 1)), NFKC)),
    (normalize(BTRIM(split_part(split_part(data_category, ':', 2), ' - ', 1)), NFKC)),
    record_time
  );

ANALYZE static_data;
