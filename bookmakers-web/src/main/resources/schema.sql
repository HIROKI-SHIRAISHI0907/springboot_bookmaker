-- ============================================================
-- schema.sql : soccer_bm（ローカル）用 DDL 一式
--  実行: psql -h localhost -U postgres -d soccer_bm -v ON_ERROR_STOP=1 -f schema.sql
--  内容: static_data（基底テーブル）→ ddl_split 00〜36 を番号順にそのまま連結
--  ※ 各ブロックは DROP → CREATE なので、何度流しても同じ状態になる（集計テーブルのデータは消える）。
--  ※ soccer_master（future_master など）・soccer_user（users, favorites など）は含まない。
-- ============================================================
SET client_encoding = 'UTF8';
SET TIME ZONE 'Asia/Tokyo';

-- ============================================================
-- 基底テーブル static_data（リアルタイムデータ。スクレイピング結果がそのまま入る）
--  ※ 列の並びは本番の \d static_data と同じ。型はローカル用に TEXT 中心にしている。
--    本番と型を完全に合わせたい場合は、本番から
--      pg_dump -s -t static_data soccer_bm > static_data.sql
--    を取り、このブロックと差し替えてください。
-- ============================================================
BEGIN;

CREATE TABLE IF NOT EXISTS static_data (
  seq_key                                    VARCHAR(100) NOT NULL,
  condition_result_data_seq_id               TEXT,
  data_category                              TEXT,
  times                                      TEXT,
  home_rank                                  TEXT,
  home_team_name                             TEXT,
  home_score                                 TEXT,
  away_rank                                  TEXT,
  away_team_name                             TEXT,
  away_score                                 TEXT,
  home_exp                                   TEXT,
  away_exp                                   TEXT,
  home_in_goal_exp                           TEXT,
  away_in_goal_exp                           TEXT,
  home_donation                              TEXT,
  away_donation                              TEXT,
  home_shoot_all                             TEXT,
  away_shoot_all                             TEXT,
  home_shoot_in                              TEXT,
  away_shoot_in                              TEXT,
  home_shoot_out                             TEXT,
  away_shoot_out                             TEXT,
  home_block_shoot                           TEXT,
  away_block_shoot                           TEXT,
  home_big_chance                            TEXT,
  away_big_chance                            TEXT,
  home_corner                                TEXT,
  away_corner                                TEXT,
  home_box_shoot_in                          TEXT,
  away_box_shoot_in                          TEXT,
  home_box_shoot_out                         TEXT,
  away_box_shoot_out                         TEXT,
  home_goal_post                             TEXT,
  away_goal_post                             TEXT,
  home_goal_head                             TEXT,
  away_goal_head                             TEXT,
  home_keeper_save                           TEXT,
  away_keeper_save                           TEXT,
  home_free_kick                             TEXT,
  away_free_kick                             TEXT,
  home_offside                               TEXT,
  away_offside                               TEXT,
  home_foul                                  TEXT,
  away_foul                                  TEXT,
  home_yellow_card                           TEXT,
  away_yellow_card                           TEXT,
  home_red_card                              TEXT,
  away_red_card                              TEXT,
  home_slow_in                               TEXT,
  away_slow_in                               TEXT,
  home_box_touch                             TEXT,
  away_box_touch                             TEXT,
  home_pass_count                            TEXT,
  away_pass_count                            TEXT,
  home_long_pass_count                       TEXT,
  away_long_pass_count                       TEXT,
  home_final_third_pass_count                TEXT,
  away_final_third_pass_count                TEXT,
  home_cross_count                           TEXT,
  away_cross_count                           TEXT,
  home_tackle_count                          TEXT,
  away_tackle_count                          TEXT,
  home_clear_count                           TEXT,
  away_clear_count                           TEXT,
  home_duel_count                            TEXT,
  away_duel_count                            TEXT,
  home_intercept_count                       TEXT,
  away_intercept_count                       TEXT,
  record_time                                TIMESTAMP(0),
  weather                                    TEXT,
  temparature                                TEXT,
  humid                                      TEXT,
  judge_member                               TEXT,
  home_manager                               TEXT,
  away_manager                               TEXT,
  home_formation                             TEXT,
  away_formation                             TEXT,
  studium                                    TEXT,
  capacity                                   TEXT,
  audience                                   TEXT,
  location                                   TEXT,
  home_max_getting_scorer                    TEXT,
  away_max_getting_scorer                    TEXT,
  home_max_getting_scorer_game_situation     TEXT,
  away_max_getting_scorer_game_situation     TEXT,
  home_team_home_score                       TEXT,
  home_team_home_lost                        TEXT,
  away_team_home_score                       TEXT,
  away_team_home_lost                        TEXT,
  home_team_away_score                       TEXT,
  home_team_away_lost                        TEXT,
  away_team_away_score                       TEXT,
  away_team_away_lost                        TEXT,
  notice_flg                                 TEXT,
  game_link                                  TEXT,
  goal_time                                  TEXT,
  goal_team_member                           TEXT,
  judge                                      TEXT,
  home_team_style                            TEXT,
  away_team_style                            TEXT,
  probablity                                 TEXT,
  prediction_score_time                      TEXT,
  game_id                                    TEXT,
  match_id                                   TEXT,
  time_sort_seconds                          TEXT,
  add_manual_flg                             TEXT,
  logic_flg                                  TEXT,
  register_id                                VARCHAR(100) NOT NULL DEFAULT 'local',
  register_time                              TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_id                                  VARCHAR(100) NOT NULL DEFAULT 'local',
  update_time                                TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_static_data PRIMARY KEY (seq_key)
);

CREATE INDEX IF NOT EXISTS idx_static_data_match_id    ON static_data (match_id);
CREATE INDEX IF NOT EXISTS idx_static_data_record_time ON static_data (record_time);

COMMIT;

-- ############################################################
-- 00_seq_counter.sql
-- ############################################################
-- 00: 採番カウンタ（最初に1回だけ。既存データは消さない）
BEGIN;

-- 採番カウンタ（テーブル × シーズンごとの最終番号）
CREATE TABLE IF NOT EXISTS seq_counter (
  table_name    VARCHAR(100)                NOT NULL,
  season        VARCHAR(20)                 NOT NULL,
  last_no       BIGINT                      NOT NULL,
  register_id   VARCHAR(100)                NOT NULL,
  register_time TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id     VARCHAR(100)                NOT NULL,
  update_time   TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  PRIMARY KEY (table_name, season)
);

COMMIT;

-- ############################################################
-- 01_bm_m004.sql
-- ############################################################
-- 01: BM_M004
BEGIN;

-- BM_M004 team_time_segment_stats 作り直し（既存データは削除される）
-- PostgreSQL は DDL もトランザクション内で実行できるため、途中で失敗したら全部元に戻る。

-- 旧テーブル（横持ち・TeamTimeSegmentShooting 時代）が残っていれば、実際のテーブル名に直してコメントを外す
-- DROP TABLE IF EXISTS <旧テーブル名>;

DROP TABLE IF EXISTS team_time_segment_stats;

-- テーブルを作り直すので採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'team_time_segment_stats';

-- BM_M004 時間帯別 対戦成績（縦持ち）
-- 1行 = 1シーズン × 対象チーム × 相手チーム × ホーム/アウェー × 時間帯
-- 1試合で 2チーム × 11時間帯 = 22行
CREATE TABLE team_time_segment_stats (
  seq                         VARCHAR(40)  NOT NULL,  -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season                      VARCHAR(20)  NOT NULL,  -- country_league_season_master.season_year
  data_category               TEXT         NOT NULL,  -- 元のキー「国: リーグ - ラウンドN」（ラウンドを含むので、同じ対戦が複数回あるリーグでも試合ごとに別の行になる）
  team_name                   TEXT         NOT NULL,  -- 対象チーム
  opponent_team_name          TEXT         NOT NULL,  -- 相手チーム
  ha                          CHAR(1)      NOT NULL,  -- 対象チームが H:ホーム / A:アウェー
  match_id                    TEXT,                   -- 参照用（BookDataEntity.matchId）
  time_segment                VARCHAR(5)   NOT NULL,  -- 0-10, 11-20, …, 81-90, AT
  segment_order               SMALLINT     NOT NULL,  -- 並び順 0〜10
  snapshot_count              INTEGER      NOT NULL,  -- 時間帯内のスナップショット行数（0 なら値はすべて NULL）

  -- 得点
  goal_for                    INTEGER,                -- 時間帯内の得点
  goal_against                INTEGER,                -- 時間帯内の失点

  -- 時間帯内に増えた量（小数）
  exp                         NUMERIC(6,2),           -- 期待値（xG）
  in_goal_exp                 NUMERIC(6,2),           -- 枠内ゴール期待値

  -- 時間帯内のポゼッション（%）
  possession                  NUMERIC(5,1),

  -- 時間帯内に増えた数
  shoot_all                   INTEGER,
  shoot_in                    INTEGER,                -- 枠内シュート
  shoot_out                   INTEGER,
  shoot_blocked               INTEGER,
  big_chance                  INTEGER,
  corner_kick                 INTEGER,
  box_shoot_in                INTEGER,                -- ボックス内シュート
  box_shoot_out               INTEGER,
  goal_post                   INTEGER,
  goal_head                   INTEGER,
  keeper_save                 INTEGER,
  free_kick                   INTEGER,
  offside                     INTEGER,
  foul                        INTEGER,
  yellow_card                 INTEGER,
  red_card                    INTEGER,
  slow_in                     INTEGER,
  box_touch                   INTEGER,
  clear_count                 INTEGER,
  duel_count                  INTEGER,
  intercept_count             INTEGER,

  -- 成功数 / 試行数 / 成功率(%)（時間帯内）
  pass_success                INTEGER,
  pass_try                    INTEGER,
  pass_rate                   NUMERIC(5,1),
  long_pass_success           INTEGER,
  long_pass_try               INTEGER,
  long_pass_rate              NUMERIC(5,1),
  final_third_pass_success    INTEGER,
  final_third_pass_try        INTEGER,
  final_third_pass_rate       NUMERIC(5,1),
  cross_success               INTEGER,
  cross_try                   INTEGER,
  cross_rate                  NUMERIC(5,1),
  tackle_success              INTEGER,
  tackle_try                  INTEGER,
  tackle_rate                 NUMERIC(5,1),

  register_id                 VARCHAR(100)                NOT NULL,
  register_time               TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                   VARCHAR(100)                NOT NULL,
  update_time                 TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_team_time_segment_stats PRIMARY KEY (seq),
  CONSTRAINT uq_team_time_segment_stats
    UNIQUE (season, data_category, team_name, opponent_team_name, ha, time_segment)
);

-- 対象チームの成績を引く用
CREATE INDEX idx_team_time_segment_stats_team
  ON team_time_segment_stats (data_category, team_name, season);

COMMIT;

-- ############################################################
-- 02_bm_m005.sql
-- ############################################################
-- 02: BM_M005
BEGIN;

-- BM_M005 no_goal_match_stats 作り直し（既存データは削除される）
-- テーブル名が実際と違う場合は、DROP の行と下の CREATE 以降を実際の名前に合わせること（\dt *goal* で確認）

DROP TABLE IF EXISTS no_goal_match_stats;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'no_goal_match_stats';

-- BM_M005 無得点試合（試合終了時 0-0）のスナップショット
-- 1行 = 1試合 × 時点（START: 試合開始時 / HT: ハーフタイム / END: 試合終了時）。1試合で最大3行
CREATE TABLE no_goal_match_stats (
  seq                                      VARCHAR(40)  NOT NULL,  -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season                                   VARCHAR(20)  NOT NULL,  -- country_league_season_master.season_year
  snapshot_type                            VARCHAR(5)   NOT NULL,  -- スナップショット種別（START: 試合開始時 / HT: ハーフタイム / END: 試合終了時）
  data_seq                                 TEXT,  -- 元データ（BookDataEntity）の通番
  match_id                                 TEXT,  -- マッチID
  data_category                            TEXT         NOT NULL,  -- 対戦チームカテゴリ
  times                                    TEXT,  -- 試合時間
  home_rank                                TEXT,  -- ホーム順位
  home_team_name                           TEXT         NOT NULL,  -- ホームチーム
  home_score                               TEXT,  -- ホームスコア
  away_rank                                TEXT,  -- アウェー順位
  away_team_name                           TEXT         NOT NULL,  -- アウェーチーム
  away_score                               TEXT,  -- アウェースコア
  home_exp                                 TEXT,  -- ホーム期待値
  away_exp                                 TEXT,  -- アウェー期待値
  home_in_goal_exp                         TEXT,  -- ホーム枠内ゴール期待値
  away_in_goal_exp                         TEXT,  -- アウェー枠内ゴール期待値
  home_donation                            TEXT,  -- ホームポゼッション
  away_donation                            TEXT,  -- アウェーポゼッション
  home_shoot_all                           TEXT,  -- ホームシュート数
  away_shoot_all                           TEXT,  -- アウェーシュート数
  home_shoot_in                            TEXT,  -- ホーム枠内シュート
  away_shoot_in                            TEXT,  -- アウェー枠内シュート
  home_shoot_out                           TEXT,  -- ホーム枠外シュート
  away_shoot_out                           TEXT,  -- アウェー枠外シュート
  home_block_shoot                         TEXT,  -- ホームブロックシュート
  away_block_shoot                         TEXT,  -- アウェーブロックシュート
  home_big_chance                          TEXT,  -- ホームビッグチャンス
  away_big_chance                          TEXT,  -- アウェービッグチャンス
  home_corner                              TEXT,  -- ホームコーナーキック
  away_corner                              TEXT,  -- アウェーコーナーキック
  home_box_shoot_in                        TEXT,  -- ホームボックス内シュート
  away_box_shoot_in                        TEXT,  -- アウェーボックス内シュート
  home_box_shoot_out                       TEXT,  -- ホームボックス外シュート
  away_box_shoot_out                       TEXT,  -- アウェーボックス外シュート
  home_goal_post                           TEXT,  -- ホームゴールポスト
  away_goal_post                           TEXT,  -- アウェーゴールポスト
  home_goal_head                           TEXT,  -- ホームヘディングゴール
  away_goal_head                           TEXT,  -- アウェーヘディングゴール
  home_keeper_save                         TEXT,  -- ホームキーパーセーブ
  away_keeper_save                         TEXT,  -- アウェーキーパーセーブ
  home_free_kick                           TEXT,  -- ホームフリーキック
  away_free_kick                           TEXT,  -- アウェーフリーキック
  home_offside                             TEXT,  -- ホームオフサイド
  away_offside                             TEXT,  -- アウェーオフサイド
  home_foul                                TEXT,  -- ホームファウル
  away_foul                                TEXT,  -- アウェーファウル
  home_yellow_card                         TEXT,  -- ホームイエローカード
  away_yellow_card                         TEXT,  -- アウェーイエローカード
  home_red_card                            TEXT,  -- ホームレッドカード
  away_red_card                            TEXT,  -- アウェーレッドカード
  home_slow_in                             TEXT,  -- ホームスローイン
  away_slow_in                             TEXT,  -- アウェースローイン
  home_box_touch                           TEXT,  -- ホームボックスタッチ
  away_box_touch                           TEXT,  -- アウェーボックスタッチ
  home_pass_count                          TEXT,  -- ホームパス数
  away_pass_count                          TEXT,  -- アウェーパス数
  home_long_pass_count                     TEXT,  -- ホームロングパス数
  away_long_pass_count                     TEXT,  -- アウェーロングパス数
  home_final_third_pass_count              TEXT,  -- ホームファイナルサードパス数
  away_final_third_pass_count              TEXT,  -- アウェーファイナルサードパス数
  home_cross_count                         TEXT,  -- ホームクロス数
  away_cross_count                         TEXT,  -- アウェークロス数
  home_tackle_count                        TEXT,  -- ホームタックル数
  away_tackle_count                        TEXT,  -- アウェータックル数
  home_clear_count                         TEXT,  -- ホームクリア数
  away_clear_count                         TEXT,  -- アウェークリア数
  home_duel_count                          TEXT,  -- ホームデュエル勝利数
  away_duel_count                          TEXT,  -- アウェーデュエル勝利数
  home_intercept_count                     TEXT,  -- ホームインターセプト数
  away_intercept_count                     TEXT,  -- アウェーインターセプト数
  record_time                              TIMESTAMP(0) WITH TIME ZONE,  -- 記録時間
  weather                                  TEXT,  -- 天気
  temperature                              TEXT,  -- 気温
  humid                                    TEXT,  -- 湿度
  judge_member                             TEXT,  -- 審判
  home_manager                             TEXT,  -- ホーム監督
  away_manager                             TEXT,  -- アウェー監督
  home_formation                           TEXT,  -- ホームフォーメーション
  away_formation                           TEXT,  -- アウェーフォーメーション
  studium                                  TEXT,  -- スタジアム
  capacity                                 TEXT,  -- 収容人数
  audience                                 TEXT,  -- 観客数
  home_max_getting_scorer                  TEXT,  -- ホームチーム最大得点者
  away_max_getting_scorer                  TEXT,  -- アウェーチーム最大得点者
  home_max_getting_scorer_game_situation   TEXT,  -- ホームチーム最大得点者出場状況
  away_max_getting_scorer_game_situation   TEXT,  -- アウェーチーム最大得点者出場状況
  home_team_home_score                     TEXT,  -- ホームチームホーム得点数
  home_team_home_lost                      TEXT,  -- ホームチームホーム失点数
  away_team_home_score                     TEXT,  -- アウェーチームホーム得点数
  away_team_home_lost                      TEXT,  -- アウェーチームホーム失点数
  home_team_away_score                     TEXT,  -- ホームチームアウェー得点数
  home_team_away_lost                      TEXT,  -- ホームチームアウェー失点数
  away_team_away_score                     TEXT,  -- アウェーチームアウェー得点数
  away_team_away_lost                      TEXT,  -- アウェーチームアウェー失点数
  notice_flg                               TEXT,  -- 通知フラグ
  goal_time                                TEXT,  -- ゴール時間
  goal_team_member                         TEXT,  -- ゴール選手名
  judge                                    TEXT,  -- 判定結果
  home_team_style                          TEXT,  -- ホームチームスタイル
  away_team_style                          TEXT,  -- アウェーチームスタイル
  probablity                               TEXT,  -- 確率
  prediction_score_time                    TEXT,  -- スコア予想時間
  register_id                              VARCHAR(100)                NOT NULL,
  register_time                            TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                                VARCHAR(100)                NOT NULL,
  update_time                              TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_no_goal_match_stats PRIMARY KEY (seq),
  CONSTRAINT uq_no_goal_match_stats
    UNIQUE (season, data_category, home_team_name, away_team_name, snapshot_type)
);

-- チームで引く用
CREATE INDEX idx_no_goal_match_stats_home ON no_goal_match_stats (data_category, home_team_name, season);
CREATE INDEX idx_no_goal_match_stats_away ON no_goal_match_stats (data_category, away_team_name, season);

COMMIT;

-- ############################################################
-- 03_bm_m006.sql
-- ############################################################
-- 03: BM_M006
BEGIN;

-- BM_M006 country_league_summary 作り直し（既存データは削除される）
-- 変更点: id → seq（<シーズン>-<枝番>、seq_counter 採番）、season 列を追加、data_count 列を削除、csv_count を INTEGER に

DROP TABLE IF EXISTS country_league_summary;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'country_league_summary';

-- 1行 = 国 × リーグ × シーズン
CREATE TABLE country_league_summary (
  seq            VARCHAR(40)                 NOT NULL,            -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season         VARCHAR(20)                 NOT NULL,            -- country_league_season_master.season_year
  country        TEXT                        NOT NULL,            -- 国
  league         TEXT                        NOT NULL,            -- リーグ
  csv_count      INTEGER                     NOT NULL DEFAULT 0,  -- 処理した回数の合計（処理ごとに、その回の試合数を加算）

  register_id    VARCHAR(100)                NOT NULL,
  register_time  TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id      VARCHAR(100)                NOT NULL,
  update_time    TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_country_league_summary PRIMARY KEY (seq),
  CONSTRAINT uq_country_league_summary UNIQUE (season, country, league),
  CONSTRAINT ck_country_league_summary_csv_count CHECK (csv_count >= 0)
);

COMMIT;

-- ############################################################
-- 04_bm_m017_m018.sql
-- ############################################################
-- 04: BM_M017 / M018
BEGIN;

-- BM_M017 / BM_M018 作り直し（既存データは削除される）
--  旧: league_score_time_band_stats（合計スコア×時間帯の件数）と
--      league_score_time_band_stats_split_score（ホーム/アウェー別）の2テーブルに件数を加算
--  新: ゴール1点 = 1行の league_score_goal_event に統合し、
--      M017 / M018 の件数・割合は同じ名前のビューで出す（試合を再処理しても二重にならない）

-- 旧テーブル・作成済みビューのどちらでも消せるように、種類を見て DROP する
-- （DROP VIEW IF EXISTS はテーブルがあるとエラー、DROP TABLE IF EXISTS はビューがあるとエラーになるため）
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('league_score_time_band_stats', 'league_score_time_band_stats_split_score')
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW %I', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DROP TABLE IF EXISTS league_score_goal_event CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'league_score_goal_event';

-- 1行 = 1試合の1ゴール（試合終了（FIN）した試合だけ）
CREATE TABLE league_score_goal_event (
  seq                 VARCHAR(40)                 NOT NULL,  -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season              VARCHAR(20)                 NOT NULL,  -- country_league_season_master.season_year
  country             TEXT                        NOT NULL,  -- 国
  league              TEXT                        NOT NULL,  -- リーグ
  round_no            SMALLINT                    NOT NULL,  -- ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別する）
  home_team_name      TEXT                        NOT NULL,  -- ホームチーム
  away_team_name      TEXT                        NOT NULL,  -- アウェーチーム
  match_id            TEXT,                                  -- 参照用（BookDataEntity.matchId）
  goal_no             SMALLINT                    NOT NULL,  -- その試合の何点目か（合計、1〜）
  scored_side         CHAR(1)                     NOT NULL,  -- 得点した側 H:ホーム / A:アウェー
  home_score_value    SMALLINT                    NOT NULL,  -- 得点後のホームスコア
  away_score_value    SMALLINT                    NOT NULL,  -- 得点後のアウェースコア
  time_range_area     VARCHAR(10)                 NOT NULL,  -- 時間帯（ExecuteMainUtil.classifyMatchTime: 0〜10, …, 90〜）
  time_band_order     SMALLINT                    NOT NULL,  -- 時間帯の並び順 0〜10
  goal_times          TEXT,                                  -- 得点を検出した行の試合時間（参照用）

  register_id         VARCHAR(100)                NOT NULL,
  register_time       TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id           VARCHAR(100)                NOT NULL,
  update_time         TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_league_score_goal_event PRIMARY KEY (seq),
  CONSTRAINT uq_league_score_goal_event
    UNIQUE (season, country, league, round_no, home_team_name, away_team_name, goal_no),
  CONSTRAINT ck_league_score_goal_event_side CHECK (scored_side IN ('H', 'A')),
  CONSTRAINT ck_league_score_goal_event_goal_no CHECK (goal_no >= 1
    AND goal_no = home_score_value + away_score_value)
);

-- 集計（ビュー）用
CREATE INDEX idx_league_score_goal_event_league
  ON league_score_goal_event (season, country, league);

-- BM_M017: 合計N点目のゴールがどの時間帯に入ったか
--   target = その時間帯に入った数 / search = 合計N点目のゴール総数 / ratio = target ÷ search（%）
CREATE VIEW league_score_time_band_stats AS
SELECT
  season,
  country,
  league,
  goal_no                                  AS sum_score_value,
  time_range_area,
  MIN(time_band_order)                     AS time_band_order,
  COUNT(*)::INTEGER                        AS target,
  (SUM(COUNT(*)) OVER w)::INTEGER          AS search,
  ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER w, 1) AS ratio
FROM league_score_goal_event
GROUP BY season, country, league, goal_no, time_range_area
WINDOW w AS (PARTITION BY season, country, league, goal_no);

-- BM_M018: どちらが・得点後何対何になるゴールを・どの時間帯に取ったか
--   target = その時間帯に入った数 / search = 同じ条件（得点側・得点後スコア）のゴール総数 / ratio = target ÷ search（%）
CREATE VIEW league_score_time_band_stats_split_score AS
SELECT
  season,
  country,
  league,
  scored_side,
  home_score_value,
  away_score_value,
  time_range_area,
  MIN(time_band_order)                     AS time_band_order,
  COUNT(*)::INTEGER                        AS target,
  (SUM(COUNT(*)) OVER w)::INTEGER          AS search,
  ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER w, 1) AS ratio
FROM league_score_goal_event
GROUP BY season, country, league, scored_side, home_score_value, away_score_value, time_range_area
WINDOW w AS (PARTITION BY season, country, league, scored_side, home_score_value, away_score_value);

COMMIT;

-- ############################################################
-- 05_bm_m019_table.sql
-- ############################################################
-- 05: BM_M019 テーブル classify_result_data（既存データは削除）
-- ※ このファイルを流し直すと、後の番号のビューが CASCADE で消えることがあります。その場合は後の番号のファイルも続けて流してください。
BEGIN;

-- BM_M019 / BM_M020 作り直し（既存データは削除される）
--  旧: classify_result_data（明細を毎回 INSERT）と classify_result_data_detail（件数を毎回 +1）
--  新: classify_result_data を試合単位で置き換える（再処理しても二重にならない）。
--      BM_M020 の件数は同じ名前のビュー classify_result_data_detail で出す（件数 0 の分類モードも出る）
--  ※ テーブル名が実際と違う場合は、実際の名前に合わせること（\dt classify* で確認）

-- テーブル・ビューのどちらでも消せるように、種類を見て DROP する
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('classify_result_data_detail', 'classify_result_data')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW %I', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'classify_result_data';

-- BM_M019: 1行 = 1試合の1時点（KICKOFF / GOAL / HT / FIN）
CREATE TABLE classify_result_data (
  seq                                      VARCHAR(40)  NOT NULL,         -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season                                   VARCHAR(20)  NOT NULL,         -- country_league_season_master.season_year
  country                                  TEXT         NOT NULL,         -- 国
  league                                   TEXT         NOT NULL,         -- リーグ
  round_no                                 SMALLINT     NOT NULL,         -- ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別する）
  classify_mode                            SMALLINT     NOT NULL,         -- 分類モード（ClassifyMode の番号。試合単位で同じ値）String → Integer
  snapshot_type                            VARCHAR(7)   NOT NULL,         -- 時点の種類（KICKOFF: 試合開始時 / GOAL: 得点した時点 / HT: ハーフタイム / FIN: 試合終了時）
  goal_no                                  SMALLINT     NOT NULL,         -- その時点までの合計得点数（KICKOFF は 0、GOAL は何点目か）
  data_seq                                 TEXT,                          -- 元データ（BookDataEntity）の通番
  match_id                                 TEXT,                          -- マッチID
  data_category                            TEXT,                          -- 対戦チームカテゴリ
  times                                    TEXT,                          -- 試合時間
  home_rank                                TEXT,                          -- ホーム順位
  home_team_name                           TEXT         NOT NULL,         -- ホームチーム
  home_score                               TEXT,                          -- ホームスコア
  away_rank                                TEXT,                          -- アウェー順位
  away_team_name                           TEXT         NOT NULL,         -- アウェーチーム
  away_score                               TEXT,                          -- アウェースコア
  home_exp                                 TEXT,                          -- ホーム期待値
  away_exp                                 TEXT,                          -- アウェー期待値
  home_in_goal_exp                         TEXT,                          -- ホーム枠内ゴール期待値
  away_in_goal_exp                         TEXT,                          -- アウェー枠内ゴール期待値
  home_donation                            TEXT,                          -- ホームポゼッション
  away_donation                            TEXT,                          -- アウェーポゼッション
  home_shoot_all                           TEXT,                          -- ホームシュート数
  away_shoot_all                           TEXT,                          -- アウェーシュート数
  home_shoot_in                            TEXT,                          -- ホーム枠内シュート
  away_shoot_in                            TEXT,                          -- アウェー枠内シュート
  home_shoot_out                           TEXT,                          -- ホーム枠外シュート
  away_shoot_out                           TEXT,                          -- アウェー枠外シュート
  home_block_shoot                         TEXT,                          -- ホームブロックシュート
  away_block_shoot                         TEXT,                          -- アウェーブロックシュート
  home_big_chance                          TEXT,                          -- ホームビッグチャンス
  away_big_chance                          TEXT,                          -- アウェービッグチャンス
  home_corner                              TEXT,                          -- ホームコーナーキック
  away_corner                              TEXT,                          -- アウェーコーナーキック
  home_box_shoot_in                        TEXT,                          -- ホームボックス内シュート
  away_box_shoot_in                        TEXT,                          -- アウェーボックス内シュート
  home_box_shoot_out                       TEXT,                          -- ホームボックス外シュート
  away_box_shoot_out                       TEXT,                          -- アウェーボックス外シュート
  home_goal_post                           TEXT,                          -- ホームゴールポスト
  away_goal_post                           TEXT,                          -- アウェーゴールポスト
  home_goal_head                           TEXT,                          -- ホームヘディングゴール
  away_goal_head                           TEXT,                          -- アウェーヘディングゴール
  home_keeper_save                         TEXT,                          -- ホームキーパーセーブ
  away_keeper_save                         TEXT,                          -- アウェーキーパーセーブ
  home_free_kick                           TEXT,                          -- ホームフリーキック
  away_free_kick                           TEXT,                          -- アウェーフリーキック
  home_offside                             TEXT,                          -- ホームオフサイド
  away_offside                             TEXT,                          -- アウェーオフサイド
  home_foul                                TEXT,                          -- ホームファウル
  away_foul                                TEXT,                          -- アウェーファウル
  home_yellow_card                         TEXT,                          -- ホームイエローカード
  away_yellow_card                         TEXT,                          -- アウェーイエローカード
  home_red_card                            TEXT,                          -- ホームレッドカード
  away_red_card                            TEXT,                          -- アウェーレッドカード
  home_slow_in                             TEXT,                          -- ホームスローイン
  away_slow_in                             TEXT,                          -- アウェースローイン
  home_box_touch                           TEXT,                          -- ホームボックスタッチ
  away_box_touch                           TEXT,                          -- アウェーボックスタッチ
  home_pass_count                          TEXT,                          -- ホームパス数
  away_pass_count                          TEXT,                          -- アウェーパス数
  home_long_pass_count                     TEXT,                          -- ホームロングパス数
  away_long_pass_count                     TEXT,                          -- アウェーロングパス数
  home_final_third_pass_count              TEXT,                          -- ホームファイナルサードパス数
  away_final_third_pass_count              TEXT,                          -- アウェーファイナルサードパス数
  home_cross_count                         TEXT,                          -- ホームクロス数
  away_cross_count                         TEXT,                          -- アウェークロス数
  home_tackle_count                        TEXT,                          -- ホームタックル数
  away_tackle_count                        TEXT,                          -- アウェータックル数
  home_clear_count                         TEXT,                          -- ホームクリア数
  away_clear_count                         TEXT,                          -- アウェークリア数
  home_duel_count                          TEXT,                          -- ホームデュエル勝利数
  away_duel_count                          TEXT,                          -- アウェーデュエル勝利数
  home_intercept_count                     TEXT,                          -- ホームインターセプト数
  away_intercept_count                     TEXT,                          -- アウェーインターセプト数
  record_time                              TIMESTAMP(0) WITH TIME ZONE,   -- 記録時間
  weather                                  TEXT,                          -- 天気
  temperature                              TEXT,                          -- 気温
  humid                                    TEXT,                          -- 湿度
  judge_member                             TEXT,                          -- 審判
  home_manager                             TEXT,                          -- ホーム監督
  away_manager                             TEXT,                          -- アウェー監督
  home_formation                           TEXT,                          -- ホームフォーメーション
  away_formation                           TEXT,                          -- アウェーフォーメーション
  studium                                  TEXT,                          -- スタジアム
  capacity                                 TEXT,                          -- 収容人数
  audience                                 TEXT,                          -- 観客数
  home_max_getting_scorer                  TEXT,                          -- ホームチーム最大得点者
  away_max_getting_scorer                  TEXT,                          -- アウェーチーム最大得点者
  home_max_getting_scorer_game_situation   TEXT,                          -- ホームチーム最大得点者出場状況
  away_max_getting_scorer_game_situation   TEXT,                          -- アウェーチーム最大得点者出場状況
  home_team_home_score                     TEXT,                          -- ホームチームホーム得点数
  home_team_home_lost                      TEXT,                          -- ホームチームホーム失点数
  away_team_home_score                     TEXT,                          -- アウェーチームホーム得点数
  away_team_home_lost                      TEXT,                          -- アウェーチームホーム失点数
  home_team_away_score                     TEXT,                          -- ホームチームアウェー得点数
  home_team_away_lost                      TEXT,                          -- ホームチームアウェー失点数
  away_team_away_score                     TEXT,                          -- アウェーチームアウェー得点数
  away_team_away_lost                      TEXT,                          -- アウェーチームアウェー失点数
  notice_flg                               TEXT,                          -- 通知フラグ
  goal_time                                TEXT,                          -- ゴール時間
  goal_team_member                         TEXT,                          -- ゴール選手名
  judge                                    TEXT,                          -- 判定結果
  home_team_style                          TEXT,                          -- ホームチームスタイル
  away_team_style                          TEXT,                          -- アウェーチームスタイル
  probablity                               TEXT,                          -- 確率
  prediction_score_time                    TEXT,                          -- スコア予想時間
  register_id                              VARCHAR(100)                NOT NULL,
  register_time                            TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                                VARCHAR(100)                NOT NULL,
  update_time                              TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_classify_result_data PRIMARY KEY (seq),
  CONSTRAINT uq_classify_result_data
    UNIQUE (season, country, league, round_no, home_team_name, away_team_name, snapshot_type, goal_no),
  CONSTRAINT ck_classify_result_data_snapshot CHECK (snapshot_type IN ('KICKOFF', 'GOAL', 'HT', 'FIN'))
);

CREATE INDEX idx_classify_result_data_mode
  ON classify_result_data (season, country, league, classify_mode);

COMMIT;

-- ############################################################
-- 06_bm_m020_view.sql
-- ############################################################
-- 06: BM_M020 ビュー classify_result_data_detail（05 の後）
BEGIN;

DROP VIEW IF EXISTS classify_result_data_detail CASCADE;

-- BM_M020: 国・リーグ・シーズン × 分類モードごとの試合数（件数 0 のモードも出す）
--   count = その分類モードの試合数 / remarks = 分類モードの文言（ClassifyMode と同じ）
CREATE VIEW classify_result_data_detail AS
WITH modes (classify_mode, sort_order, remarks) AS (
  VALUES
    (1, 0, '20分以内にホームチームが得点後、次の得点が前半に入る'),
    (2, 1, '20分以内にホームチームが得点後、次の得点が後半に入る'),
    (3, 2, '20分以内にホームチームが得点後、得点が入らない'),
    (4, 3, '20分以内にアウェーチームが得点後、次の得点が前半に入る'),
    (5, 4, '20分以内にアウェーチームが得点後、次の得点が後半に入る'),
    (6, 5, '20分以内にアウェーチームが得点後、得点が入らない'),
    (7, 6, '20分〜前半にホームチームが得点後、次の得点が前半に入る'),
    (8, 7, '20分〜前半にホームチームが得点後、次の得点が後半に入る'),
    (9, 8, '20分〜前半にホームチームが得点後、得点が入らない'),
    (10, 9, '20分〜前半にアウェーチームが得点後、次の得点が前半に入る'),
    (11, 10, '20分〜前半にアウェーチームが得点後、次の得点が後半に入る'),
    (12, 11, '20分〜前半にアウェーチームが得点後、得点が入らない'),
    (13, 12, '前半で無得点後、後半にホーム側の得点が入る'),
    (14, 13, '前半で無得点後、後半にアウェー側の得点が入る'),
    (15, 14, '両チーム無得点後、得点が入らない'),
    (-1, 15, '条件対象外')
),
leagues AS (
  SELECT DISTINCT season, country, league FROM classify_result_data
),
matches AS (
  SELECT DISTINCT season, country, league, round_no, home_team_name, away_team_name, classify_mode
  FROM classify_result_data
)
SELECT
  l.season,
  l.country,
  l.league,
  m.classify_mode,
  COUNT(x.home_team_name)::INTEGER AS count,
  m.remarks,
  m.sort_order
FROM leagues l
CROSS JOIN modes m
LEFT JOIN matches x
  ON x.season = l.season AND x.country = l.country AND x.league = l.league
 AND x.classify_mode = m.classify_mode
GROUP BY l.season, l.country, l.league, m.classify_mode, m.remarks, m.sort_order;

COMMIT;

-- ############################################################
-- 07_bm_m021.sql
-- ############################################################
-- 07: BM_M021
BEGIN;

-- BM_M021 team_match_final_stats 作り直し（既存データは削除される）
-- 変更点: seq を <シーズン>-<枝番>（seq_counter 採番）に、season / country / league / match_id / goals_for / goals_against を追加、
--         値を数値型に変更、同じ試合は UPSERT（再処理しても行が増えない）

DROP TABLE IF EXISTS team_match_final_stats CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'team_match_final_stats';

-- 1行 = 1試合 × 1チーム視点（1試合で ha=H と ha=A の2行）
CREATE TABLE team_match_final_stats (
  seq                                            VARCHAR(40)  NOT NULL,   -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season                                         VARCHAR(20)  NOT NULL,   -- country_league_season_master.season_year
  country                                        TEXT         NOT NULL,   -- 国
  league                                         TEXT         NOT NULL,   -- リーグ
  round_no                                       SMALLINT     NOT NULL,   -- ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別する）
  team_name                                      TEXT         NOT NULL,   -- チーム（この行の視点）
  versus_team_name                               TEXT         NOT NULL,   -- 対戦チーム
  ha                                             CHAR(1)      NOT NULL,   -- 対戦場所 H:ホーム / A:アウェー
  match_id                                       TEXT,                    -- マッチID（参照用）
  goals_for                                      SMALLINT     NOT NULL,   -- 得点
  goals_against                                  SMALLINT     NOT NULL,   -- 失点
  score                                          VARCHAR(20)  NOT NULL,   -- スコア表示（勝敗記号＋自チーム得点-相手得点。例: ○2-1）
  result                                         VARCHAR(4)   NOT NULL,   -- 結果 WIN / LOSE / DRAW
  game_fin_rank                                  SMALLINT,                -- 試合終了時点の順位（数字が取れなければ NULL）
  opposite_game_fin_rank                         SMALLINT,                -- 対戦相手の試合終了時点の順位
  exp                                            NUMERIC(6,2),            -- 期待値（xG）
  opposite_exp                                   NUMERIC(6,2),            -- 対戦相手期待値（xG）
  in_goal_exp                                    NUMERIC(6,2),            -- 枠内ゴール期待値
  opposite_in_goal_exp                           NUMERIC(6,2),            -- 対戦相手枠内ゴール期待値
  donation                                       NUMERIC(5,1),            -- ポゼッション（%）
  opposite_donation                              NUMERIC(5,1),            -- 対戦相手ポゼッション（%）
  shoot_all                                      INTEGER,                 -- シュート数
  opposite_shoot_all                             INTEGER,                 -- 対戦相手シュート数
  shoot_in                                       INTEGER,                 -- 枠内シュート
  opposite_shoot_in                              INTEGER,                 -- 対戦相手枠内シュート
  shoot_out                                      INTEGER,                 -- 枠外シュート
  opposite_shoot_out                             INTEGER,                 -- 対戦相手枠外シュート
  block_shoot                                    INTEGER,                 -- ブロックシュート
  opposite_block_shoot                           INTEGER,                 -- 対戦相手ブロックシュート
  big_chance                                     INTEGER,                 -- ビッグチャンス
  opposite_big_chance                            INTEGER,                 -- 対戦相手ビッグチャンス
  corner                                         INTEGER,                 -- コーナーキック
  opposite_corner                                INTEGER,                 -- 対戦相手コーナーキック
  box_shoot_in                                   INTEGER,                 -- ボックス内シュート
  opposite_box_shoot_in                          INTEGER,                 -- 対戦相手ボックス内シュート
  box_shoot_out                                  INTEGER,                 -- ボックス外シュート
  opposite_box_shoot_out                         INTEGER,                 -- 対戦相手ボックス外シュート
  goal_post                                      INTEGER,                 -- ゴールポスト
  opposite_goal_post                             INTEGER,                 -- 対戦相手ゴールポスト
  goal_head                                      INTEGER,                 -- ヘディングゴール
  opposite_goal_head                             INTEGER,                 -- 対戦相手ヘディングゴール
  keeper_save                                    INTEGER,                 -- キーパーセーブ
  opposite_keeper_save                           INTEGER,                 -- 対戦相手キーパーセーブ
  free_kick                                      INTEGER,                 -- フリーキック
  opposite_free_kick                             INTEGER,                 -- 対戦相手フリーキック
  offside                                        INTEGER,                 -- オフサイド
  opposite_offside                               INTEGER,                 -- 対戦相手オフサイド
  foul                                           INTEGER,                 -- ファウル
  opposite_foul                                  INTEGER,                 -- 対戦相手ファウル
  yellow_card                                    INTEGER,                 -- イエローカード
  opposite_yellow_card                           INTEGER,                 -- 対戦相手イエローカード
  red_card                                       INTEGER,                 -- レッドカード
  opposite_red_card                              INTEGER,                 -- 対戦相手レッドカード
  slow_in                                        INTEGER,                 -- スローイン
  opposite_slow_in                               INTEGER,                 -- 対戦相手スローイン
  box_touch                                      INTEGER,                 -- ボックスタッチ
  opposite_box_touch                             INTEGER,                 -- 対戦相手ボックスタッチ
  pass_count_success_ratio                       NUMERIC(5,1),            -- パス_成功率（%）
  pass_count_success_count                       INTEGER,                 -- パス_成功数
  pass_count_try_count                           INTEGER,                 -- パス_試行数
  opposite_pass_count_success_ratio              NUMERIC(5,1),            -- 対戦相手パス_成功率（%）
  opposite_pass_count_success_count              INTEGER,                 -- 対戦相手パス_成功数
  opposite_pass_count_try_count                  INTEGER,                 -- 対戦相手パス_試行数
  long_pass_count_success_ratio                  NUMERIC(5,1),            -- ロングパス_成功率（%）
  long_pass_count_success_count                  INTEGER,                 -- ロングパス_成功数
  long_pass_count_try_count                      INTEGER,                 -- ロングパス_試行数
  opposite_long_pass_count_success_ratio         NUMERIC(5,1),            -- 対戦相手ロングパス_成功率（%）
  opposite_long_pass_count_success_count         INTEGER,                 -- 対戦相手ロングパス_成功数
  opposite_long_pass_count_try_count             INTEGER,                 -- 対戦相手ロングパス_試行数
  final_third_pass_count_success_ratio           NUMERIC(5,1),            -- ファイナルサードパス_成功率（%）
  final_third_pass_count_success_count           INTEGER,                 -- ファイナルサードパス_成功数
  final_third_pass_count_try_count               INTEGER,                 -- ファイナルサードパス_試行数
  opposite_final_third_pass_count_success_ratio  NUMERIC(5,1),            -- 対戦相手ファイナルサードパス_成功率（%）
  opposite_final_third_pass_count_success_count  INTEGER,                 -- 対戦相手ファイナルサードパス_成功数
  opposite_final_third_pass_count_try_count      INTEGER,                 -- 対戦相手ファイナルサードパス_試行数
  cross_count_success_ratio                      NUMERIC(5,1),            -- クロス_成功率（%）
  cross_count_success_count                      INTEGER,                 -- クロス_成功数
  cross_count_try_count                          INTEGER,                 -- クロス_試行数
  opposite_cross_count_success_ratio             NUMERIC(5,1),            -- 対戦相手クロス_成功率（%）
  opposite_cross_count_success_count             INTEGER,                 -- 対戦相手クロス_成功数
  opposite_cross_count_try_count                 INTEGER,                 -- 対戦相手クロス_試行数
  tackle_count_success_ratio                     NUMERIC(5,1),            -- タックル_成功率（%）
  tackle_count_success_count                     INTEGER,                 -- タックル_成功数
  tackle_count_try_count                         INTEGER,                 -- タックル_試行数
  opposite_tackle_count_success_ratio            NUMERIC(5,1),            -- 対戦相手タックル_成功率（%）
  opposite_tackle_count_success_count            INTEGER,                 -- 対戦相手タックル_成功数
  opposite_tackle_count_try_count                INTEGER,                 -- 対戦相手タックル_試行数
  clear_count                                    INTEGER,                 -- クリア数
  opposite_clear_count                           INTEGER,                 -- 対戦相手クリア数
  duel_count                                     INTEGER,                 -- デュエル勝利数
  opposite_duel_count                            INTEGER,                 -- 対戦相手デュエル勝利数
  intercept_count                                INTEGER,                 -- インターセプト数
  opposite_intercept_count                       INTEGER,                 -- 対戦相手インターセプト数
  weather                                        TEXT,                    -- 天気
  temperature                                    TEXT,                    -- 気温
  humid                                          TEXT,                    -- 湿度
  register_id                                    VARCHAR(100)                NOT NULL,
  register_time                                  TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                                      VARCHAR(100)                NOT NULL,
  update_time                                    TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_team_match_final_stats PRIMARY KEY (seq),
  CONSTRAINT uq_team_match_final_stats
    UNIQUE (season, country, league, round_no, team_name, versus_team_name, ha),
  CONSTRAINT ck_team_match_final_stats_ha CHECK (ha IN ('H', 'A')),
  CONSTRAINT ck_team_match_final_stats_result CHECK (result IN ('WIN', 'LOSE', 'DRAW'))
);

-- チームの成績を引く用
CREATE INDEX idx_team_match_final_stats_team
  ON team_match_final_stats (country, league, team_name, season, round_no);

COMMIT;

-- ############################################################
-- 08_stat_functions.sql
-- ############################################################
-- 08: 統計の計算関数（平均・σ・歪度・尖度・相関係数・率・正規化スコア）。CREATE OR REPLACE なので何度流してもよい
BEGIN;

-- ===== 統計の計算関数（件数 n と Σx, Σx², Σx³, Σx⁴ から計算。NUMERIC で正確に計算してから平方根等を取る）=====
-- 平均
CREATE OR REPLACE FUNCTION bm_stat_mean(n NUMERIC, s1 NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN n > 0 THEN s1 / n END
$$;

-- 標準偏差（母標準偏差。旧 BM_M023 と同じ）
CREATE OR REPLACE FUNCTION bm_stat_sigma(n NUMERIC, s1 NUMERIC, s2 NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN n > 0 THEN sqrt(GREATEST((s2 - s1 * s1 / n) / n, 0)) END
$$;

-- 歪度（標本歪度 G1。旧 BM_M023 と同じ式。n < 3 または分散 0 は NULL）
CREATE OR REPLACE FUNCTION bm_stat_skew(n NUMERIC, s1 NUMERIC, s2 NUMERIC, s3 NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN n >= 3 AND m2 > 0
              THEN (n * sqrt(n - 1) / (n - 2) * m3 / power(m2, 1.5))
         END
  FROM (SELECT s2 - s1 * s1 / n                                    AS m2,
               s3 - 3 * s1 * s2 / n + 2 * s1 * s1 * s1 / (n * n)    AS m3
        WHERE n > 0) t
$$;

-- 尖度（標本超過尖度 G2。旧 BM_M023 と同じ式。n < 4 または分散 0 は NULL）
CREATE OR REPLACE FUNCTION bm_stat_kurt(n NUMERIC, s1 NUMERIC, s2 NUMERIC, s3 NUMERIC, s4 NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN n >= 4 AND m2 > 0
              THEN (n + 1) * n * (n - 1) / ((n - 2) * (n - 3)) * m4 / (m2 * m2)
                   - 3 * (n - 1) * (n - 1) / ((n - 2) * (n - 3))
         END
  FROM (SELECT s2 - s1 * s1 / n                                                          AS m2,
               s4 - 4 * s1 * s3 / n + 6 * s1 * s1 * s2 / (n * n) - 3 * power(s1, 4) / power(n, 3) AS m4
        WHERE n > 0) t
$$;

-- ピアソン相関係数（n と Σx, Σy, Σx², Σy², Σxy から。n < 2 またはどちらかの分散が 0 なら NULL）
CREATE OR REPLACE FUNCTION bm_stat_corr(n NUMERIC, sx NUMERIC, sy NUMERIC, sxx NUMERIC, syy NUMERIC, sxy NUMERIC)
RETURNS NUMERIC LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN n >= 2 AND vx > 0 AND vy > 0 THEN cov / sqrt(vx * vy) END
  FROM (SELECT n * sxy - sx * sy AS cov,
               n * sxx - sx * sx AS vx,
               n * syy - sy * sy AS vy) t
$$;

-- 率（%）: 分母が 0・NULL なら NULL（BM_M036〜M042 のビューで使う）
CREATE OR REPLACE FUNCTION bm_rate_pct(num NUMERIC, den NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN den > 0 AND num IS NOT NULL THEN ROUND(num * 100.0 / den, 2) END
$$;

-- 正規化スコア（値が大きいほど 1。値が NULL なら 0 = 旧実装と同じ扱い）
CREATE OR REPLACE FUNCTION bm_score_high(v NUMERIC, lo NUMERIC, hi NUMERIC) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN v IS NULL THEN 0 ELSE GREATEST(0, LEAST(1, (v - lo) / (hi - lo))) END
$$;

COMMIT;

-- ############################################################
-- 09_bm_m023_table.sql
-- ############################################################
-- 09: BM_M023 / M026 明細テーブル score_based_feature_match_stats（既存データは削除）
-- ※ このファイルを流し直すと、後の番号のビューが CASCADE で消えることがあります。その場合は後の番号のファイルも続けて流してください。
BEGIN;

-- BM_M023 / BM_M026 作り直し（既存データは削除される）
--  旧: score_based_feature_stats（リーグ単位）/ each_team_score_based_feature_stats（チーム単位）に
--      統計値を文字列で積み上げ、歪度・尖度は stat_encryption の全履歴を復号して計算
--  新: 試合ごとの明細 score_based_feature_match_stats（件数・Σx〜Σx⁴・最小・最大）を持ち、
--      リーグ・チーム・カード単位の統計はビューで出す（同じ試合を何度処理しても二重にならない）
--      ラウンド番号・記録時間を持つので、推移（ラウンド N 終了時点の統計）もビューで出す
--      （旧 BM_M023H / BM_M026H の履歴テーブル *_history は削除）

-- テーブル・ビューのどちらでも消せるように、種類を見て DROP する
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('score_based_feature_stats', 'each_team_score_based_feature_stats',
                        'card_score_based_feature_stats', 'score_based_feature_side',
                        'score_based_feature_trend', 'each_team_score_based_feature_trend',
                        'score_based_feature_stats_history', 'each_team_score_based_feature_stats_history')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname DESC
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DROP TABLE IF EXISTS score_based_feature_match_stats CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'score_based_feature_match_stats';

-- ===== 明細: 1行 = 1試合 × 区分 × 特徴量（ホーム・アウェーの値を横に持つ）=====
CREATE TABLE score_based_feature_match_stats (
  seq             VARCHAR(40)  NOT NULL,  -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season          VARCHAR(20)  NOT NULL,  -- country_league_season_master.season_year
  country         TEXT         NOT NULL,  -- 国
  league          TEXT         NOT NULL,  -- リーグ
  home_team_name  TEXT         NOT NULL,  -- ホームチーム
  away_team_name  TEXT         NOT NULL,  -- アウェーチーム
  match_id        TEXT,                   -- 参照用（BookDataEntity.matchId）
  round_no        SMALLINT     NOT NULL,  -- ラウンド番号（キーの「ラウンド N」の N。同じ対戦が複数回あるリーグで試合を区別するため一意キーに含む）
  record_time     TIMESTAMP(0) WITH TIME ZONE, -- 試合終了（FIN）行の記録時間（取れなければ NULL）
  situation       VARCHAR(10)  NOT NULL,  -- 得点あり / 得点なし（試合終了時のスコア）
  chk_body        VARCHAR(10)  NOT NULL,  -- 区分: ALL（全体）/ 1st（前半）/ 2nd（後半）/ スコア（例: 1-0）
  feature         VARCHAR(40)  NOT NULL,  -- 特徴量（例: shootAll, ballPossesion, passCount_rate）
  feature_order   SMALLINT     NOT NULL,  -- 特徴量の並び順
  home_n     INTEGER NOT NULL DEFAULT 0,        -- ホーム 値がある観測数
  home_s1    NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx
  home_s2    NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx²
  home_s3    NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx³
  home_s4    NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx⁴
  home_min   NUMERIC,                           -- ホーム 最小
  home_max   NUMERIC,                           -- ホーム 最大
  home_tn    INTEGER NOT NULL DEFAULT 0,        -- ホーム 時間: 観測数（値があり、試合時間が読めた行）
  home_ts1   NUMERIC NOT NULL DEFAULT 0,        -- ホーム 時間: Σ分
  home_ts2   NUMERIC NOT NULL DEFAULT 0,        -- ホーム 時間: Σ分²
  home_tmin  NUMERIC,                           -- ホーム 時間: 最小（分）
  home_tmax  NUMERIC,                           -- ホーム 時間: 最大（分）
  away_n     INTEGER NOT NULL DEFAULT 0,        -- アウェー 値がある観測数
  away_s1    NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx
  away_s2    NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx²
  away_s3    NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx³
  away_s4    NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx⁴
  away_min   NUMERIC,                           -- アウェー 最小
  away_max   NUMERIC,                           -- アウェー 最大
  away_tn    INTEGER NOT NULL DEFAULT 0,        -- アウェー 時間: 観測数（値があり、試合時間が読めた行）
  away_ts1   NUMERIC NOT NULL DEFAULT 0,        -- アウェー 時間: Σ分
  away_ts2   NUMERIC NOT NULL DEFAULT 0,        -- アウェー 時間: Σ分²
  away_tmin  NUMERIC,                           -- アウェー 時間: 最小（分）
  away_tmax  NUMERIC,                           -- アウェー 時間: 最大（分）
  register_id     VARCHAR(100)                NOT NULL,
  register_time   TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id       VARCHAR(100)                NOT NULL,
  update_time     TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_score_based_feature_match_stats PRIMARY KEY (seq),
  CONSTRAINT uq_score_based_feature_match_stats
    UNIQUE (season, country, league, round_no, home_team_name, away_team_name, chk_body, feature)
);

CREATE INDEX idx_sbfms_league ON score_based_feature_match_stats (season, country, league, chk_body, feature);
CREATE INDEX idx_sbfms_home   ON score_based_feature_match_stats (country, league, home_team_name, season);
CREATE INDEX idx_sbfms_away   ON score_based_feature_match_stats (country, league, away_team_name, season);
CREATE INDEX idx_sbfms_round  ON score_based_feature_match_stats (season, country, league, round_no);

COMMIT;

-- ############################################################
-- 10_bm_m023_views.sql
-- ############################################################
-- 10: BM_M023 / M026 集計ビュー（09・08 の後）
-- ※ このファイルを流し直すと、後の番号のビューが CASCADE で消えることがあります。その場合は後の番号のファイルも続けて流してください。
BEGIN;

DROP VIEW IF EXISTS score_based_feature_side CASCADE;
DROP VIEW IF EXISTS score_based_feature_stats CASCADE;
DROP VIEW IF EXISTS each_team_score_based_feature_stats CASCADE;
DROP VIEW IF EXISTS card_score_based_feature_stats CASCADE;

-- ===== チーム視点に並べ替えたビュー（1明細行 → ホーム視点・アウェー視点の2行）=====
CREATE VIEW score_based_feature_side AS
SELECT
  season, country, league, situation, chk_body, feature, feature_order,
  'H'::CHAR(1) AS ha, home_team_name AS team, away_team_name AS opponent, match_id, round_no, record_time,
  season || '|' || round_no || '|' || home_team_name || '|' || away_team_name AS match_key,
  home_n AS n, home_s1 AS s1, home_s2 AS s2, home_s3 AS s3, home_s4 AS s4, home_min AS min, home_max AS max,
  home_tn AS tn, home_ts1 AS ts1, home_ts2 AS ts2, home_tmin AS tmin, home_tmax AS tmax
FROM score_based_feature_match_stats
WHERE home_n > 0
UNION ALL
SELECT
  season, country, league, situation, chk_body, feature, feature_order,
  'A'::CHAR(1) AS ha, away_team_name AS team, home_team_name AS opponent, match_id, round_no, record_time,
  season || '|' || round_no || '|' || home_team_name || '|' || away_team_name AS match_key,
  away_n AS n, away_s1 AS s1, away_s2 AS s2, away_s3 AS s3, away_s4 AS s4, away_min AS min, away_max AS max,
  away_tn AS tn, away_ts1 AS ts1, away_ts2 AS ts2, away_tmin AS tmin, away_tmax AS tmax
FROM score_based_feature_match_stats
WHERE away_n > 0;

-- ===== BM_M023: リーグ単位（ha = H: ホームチームの値 / A: アウェーチームの値）=====
CREATE VIEW score_based_feature_stats AS
SELECT
  season, country, league, situation, chk_body, feature, feature_order, ha,
  SUM(n)::INTEGER                                   AS cnt,
  ROUND(bm_stat_mean(SUM(n), SUM(s1)), 4)            AS ave,
  ROUND(bm_stat_sigma(SUM(n), SUM(s1), SUM(s2)), 4)  AS sigma,
  ROUND(bm_stat_skew(SUM(n), SUM(s1), SUM(s2), SUM(s3)), 4) AS skewness,
  ROUND(bm_stat_kurt(SUM(n), SUM(s1), SUM(s2), SUM(s3), SUM(s4)), 4) AS kurtosis,
  MIN(min)                                           AS min,
  MAX(max)                                           AS max,
  SUM(tn)::INTEGER                                   AS time_cnt,
  ROUND(bm_stat_mean(SUM(tn), SUM(ts1)), 2)          AS time_ave,
  ROUND(bm_stat_sigma(SUM(tn), SUM(ts1), SUM(ts2)), 2) AS time_sigma,
  MIN(tmin)                                          AS time_min,
  MAX(tmax)                                          AS time_max,
  COUNT(DISTINCT match_key)::INTEGER                 AS match_cnt
FROM score_based_feature_side
GROUP BY season, country, league, situation, chk_body, feature, feature_order, ha;

-- ===== BM_M026: チーム単位（そのチームの値。ha = H: ホーム戦 / A: アウェー戦 / *: ホーム・アウェー合算）=====
CREATE VIEW each_team_score_based_feature_stats AS
SELECT
  season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha, situation, chk_body, feature, feature_order,
  SUM(n)::INTEGER                                   AS cnt,
  ROUND(bm_stat_mean(SUM(n), SUM(s1)), 4)            AS ave,
  ROUND(bm_stat_sigma(SUM(n), SUM(s1), SUM(s2)), 4)  AS sigma,
  ROUND(bm_stat_skew(SUM(n), SUM(s1), SUM(s2), SUM(s3)), 4) AS skewness,
  ROUND(bm_stat_kurt(SUM(n), SUM(s1), SUM(s2), SUM(s3), SUM(s4)), 4) AS kurtosis,
  MIN(min)                                           AS min,
  MAX(max)                                           AS max,
  SUM(tn)::INTEGER                                   AS time_cnt,
  ROUND(bm_stat_mean(SUM(tn), SUM(ts1)), 2)          AS time_ave,
  ROUND(bm_stat_sigma(SUM(tn), SUM(ts1), SUM(ts2)), 2) AS time_sigma,
  MIN(tmin)                                          AS time_min,
  MAX(tmax)                                          AS time_max,
  COUNT(DISTINCT match_key)::INTEGER                 AS match_cnt
FROM score_based_feature_side
GROUP BY GROUPING SETS (
  (season, country, league, team, ha, situation, chk_body, feature, feature_order),
  (season, country, league, team, situation, chk_body, feature, feature_order));

-- ===== カード単位（team の値。opponent との対戦のみ）=====
CREATE VIEW card_score_based_feature_stats AS
SELECT
  season, country, league, team, opponent, ha, situation, chk_body, feature, feature_order,
  SUM(n)::INTEGER                                   AS cnt,
  ROUND(bm_stat_mean(SUM(n), SUM(s1)), 4)            AS ave,
  ROUND(bm_stat_sigma(SUM(n), SUM(s1), SUM(s2)), 4)  AS sigma,
  ROUND(bm_stat_skew(SUM(n), SUM(s1), SUM(s2), SUM(s3)), 4) AS skewness,
  ROUND(bm_stat_kurt(SUM(n), SUM(s1), SUM(s2), SUM(s3), SUM(s4)), 4) AS kurtosis,
  MIN(min)                                           AS min,
  MAX(max)                                           AS max,
  SUM(tn)::INTEGER                                   AS time_cnt,
  ROUND(bm_stat_mean(SUM(tn), SUM(ts1)), 2)          AS time_ave,
  ROUND(bm_stat_sigma(SUM(tn), SUM(ts1), SUM(ts2)), 2) AS time_sigma,
  MIN(tmin)                                          AS time_min,
  MAX(tmax)                                          AS time_max,
  COUNT(DISTINCT match_key)::INTEGER                 AS match_cnt
FROM score_based_feature_side
GROUP BY season, country, league, team, opponent, ha, situation, chk_body, feature, feature_order;

COMMIT;

-- ############################################################
-- 11_bm_m023_trend_views.sql
-- ############################################################
-- 11: BM_M023 / M026 推移ビュー（10 の後）
BEGIN;

DROP VIEW IF EXISTS score_based_feature_trend CASCADE;
DROP VIEW IF EXISTS each_team_score_based_feature_trend CASCADE;

-- ===== 推移（旧 BM_M023H / BM_M026H の置き換え）=====
-- as_of_round = N の行は「ラウンド N 終了時点」（round_no <= N の試合だけで集計した値）。
-- 2時点の差分（METRIC_DELTA）は as_of_round の違う2行を引き算する。
-- ラウンド番号が取れなかった試合（round_no が NULL）は推移には入らない（通常のビューには入る）。

-- リーグ単位の推移
CREATE VIEW score_based_feature_trend AS
WITH rounds AS (
  SELECT DISTINCT season, country, league, round_no
  FROM score_based_feature_match_stats
  WHERE round_no IS NOT NULL
)
SELECT
  r.round_no AS as_of_round,
  s.season, s.country, s.league, s.situation, s.chk_body, s.feature, s.feature_order, s.ha,
  SUM(s.n)::INTEGER                                   AS cnt,
  ROUND(bm_stat_mean(SUM(s.n), SUM(s.s1)), 4)            AS ave,
  ROUND(bm_stat_sigma(SUM(s.n), SUM(s.s1), SUM(s.s2)), 4)  AS sigma,
  ROUND(bm_stat_skew(SUM(s.n), SUM(s.s1), SUM(s.s2), SUM(s.s3)), 4) AS skewness,
  ROUND(bm_stat_kurt(SUM(s.n), SUM(s.s1), SUM(s.s2), SUM(s.s3), SUM(s.s4)), 4) AS kurtosis,
  MIN(s.min)                                           AS min,
  MAX(s.max)                                           AS max,
  SUM(s.tn)::INTEGER                                   AS time_cnt,
  ROUND(bm_stat_mean(SUM(s.tn), SUM(s.ts1)), 2)          AS time_ave,
  ROUND(bm_stat_sigma(SUM(s.tn), SUM(s.ts1), SUM(s.ts2)), 2) AS time_sigma,
  MIN(s.tmin)                                          AS time_min,
  MAX(s.tmax)                                          AS time_max,
  COUNT(DISTINCT s.match_key)::INTEGER                 AS match_cnt
FROM rounds r
JOIN score_based_feature_side s
  ON s.season = r.season AND s.country = r.country AND s.league = r.league
 AND s.round_no <= r.round_no
GROUP BY r.round_no, s.season, s.country, s.league, s.situation, s.chk_body, s.feature, s.feature_order, s.ha;

-- チーム単位の推移（ha = H / A / *（合算）。team で絞って使うこと。例: WHERE team = 'X' AND ha = '*' AND feature = 'shootAll' ORDER BY as_of_round）
CREATE VIEW each_team_score_based_feature_trend AS
WITH rounds AS (
  SELECT DISTINCT season, country, league, round_no
  FROM score_based_feature_match_stats
  WHERE round_no IS NOT NULL
)
SELECT
  r.round_no AS as_of_round,
  s.season, s.country, s.league, s.team, COALESCE(s.ha, '*')::CHAR(1) AS ha, s.situation, s.chk_body, s.feature, s.feature_order,
  SUM(s.n)::INTEGER                                   AS cnt,
  ROUND(bm_stat_mean(SUM(s.n), SUM(s.s1)), 4)            AS ave,
  ROUND(bm_stat_sigma(SUM(s.n), SUM(s.s1), SUM(s.s2)), 4)  AS sigma,
  ROUND(bm_stat_skew(SUM(s.n), SUM(s.s1), SUM(s.s2), SUM(s.s3)), 4) AS skewness,
  ROUND(bm_stat_kurt(SUM(s.n), SUM(s.s1), SUM(s.s2), SUM(s.s3), SUM(s.s4)), 4) AS kurtosis,
  MIN(s.min)                                           AS min,
  MAX(s.max)                                           AS max,
  SUM(s.tn)::INTEGER                                   AS time_cnt,
  ROUND(bm_stat_mean(SUM(s.tn), SUM(s.ts1)), 2)          AS time_ave,
  ROUND(bm_stat_sigma(SUM(s.tn), SUM(s.ts1), SUM(s.ts2)), 2) AS time_sigma,
  MIN(s.tmin)                                          AS time_min,
  MAX(s.tmax)                                          AS time_max,
  COUNT(DISTINCT s.match_key)::INTEGER                 AS match_cnt
FROM rounds r
JOIN score_based_feature_side s
  ON s.season = r.season AND s.country = r.country AND s.league = r.league
 AND s.round_no <= r.round_no
GROUP BY GROUPING SETS (
  (r.round_no, s.season, s.country, s.league, s.team, s.ha, s.situation, s.chk_body, s.feature, s.feature_order),
  (r.round_no, s.season, s.country, s.league, s.team, s.situation, s.chk_body, s.feature, s.feature_order));

COMMIT;

-- ############################################################
-- 12_bm_m024_table.sql
-- ############################################################
-- 12: BM_M024 明細テーブル calc_correlation_match_stats（既存データは削除）
-- ※ このファイルを流し直すと、後の番号のビューが CASCADE で消えることがあります。その場合は後の番号のファイルも続けて流してください。
BEGIN;

-- BM_M024 作り直し（既存データは削除される）
--  旧: calc_correlation（1試合ごとの相関係数を毎回 INSERT。ロングパス・デュエルの列なし）
--  新: 試合ごとの明細 calc_correlation_match_stats（組数・Σx・Σy・Σx²・Σy²・Σxy）を持ち、
--      相関係数はリーグ・チーム・カード単位、ラウンド推移ともビューで出す（再処理しても二重にならない）

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('calc_correlation', 'calc_correlation_side', 'calc_correlation_stats',
                        'each_team_calc_correlation_stats', 'card_calc_correlation_stats',
                        'calc_correlation_trend', 'each_team_calc_correlation_trend')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname DESC
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DROP TABLE IF EXISTS calc_correlation_match_stats CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'calc_correlation_match_stats';

-- ===== 明細: 1行 = 1試合 × 区分（ALL / 1st / 2nd）× 特徴量 =====
-- x = 区間の終わり（後のスナップショット）の特徴量の値 / y = その区間にその側が得点したか（1/0）
CREATE TABLE calc_correlation_match_stats (
  seq             VARCHAR(40)  NOT NULL,  -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season          VARCHAR(20)  NOT NULL,  -- country_league_season_master.season_year
  country         TEXT         NOT NULL,  -- 国
  league          TEXT         NOT NULL,  -- リーグ
  home_team_name  TEXT         NOT NULL,  -- ホームチーム
  away_team_name  TEXT         NOT NULL,  -- アウェーチーム
  match_id        TEXT,                   -- 参照用
  round_no        SMALLINT     NOT NULL,  -- ラウンド番号（キーの「ラウンド N」の N。同じ対戦が複数回あるリーグで試合を区別するため一意キーに含む）
  record_time     TIMESTAMP(0) WITH TIME ZONE, -- 試合終了（FIN）行の記録時間
  situation       VARCHAR(10)  NOT NULL,  -- 得点あり / 得点なし（試合終了時のスコア）
  chk_body        VARCHAR(10)  NOT NULL,  -- 区分: ALL（全体）/ 1st（前半）/ 2nd（後半）
  feature         VARCHAR(40)  NOT NULL,  -- 特徴量（ScoreBasedFeature と同じ名前）
  feature_order   SMALLINT     NOT NULL,  -- 特徴量の並び順
  home_n    INTEGER NOT NULL DEFAULT 0,        -- ホーム 組の数（x と y が両方ある区間数）
  home_sx   NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx
  home_sy   NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σy（得点した区間数）
  home_sxx  NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σx²
  home_syy  NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σy²
  home_sxy  NUMERIC NOT NULL DEFAULT 0,        -- ホーム Σxy
  away_n    INTEGER NOT NULL DEFAULT 0,        -- アウェー 組の数（x と y が両方ある区間数）
  away_sx   NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx
  away_sy   NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σy（得点した区間数）
  away_sxx  NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σx²
  away_syy  NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σy²
  away_sxy  NUMERIC NOT NULL DEFAULT 0,        -- アウェー Σxy
  register_id     VARCHAR(100)                NOT NULL,
  register_time   TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id       VARCHAR(100)                NOT NULL,
  update_time     TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_calc_correlation_match_stats PRIMARY KEY (seq),
  CONSTRAINT uq_calc_correlation_match_stats
    UNIQUE (season, country, league, round_no, home_team_name, away_team_name, chk_body, feature)
);

CREATE INDEX idx_ccms_league ON calc_correlation_match_stats (season, country, league, chk_body, feature);
CREATE INDEX idx_ccms_home   ON calc_correlation_match_stats (country, league, home_team_name, season);
CREATE INDEX idx_ccms_away   ON calc_correlation_match_stats (country, league, away_team_name, season);
CREATE INDEX idx_ccms_round  ON calc_correlation_match_stats (season, country, league, round_no);

COMMIT;

-- ############################################################
-- 13_bm_m024_views.sql
-- ############################################################
-- 13: BM_M024 相関係数ビュー（12・08 の後）
-- ※ このファイルを流し直すと、後の番号のビューが CASCADE で消えることがあります。その場合は後の番号のファイルも続けて流してください。
BEGIN;

DROP VIEW IF EXISTS calc_correlation_side CASCADE;
DROP VIEW IF EXISTS calc_correlation_stats CASCADE;
DROP VIEW IF EXISTS each_team_calc_correlation_stats CASCADE;
DROP VIEW IF EXISTS card_calc_correlation_stats CASCADE;

-- ===== チーム視点に並べ替えたビュー =====
CREATE VIEW calc_correlation_side AS
SELECT
  season, country, league, situation, chk_body, feature, feature_order,
  'H'::CHAR(1) AS ha, home_team_name AS team, away_team_name AS opponent, match_id, round_no, record_time,
  season || '|' || round_no || '|' || home_team_name || '|' || away_team_name AS match_key,
  home_n AS n, home_sx AS sx, home_sy AS sy, home_sxx AS sxx, home_syy AS syy, home_sxy AS sxy
FROM calc_correlation_match_stats
WHERE home_n > 0
UNION ALL
SELECT
  season, country, league, situation, chk_body, feature, feature_order,
  'A'::CHAR(1) AS ha, away_team_name AS team, home_team_name AS opponent, match_id, round_no, record_time,
  season || '|' || round_no || '|' || home_team_name || '|' || away_team_name AS match_key,
  away_n AS n, away_sx AS sx, away_sy AS sy, away_sxx AS sxx, away_syy AS syy, away_sxy AS sxy
FROM calc_correlation_match_stats
WHERE away_n > 0;

-- ===== リーグ単位（ha = H: ホームチームの特徴量とホームの得点 / A: アウェー側）=====
--   pearson = 相関係数 / pair_cnt = 区間数 / goal_cnt = 得点した区間数 / match_cnt = 試合数
CREATE VIEW calc_correlation_stats AS
SELECT season, country, league, situation, chk_body, feature, feature_order, ha,
  SUM(n)::INTEGER                                  AS pair_cnt,
  SUM(sy)::INTEGER                                 AS goal_cnt,
  ROUND(bm_stat_corr(SUM(n), SUM(sx), SUM(sy), SUM(sxx), SUM(syy), SUM(sxy)), 5) AS pearson,
  COUNT(DISTINCT match_key)::INTEGER               AS match_cnt
FROM calc_correlation_side
GROUP BY season, country, league, situation, chk_body, feature, feature_order, ha;

-- ===== チーム単位（ha = H / A / *（合算））=====
CREATE VIEW each_team_calc_correlation_stats AS
SELECT season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha, situation, chk_body, feature, feature_order,
  SUM(n)::INTEGER                                  AS pair_cnt,
  SUM(sy)::INTEGER                                 AS goal_cnt,
  ROUND(bm_stat_corr(SUM(n), SUM(sx), SUM(sy), SUM(sxx), SUM(syy), SUM(sxy)), 5) AS pearson,
  COUNT(DISTINCT match_key)::INTEGER               AS match_cnt
FROM calc_correlation_side
GROUP BY GROUPING SETS (
  (season, country, league, team, ha, situation, chk_body, feature, feature_order),
  (season, country, league, team, situation, chk_body, feature, feature_order));

-- ===== カード単位 =====
CREATE VIEW card_calc_correlation_stats AS
SELECT season, country, league, team, opponent, ha, situation, chk_body, feature, feature_order,
  SUM(n)::INTEGER                                  AS pair_cnt,
  SUM(sy)::INTEGER                                 AS goal_cnt,
  ROUND(bm_stat_corr(SUM(n), SUM(sx), SUM(sy), SUM(sxx), SUM(syy), SUM(sxy)), 5) AS pearson,
  COUNT(DISTINCT match_key)::INTEGER               AS match_cnt
FROM calc_correlation_side
GROUP BY season, country, league, team, opponent, ha, situation, chk_body, feature, feature_order;

COMMIT;

-- ############################################################
-- 14_bm_m024_trend_views.sql
-- ############################################################
-- 14: BM_M024 推移ビュー（13 の後）
BEGIN;

DROP VIEW IF EXISTS calc_correlation_trend CASCADE;
DROP VIEW IF EXISTS each_team_calc_correlation_trend CASCADE;

-- ===== 推移（as_of_round = ラウンド N 終了時点）=====
CREATE VIEW calc_correlation_trend AS
WITH rounds AS (
  SELECT DISTINCT season, country, league, round_no FROM calc_correlation_match_stats WHERE round_no IS NOT NULL
)
SELECT r.round_no AS as_of_round,
  s.season, s.country, s.league, s.situation, s.chk_body, s.feature, s.feature_order, s.ha,
  SUM(s.n)::INTEGER                                  AS pair_cnt,
  SUM(s.sy)::INTEGER                                 AS goal_cnt,
  ROUND(bm_stat_corr(SUM(s.n), SUM(s.sx), SUM(s.sy), SUM(s.sxx), SUM(s.syy), SUM(s.sxy)), 5) AS pearson,
  COUNT(DISTINCT s.match_key)::INTEGER               AS match_cnt
FROM rounds r
JOIN calc_correlation_side s
  ON s.season = r.season AND s.country = r.country AND s.league = r.league AND s.round_no <= r.round_no
GROUP BY r.round_no, s.season, s.country, s.league, s.situation, s.chk_body, s.feature, s.feature_order, s.ha;

CREATE VIEW each_team_calc_correlation_trend AS
WITH rounds AS (
  SELECT DISTINCT season, country, league, round_no FROM calc_correlation_match_stats WHERE round_no IS NOT NULL
)
SELECT r.round_no AS as_of_round,
  s.season, s.country, s.league, s.team, COALESCE(s.ha, '*')::CHAR(1) AS ha, s.situation, s.chk_body, s.feature, s.feature_order,
  SUM(s.n)::INTEGER                                  AS pair_cnt,
  SUM(s.sy)::INTEGER                                 AS goal_cnt,
  ROUND(bm_stat_corr(SUM(s.n), SUM(s.sx), SUM(s.sy), SUM(s.sxx), SUM(s.syy), SUM(s.sxy)), 5) AS pearson,
  COUNT(DISTINCT s.match_key)::INTEGER               AS match_cnt
FROM rounds r
JOIN calc_correlation_side s
  ON s.season = r.season AND s.country = r.country AND s.league = r.league AND s.round_no <= r.round_no
GROUP BY GROUPING SETS (
  (r.round_no, s.season, s.country, s.league, s.team, s.ha, s.situation, s.chk_body, s.feature, s.feature_order),
  (r.round_no, s.season, s.country, s.league, s.team, s.situation, s.chk_body, s.feature, s.feature_order));

COMMIT;

-- ############################################################
-- 15_bm_m025_ranking.sql
-- ############################################################
-- 15: BM_M025 相関係数ランキングビュー（13 の後）
BEGIN;

-- BM_M025 作り直し（相関係数ランキング）
--  旧: calc_correlation_ranking テーブル（1試合ごとの相関係数を並べ替え、rank1st〜rank74th に「項目名,値」の文字列で INSERT）
--  新: BM_M024 のビュー（calc_correlation_stats 等）をウィンドウ関数で順位付けするビュー。Java のクラスは不要。
--  ※ BM_M024 の DDL（ddl_calc_correlation_rebuild.sql）の後に実行すること。
--     M024 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('calc_correlation_ranking', 'each_team_calc_correlation_ranking',
                        'card_calc_correlation_ranking')
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 相関係数が計算できた（NULL でない）項目だけを順位付けする。
--   rank_no     : 相関係数の大きい順（得点と一緒に増える項目が上位）
--   abs_rank_no : 相関の強さ（絶対値）の大きい順（負の相関も含めて、得点と関係が強い項目が上位）
--   同じ値は同順位（RANK）。件数が少ない項目は pair_cnt / goal_cnt / match_cnt で除外すること。

-- リーグ単位
CREATE VIEW calc_correlation_ranking AS
SELECT
  season, country, league,
  situation, chk_body, feature, feature_order, ha,
  pearson, pair_cnt, goal_cnt, match_cnt,
  RANK() OVER (PARTITION BY season, country, league, situation, chk_body, ha ORDER BY pearson DESC)      AS rank_no,
  RANK() OVER (PARTITION BY season, country, league, situation, chk_body, ha ORDER BY abs(pearson) DESC) AS abs_rank_no
FROM calc_correlation_stats
WHERE pearson IS NOT NULL;

-- チーム単位（ha = H / A / *合算）
CREATE VIEW each_team_calc_correlation_ranking AS
SELECT
  season, country, league, team,
  situation, chk_body, feature, feature_order, ha,
  pearson, pair_cnt, goal_cnt, match_cnt,
  RANK() OVER (PARTITION BY season, country, league, team, situation, chk_body, ha ORDER BY pearson DESC)      AS rank_no,
  RANK() OVER (PARTITION BY season, country, league, team, situation, chk_body, ha ORDER BY abs(pearson) DESC) AS abs_rank_no
FROM each_team_calc_correlation_stats
WHERE pearson IS NOT NULL;

-- カード単位
CREATE VIEW card_calc_correlation_ranking AS
SELECT
  season, country, league, team, opponent,
  situation, chk_body, feature, feature_order, ha,
  pearson, pair_cnt, goal_cnt, match_cnt,
  RANK() OVER (PARTITION BY season, country, league, team, opponent, situation, chk_body, ha ORDER BY pearson DESC)      AS rank_no,
  RANK() OVER (PARTITION BY season, country, league, team, opponent, situation, chk_body, ha ORDER BY abs(pearson) DESC) AS abs_rank_no
FROM card_calc_correlation_stats
WHERE pearson IS NOT NULL;

COMMIT;

-- ############################################################
-- 16_bm_m027_ranking.sql
-- ############################################################
-- 16: BM_M027 特徴量ランキングビュー（10 の後）
BEGIN;

-- BM_M027 作り直し（特徴量の平均値ランキング）
--  旧: score_based_feature_stats / each_team_score_based_feature_stats の文字列を読み、平均の大きい順に上位 68 件へ
--      「N位」を値の文字列の末尾に足して UPDATE していた（再実行のたびに「,1位,1位…」と増え、元の統計文字列も壊れる）。
--  新: BM_M023 のビュー（score_based_feature_stats / each_team_score_based_feature_stats）を
--      ウィンドウ関数で順位付けするビュー。Java のクラスは不要。
--  ※ BM_M023 の DDL（ddl_score_based_feature_rebuild.sql）の後に実行すること。
--     M023 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('score_based_feature_ranking', 'each_team_score_based_feature_ranking')
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 共通ルール
--   順位は（状況, 区分, 特徴量, ha）ごとに、平均値（ave）で付ける。ave が NULL の行は含めない。
--   rank_no     : ave の大きい順 / asc_rank_no : ave の小さい順（ファウル・被シュートなど「少ないほど良い」項目用）
--   同じ値は同順位（RANK: 1, 1, 3 …。旧実装と同じ付け方）。
--   旧実装の「上位 68 件」は WHERE rank_no <= 68 で絞ること。
--   試合数が少ないと平均が極端になり上位に来やすいので、match_cnt で下限を付けて使うこと（例: match_cnt >= 5）。
--   ※ 下限を付けて絞ると順位に欠番が出る。下限込みの順位が欲しい場合は、読み取り側で RANK() を付け直すこと。
--   シーズンの表記はリーグごとに違う（例: 2025-2026 / 2026）ため、リーグをまたぐ順位は
--   各リーグの最新シーズン（season の最大値）の行だけで付ける。

-- リーグ単位（旧 BM_M027 overall: リーグ同士の比較）
--   最新シーズンの行のみ。ha = H: ホームチームの値 / A: アウェーチームの値。
CREATE VIEW score_based_feature_ranking AS
WITH latest AS (
  SELECT s.*,
         MAX(s.season) OVER (PARTITION BY s.country, s.league) AS latest_season
  FROM score_based_feature_stats s
  WHERE s.ave IS NOT NULL
)
SELECT
  season, country, league, situation, chk_body, feature, feature_order, ha,
  ave, sigma, min, max, cnt, match_cnt,
  RANK() OVER (PARTITION BY situation, chk_body, feature, ha ORDER BY ave DESC) AS rank_no,
  RANK() OVER (PARTITION BY situation, chk_body, feature, ha ORDER BY ave ASC)  AS asc_rank_no,
  COUNT(*) OVER (PARTITION BY situation, chk_body, feature, ha)                 AS rank_total
FROM latest
WHERE season = latest_season;

-- チーム単位（旧 BM_M027 eachTeam。ha = H: ホーム戦 / A: アウェー戦 / *: 合算）
--   rank_in_league 系: 同じリーグ・シーズン内のチーム同士の順位（全シーズン）
--   rank_all 系      : 全リーグのチーム同士の順位（各リーグの最新シーズンの行のみ。それ以外は NULL）
CREATE VIEW each_team_score_based_feature_ranking AS
WITH base AS (
  SELECT s.*,
         (s.season = MAX(s.season) OVER (PARTITION BY s.country, s.league)) AS is_latest
  FROM each_team_score_based_feature_stats s
  WHERE s.ave IS NOT NULL
)
SELECT
  season, country, league, team, ha, situation, chk_body, feature, feature_order,
  ave, sigma, min, max, cnt, match_cnt, is_latest,
  RANK() OVER (PARTITION BY season, country, league, situation, chk_body, feature, ha ORDER BY ave DESC) AS rank_in_league,
  RANK() OVER (PARTITION BY season, country, league, situation, chk_body, feature, ha ORDER BY ave ASC)  AS asc_rank_in_league,
  COUNT(*) OVER (PARTITION BY season, country, league, situation, chk_body, feature, ha)                 AS league_total,
  CASE WHEN is_latest THEN RANK() OVER (PARTITION BY is_latest, situation, chk_body, feature, ha ORDER BY ave DESC) END AS rank_all,
  CASE WHEN is_latest THEN RANK() OVER (PARTITION BY is_latest, situation, chk_body, feature, ha ORDER BY ave ASC)  END AS asc_rank_all,
  CASE WHEN is_latest THEN COUNT(*) OVER (PARTITION BY is_latest, situation, chk_body, feature, ha) END                  AS all_total
FROM base;

COMMIT;

-- ############################################################
-- 17_bm_m029.sql
-- ############################################################
-- 17: BM_M029 リアルタイム差分 real_data_process（既存データは削除）
BEGIN;

-- BM_M029 real_data_process 作り直し（既存データは削除される）
--  1行 = 1試合（match_id）。最新のデータと1つ前のデータの差分（どれだけ増えたか）を上書き保存する。
--  変更点: seq（seq_counter 採番）・season・country・league を追加、差分を数値型に、成功率系は成功数・試行数・区間の成功率に分割、
--          区間（prev_times〜times）と has_previous を追加、time_sort_seconds を削除
--  ※ テーブル名が実際と違う場合は、実際の名前に合わせること（\dt real* で確認）

DROP TABLE IF EXISTS real_data_process CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'real_data_process';

CREATE TABLE real_data_process (
  seq                                         VARCHAR(40) NOT NULL,                       -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season                                      VARCHAR(20) NOT NULL,                       -- country_league_season_master.season_year
  country                                     TEXT NOT NULL,                              -- 国
  league                                      TEXT NOT NULL,                              -- リーグ
  match_id                                    TEXT NOT NULL,                              -- マッチID（1試合1行のキー）
  data_category                               TEXT NOT NULL,                              -- 対戦チームカテゴリ（国: リーグ - ラウンドN）
  game_id                                     TEXT,                                       -- 試合ID
  game_link                                   TEXT,                                       -- 試合リンク
  condition_result_data_seq_id                TEXT,                                       -- 条件分岐結果通番ID（最新側）
  home_team_name                              TEXT NOT NULL,                              -- ホームチーム
  away_team_name                              TEXT NOT NULL,                              -- アウェーチーム
  home_rank                                   TEXT,                                       -- ホーム順位（最新）
  away_rank                                   TEXT,                                       -- アウェー順位（最新）
  has_previous                                BOOLEAN NOT NULL,                           -- 1つ前のデータがあるか（false なら差分は試合開始＝0 からの増加＝最新の累計値）
  prev_times                                  TEXT,                                       -- 区間の開始: 1つ前のデータの試合時間
  times                                       TEXT,                                       -- 区間の終了: 最新の試合時間
  prev_record_time                            TIMESTAMP(0) WITH TIME ZONE,                -- 1つ前のデータの記録時間
  record_time                                 TIMESTAMP(0) WITH TIME ZONE,                -- 最新の記録時間
  home_current_score                          SMALLINT,                                   -- ホーム 現在のスコア（最新）
  away_current_score                          SMALLINT,                                   -- アウェー 現在のスコア（最新）
  home_score                                  SMALLINT,                                   -- ホーム 増加: 得点
  home_exp                                    NUMERIC(6,2),                               -- ホーム 増加: 期待値（xG）
  home_in_goal_exp                            NUMERIC(6,2),                               -- ホーム 増加: 枠内ゴール期待値
  home_donation                               NUMERIC(5,1),                               -- ホーム 増加: ポゼッション（% の増減ポイント）
  home_shoot_all                              INTEGER,                                    -- ホーム 増加: シュート数
  home_shoot_in                               INTEGER,                                    -- ホーム 増加: 枠内シュート
  home_shoot_out                              INTEGER,                                    -- ホーム 増加: 枠外シュート
  home_block_shoot                            INTEGER,                                    -- ホーム 増加: ブロックシュート
  home_big_chance                             INTEGER,                                    -- ホーム 増加: ビッグチャンス
  home_corner                                 INTEGER,                                    -- ホーム 増加: コーナーキック
  home_box_shoot_in                           INTEGER,                                    -- ホーム 増加: ボックス内シュート
  home_box_shoot_out                          INTEGER,                                    -- ホーム 増加: ボックス外シュート
  home_goal_post                              INTEGER,                                    -- ホーム 増加: ゴールポスト
  home_goal_head                              INTEGER,                                    -- ホーム 増加: ヘディングゴール
  home_keeper_save                            INTEGER,                                    -- ホーム 増加: キーパーセーブ
  home_free_kick                              INTEGER,                                    -- ホーム 増加: フリーキック
  home_offside                                INTEGER,                                    -- ホーム 増加: オフサイド
  home_foul                                   INTEGER,                                    -- ホーム 増加: ファウル
  home_yellow_card                            INTEGER,                                    -- ホーム 増加: イエローカード
  home_red_card                               INTEGER,                                    -- ホーム 増加: レッドカード
  home_slow_in                                INTEGER,                                    -- ホーム 増加: スローイン
  home_box_touch                              INTEGER,                                    -- ホーム 増加: ボックスタッチ
  home_clear_count                            INTEGER,                                    -- ホーム 増加: クリア数
  home_duel_count                             INTEGER,                                    -- ホーム 増加: デュエル数
  home_intercept_count                        INTEGER,                                    -- ホーム 増加: インターセプト数
  home_pass_count_success                     INTEGER,                                    -- ホーム 増加: パス 成功数
  home_pass_count_try                         INTEGER,                                    -- ホーム 増加: パス 試行数
  home_pass_count_rate                        NUMERIC(5,1),                               -- ホーム 増加: パス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  home_long_pass_count_success                INTEGER,                                    -- ホーム 増加: ロングパス 成功数
  home_long_pass_count_try                    INTEGER,                                    -- ホーム 増加: ロングパス 試行数
  home_long_pass_count_rate                   NUMERIC(5,1),                               -- ホーム 増加: ロングパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  home_final_third_pass_count_success         INTEGER,                                    -- ホーム 増加: ファイナルサードパス 成功数
  home_final_third_pass_count_try             INTEGER,                                    -- ホーム 増加: ファイナルサードパス 試行数
  home_final_third_pass_count_rate            NUMERIC(5,1),                               -- ホーム 増加: ファイナルサードパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  home_cross_count_success                    INTEGER,                                    -- ホーム 増加: クロス 成功数
  home_cross_count_try                        INTEGER,                                    -- ホーム 増加: クロス 試行数
  home_cross_count_rate                       NUMERIC(5,1),                               -- ホーム 増加: クロス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  home_tackle_count_success                   INTEGER,                                    -- ホーム 増加: タックル 成功数
  home_tackle_count_try                       INTEGER,                                    -- ホーム 増加: タックル 試行数
  home_tackle_count_rate                      NUMERIC(5,1),                               -- ホーム 増加: タックル 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  away_score                                  SMALLINT,                                   -- アウェー 増加: 得点
  away_exp                                    NUMERIC(6,2),                               -- アウェー 増加: 期待値（xG）
  away_in_goal_exp                            NUMERIC(6,2),                               -- アウェー 増加: 枠内ゴール期待値
  away_donation                               NUMERIC(5,1),                               -- アウェー 増加: ポゼッション（% の増減ポイント）
  away_shoot_all                              INTEGER,                                    -- アウェー 増加: シュート数
  away_shoot_in                               INTEGER,                                    -- アウェー 増加: 枠内シュート
  away_shoot_out                              INTEGER,                                    -- アウェー 増加: 枠外シュート
  away_block_shoot                            INTEGER,                                    -- アウェー 増加: ブロックシュート
  away_big_chance                             INTEGER,                                    -- アウェー 増加: ビッグチャンス
  away_corner                                 INTEGER,                                    -- アウェー 増加: コーナーキック
  away_box_shoot_in                           INTEGER,                                    -- アウェー 増加: ボックス内シュート
  away_box_shoot_out                          INTEGER,                                    -- アウェー 増加: ボックス外シュート
  away_goal_post                              INTEGER,                                    -- アウェー 増加: ゴールポスト
  away_goal_head                              INTEGER,                                    -- アウェー 増加: ヘディングゴール
  away_keeper_save                            INTEGER,                                    -- アウェー 増加: キーパーセーブ
  away_free_kick                              INTEGER,                                    -- アウェー 増加: フリーキック
  away_offside                                INTEGER,                                    -- アウェー 増加: オフサイド
  away_foul                                   INTEGER,                                    -- アウェー 増加: ファウル
  away_yellow_card                            INTEGER,                                    -- アウェー 増加: イエローカード
  away_red_card                               INTEGER,                                    -- アウェー 増加: レッドカード
  away_slow_in                                INTEGER,                                    -- アウェー 増加: スローイン
  away_box_touch                              INTEGER,                                    -- アウェー 増加: ボックスタッチ
  away_clear_count                            INTEGER,                                    -- アウェー 増加: クリア数
  away_duel_count                             INTEGER,                                    -- アウェー 増加: デュエル数
  away_intercept_count                        INTEGER,                                    -- アウェー 増加: インターセプト数
  away_pass_count_success                     INTEGER,                                    -- アウェー 増加: パス 成功数
  away_pass_count_try                         INTEGER,                                    -- アウェー 増加: パス 試行数
  away_pass_count_rate                        NUMERIC(5,1),                               -- アウェー 増加: パス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  away_long_pass_count_success                INTEGER,                                    -- アウェー 増加: ロングパス 成功数
  away_long_pass_count_try                    INTEGER,                                    -- アウェー 増加: ロングパス 試行数
  away_long_pass_count_rate                   NUMERIC(5,1),                               -- アウェー 増加: ロングパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  away_final_third_pass_count_success         INTEGER,                                    -- アウェー 増加: ファイナルサードパス 成功数
  away_final_third_pass_count_try             INTEGER,                                    -- アウェー 増加: ファイナルサードパス 試行数
  away_final_third_pass_count_rate            NUMERIC(5,1),                               -- アウェー 増加: ファイナルサードパス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  away_cross_count_success                    INTEGER,                                    -- アウェー 増加: クロス 成功数
  away_cross_count_try                        INTEGER,                                    -- アウェー 増加: クロス 試行数
  away_cross_count_rate                       NUMERIC(5,1),                               -- アウェー 増加: クロス 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  away_tackle_count_success                   INTEGER,                                    -- アウェー 増加: タックル 成功数
  away_tackle_count_try                       INTEGER,                                    -- アウェー 増加: タックル 試行数
  away_tackle_count_rate                      NUMERIC(5,1),                               -- アウェー 増加: タックル 区間の成功率（%。成功数の増加 ÷ 試行数の増加。試行数の増加が 0 以下なら NULL）
  probablity_diff                             NUMERIC(7,2),                               -- 確率の増減（数値が読めた場合）
  probablity                                  TEXT,                                       -- 確率（最新）
  prediction_score_time                       TEXT,                                       -- スコア予想時間（最新）
  weather                                     TEXT,                                       -- 天気（最新）
  temperature                                 TEXT,                                       -- 気温（最新）
  humid                                       TEXT,                                       -- 湿度（最新）
  judge_member                                TEXT,                                       -- 審判（最新）
  home_manager                                TEXT,                                       -- ホーム監督（最新）
  away_manager                                TEXT,                                       -- アウェー監督（最新）
  home_formation                              TEXT,                                       -- ホームフォーメーション（最新）
  away_formation                              TEXT,                                       -- アウェーフォーメーション（最新）
  studium                                     TEXT,                                       -- スタジアム（最新）
  capacity                                    TEXT,                                       -- 収容人数（最新）
  audience                                    TEXT,                                       -- 観客数（最新）
  location                                    TEXT,                                       -- 開催場所（最新）
  home_max_getting_scorer                     TEXT,                                       -- ホームチーム最大得点者（最新）
  away_max_getting_scorer                     TEXT,                                       -- アウェーチーム最大得点者（最新）
  home_max_getting_scorer_game_situation      TEXT,                                       -- ホームチーム最大得点者出場状況（最新）
  away_max_getting_scorer_game_situation      TEXT,                                       -- アウェーチーム最大得点者出場状況（最新）
  home_team_home_score                        TEXT,                                       -- ホームチームホーム得点数（最新）
  home_team_home_lost                         TEXT,                                       -- ホームチームホーム失点数（最新）
  away_team_home_score                        TEXT,                                       -- アウェーチームホーム得点数（最新）
  away_team_home_lost                         TEXT,                                       -- アウェーチームホーム失点数（最新）
  home_team_away_score                        TEXT,                                       -- ホームチームアウェー得点数（最新）
  home_team_away_lost                         TEXT,                                       -- ホームチームアウェー失点数（最新）
  away_team_away_score                        TEXT,                                       -- アウェーチームアウェー得点数（最新）
  away_team_away_lost                         TEXT,                                       -- アウェーチームアウェー失点数（最新）
  notice_flg                                  TEXT,                                       -- 通知フラグ（最新）
  goal_time                                   TEXT,                                       -- ゴール時間（最新）
  goal_team_member                            TEXT,                                       -- ゴール選手名（最新）
  judge                                       TEXT,                                       -- 判定結果（最新）
  home_team_style                             TEXT,                                       -- ホームチームスタイル（最新）
  away_team_style                             TEXT,                                       -- アウェーチームスタイル（最新）
  register_id                                 VARCHAR(100) NOT NULL,
  register_time                               TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                                   VARCHAR(100) NOT NULL,
  update_time                                 TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_real_data_process PRIMARY KEY (seq),
  CONSTRAINT uq_real_data_process UNIQUE (match_id)
);

-- リーグ単位で引く用（試合中の一覧表示など）
CREATE INDEX idx_real_data_process_league ON real_data_process (season, country, league);


COMMIT;

-- ############################################################
-- 18_bm_m030_drop.sql
-- ############################################################
-- 18: BM_M030 廃止（stat_encryption テーブルの削除）
-- BM_M030 廃止（stat_encryption テーブルの削除）
--  旧 BM_M023 / BM_M026 が、特徴量の生の値の履歴を暗号化して保存し、歪度・尖度の計算のたびに全部復号していたテーブル。
--  新しい BM_M023 / M024 は明細（score_based_feature_match_stats / calc_correlation_match_stats）に Σx〜Σx⁴・Σxy を持ち、
--  歪度・尖度・相関係数はビューで計算するため不要。
--  ※ 他のクラスが stat_encryption を使っていないことを確認してから実行すること（データは戻せない）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname = 'stat_encryption'
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 採番の行が作られていれば消す（旧テーブルは id 採番のため通常は無し）
DELETE FROM seq_counter WHERE table_name = 'stat_encryption';

COMMIT;

-- ############################################################
-- 19_bm_m031.sql
-- ############################################################
-- 19: BM_M031 / M032 / M033 チーム表面データ surface_overview_match ＋ ビュー（既存データは削除）
BEGIN;

-- BM_M031 surface_overview 作り直し（既存データは削除される）
--  旧: surface_overview（国 × リーグ × 年 × 月 × チーム）に、試合が届くたびに「前回の値 ＋ 今回の1試合」を足し込んでいた。
--      → 同じ試合の再送で二重加算、古い試合を後から入れると連続記録（無敗・得点継続など）が壊れる。
--  新: 1行 = チーム × 1試合 の明細 surface_overview_match を UPSERT し、
--      月別の成績・連続記録・表示文言・順位はすべてビューで明細から計算する。
--      欠けていた試合を後から入れると、その試合より後の連続記録・順位も自動で正しくなる。
--  旧 BM_M032（surface_overview_process 差分テーブル）もビュー surface_overview_process に置き換える。
--  ※ 連続記録（連勝・連敗・無敗・得点継続）はラウンド番号の順。ラウンドが欠けていると連続は途切れる（埋まればつながる）。

-- テーブル・ビューのどちらでも消せるように、種類を見て DROP する
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('surface_overview', 'surface_overview_season', 'surface_overview_standing',
                        'surface_overview_match_state', 'surface_overview_process')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname DESC
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DROP TABLE IF EXISTS surface_overview_match CASCADE;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name IN ('surface_overview', 'surface_overview_match');

-- ===== 明細: 1行 = チーム × 1試合（1試合でホーム視点・アウェー視点の2行）=====
CREATE TABLE surface_overview_match (
  seq                 VARCHAR(40)  NOT NULL,                 -- <シーズン>-<6桁枝番>（seq_counter で採番）
  season              VARCHAR(20)  NOT NULL,                 -- country_league_season_master.season_year
  country             TEXT         NOT NULL,                 -- 国
  league              TEXT         NOT NULL,                 -- リーグ
  team                TEXT         NOT NULL,                 -- チーム（この行の視点）
  opponent            TEXT         NOT NULL,                 -- 対戦相手
  ha                  CHAR(1)      NOT NULL,                 -- H: ホーム / A: アウェー
  match_id            TEXT,                                  -- 参照用
  round_no            SMALLINT     NOT NULL,                 -- ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別するため一意キーに含む）
  total_rounds        SMALLINT,                              -- そのシーズンの総ラウンド数（マスタ。無ければ NULL）
  phase               VARCHAR(5),                            -- FIRST: 序盤 / MID: 中盤 / LAST: 終盤（総ラウンド数の 1/3 ずつ。総ラウンド数が無ければ NULL）
  match_time          TIMESTAMP(0) WITH TIME ZONE,           -- 試合終了行の記録時間
  game_year           SMALLINT,                              -- 試合の年（記録時間から。取れなければ NULL＝月別ビューには入らない）
  game_month          SMALLINT,                              -- 試合の月
  result              CHAR(1)      NOT NULL,                 -- W: 勝ち / D: 引分 / L: 負け（PK 決着は PK の勝敗）
  pk_flg              BOOLEAN      NOT NULL DEFAULT FALSE,   -- PK 決着の試合
  points              SMALLINT     NOT NULL,                 -- この試合で得た勝ち点（point_setting_master。PK勝ち/負けの設定も反映）
  goals_for           SMALLINT     NOT NULL,                 -- 得点（PK 戦の得点は含めない）
  goals_against       SMALLINT     NOT NULL,                 -- 失点
  goals_for_1st       SMALLINT,                              -- 前半得点（ハーフタイムの行が無い試合は NULL）
  goals_for_2nd       SMALLINT,                              -- 後半得点
  goals_against_1st   SMALLINT,                              -- 前半失点
  goals_against_2nd   SMALLINT,                              -- 後半失点
  first_goal          CHAR(1)      NOT NULL,                 -- 先制: T: このチーム / O: 相手 / N: 両者無得点 / U: 不明
  flow_known          BOOLEAN      NOT NULL,                 -- スコアの推移が分かるか（試合終了の行しか無い試合は FALSE。逆転の判定が不完全）
  ever_led            BOOLEAN      NOT NULL,                 -- 試合中にリードした
  ever_trailed        BOOLEAN      NOT NULL,                 -- 試合中にリードされた
  led_1_0             BOOLEAN      NOT NULL,                 -- 1-0（このチーム視点）になった
  led_2_0             BOOLEAN      NOT NULL,                 -- 2-0 になった
  trailed_0_1         BOOLEAN      NOT NULL,                 -- 0-1 になった
  trailed_0_2         BOOLEAN      NOT NULL,                 -- 0-2 になった
  data_category       TEXT,                                  -- 元のキー（「国: リーグ - ラウンドN」。参照・調査用）
  snapshot_count      INTEGER,                               -- 使えたスナップショット数（取得エラー行を除く。少ない試合は前後半・推移の精度が低い）
  pk_goals_for        SMALLINT,                              -- PK 戦の得点（PK 決着でなければ NULL）
  pk_goals_against    SMALLINT,                              -- PK 戦の失点
  team_rank           SMALLINT,                              -- サイト表示の順位（試合終了行の順位。旧 BM_M033 の元データ。取れなければ NULL）
  register_id         VARCHAR(100)                NOT NULL,
  register_time       TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id           VARCHAR(100)                NOT NULL,
  update_time         TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_surface_overview_match PRIMARY KEY (seq),
  CONSTRAINT uq_surface_overview_match UNIQUE (season, country, league, round_no, team, opponent, ha),
  CONSTRAINT ck_surface_overview_match_ha CHECK (ha IN ('H', 'A')),
  CONSTRAINT ck_surface_overview_match_result CHECK (result IN ('W', 'D', 'L')),
  CONSTRAINT ck_surface_overview_match_first CHECK (first_goal IN ('T', 'O', 'N', 'U')),
  CONSTRAINT ck_surface_overview_match_phase CHECK (phase IS NULL OR phase IN ('FIRST', 'MID', 'LAST'))
);

CREATE INDEX idx_surface_overview_match_team  ON surface_overview_match (season, country, league, team, round_no);
CREATE INDEX idx_surface_overview_match_month ON surface_overview_match (country, league, game_year, game_month);

-- ===== 試合ごとの状態（その試合終了時点のシーズン累計・連続記録）=====
-- ラウンド番号のある試合だけ。連続記録はラウンド番号が連続している間だけ数える（欠けていれば途切れる）。
-- 表示のしきい値（旧コメントどおり）: 連勝/連敗/無敗/得点継続 3以上、負け込み 4連敗以上、初勝利モチベ 5試合以上未勝利、
--   序盤/中盤/終盤好調 その期間 3試合以上で勝率 7割以上、ホーム/アウェー逆境 勝ちのうち逆転勝ちが 3割以上。
CREATE VIEW surface_overview_match_state AS
WITH b AS (
  SELECT m.*,
         (m.result = 'W')                  AS is_w,
         (m.result = 'L')                  AS is_l,
         (m.result <> 'L')                 AS is_u,
         (m.goals_for > 0)                 AS is_s,
         (m.result = 'W' AND m.ever_trailed) AS is_cb_win
  FROM surface_overview_match m
  WHERE m.round_no IS NOT NULL
),
k AS (
  SELECT b.*,
         round_no - ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_w ORDER BY round_no) AS g_w,
         round_no - ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_l ORDER BY round_no) AS g_l,
         round_no - ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_u ORDER BY round_no) AS g_u,
         round_no - ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_s ORDER BY round_no) AS g_s
  FROM b
),
s AS (
  SELECT k.*,
    CASE WHEN is_w THEN ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_w, g_w ORDER BY round_no) ELSE 0 END AS win_streak,
    CASE WHEN is_l THEN ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_l, g_l ORDER BY round_no) ELSE 0 END AS lose_streak,
    CASE WHEN is_u THEN ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_u, g_u ORDER BY round_no) ELSE 0 END AS unbeaten_streak,
    CASE WHEN is_s THEN ROW_NUMBER() OVER (PARTITION BY season, country, league, team, is_s, g_s ORDER BY round_no) ELSE 0 END AS score_streak,
    COUNT(*)                                        OVER w AS season_games,
    COUNT(*) FILTER (WHERE is_w)                    OVER w AS season_win,
    COUNT(*) FILTER (WHERE result = 'D')            OVER w AS season_draw,
    COUNT(*) FILTER (WHERE is_l)                    OVER w AS season_lose,
    SUM(points)                                     OVER w AS season_points,
    SUM(goals_for)                                  OVER w AS season_goals_for,
    SUM(goals_against)                              OVER w AS season_goals_against,
    COUNT(*) FILTER (WHERE phase = 'FIRST')         OVER w AS first_games,
    COUNT(*) FILTER (WHERE phase = 'FIRST' AND is_w) OVER w AS first_win,
    COUNT(*) FILTER (WHERE phase = 'MID')           OVER w AS mid_games,
    COUNT(*) FILTER (WHERE phase = 'MID' AND is_w)  OVER w AS mid_win,
    COUNT(*) FILTER (WHERE phase = 'LAST')          OVER w AS last_games,
    COUNT(*) FILTER (WHERE phase = 'LAST' AND is_w) OVER w AS last_win,
    COUNT(*) FILTER (WHERE ha = 'H' AND is_w)       OVER w AS home_win,
    COUNT(*) FILTER (WHERE ha = 'H' AND is_cb_win)  OVER w AS home_comeback_win,
    COUNT(*) FILTER (WHERE ha = 'A' AND is_w)       OVER w AS away_win,
    COUNT(*) FILTER (WHERE ha = 'A' AND is_cb_win)  OVER w AS away_comeback_win
  FROM k
  WINDOW w AS (PARTITION BY season, country, league, team ORDER BY round_no ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
)
SELECT
  seq, season, country, league, team, opponent, ha, match_id, round_no, total_rounds, phase, match_time,
  game_year, game_month, result, pk_flg, points, goals_for, goals_against,
  win_streak::INTEGER       AS consecutive_win_count,
  CASE WHEN win_streak >= 3 THEN win_streak || '連勝中' END                   AS consecutive_win_disp,
  lose_streak::INTEGER      AS consecutive_lose_count,
  CASE WHEN lose_streak >= 3 THEN lose_streak || '連敗中' END                 AS consecutive_lose_disp,
  CASE WHEN lose_streak >= 4 THEN '負け込み' END                              AS lose_streak_disp,
  unbeaten_streak::INTEGER  AS unbeaten_streak_count,
  CASE WHEN unbeaten_streak >= 3 THEN '無敗継続中' END                        AS unbeaten_streak_disp,
  score_streak::INTEGER     AS consecutive_score_count,
  CASE WHEN score_streak >= 3 THEN '得点試合継続中' END                       AS consecutive_score_count_disp,
  season_games::INTEGER, season_win::INTEGER, season_draw::INTEGER, season_lose::INTEGER,
  season_points::INTEGER, season_goals_for::INTEGER, season_goals_against::INTEGER,
  (season_goals_for - season_goals_against)::INTEGER AS season_goal_diff,
  CASE WHEN season_win = 0 AND season_games >= 5 THEN '初勝利モチベ' END       AS first_win_disp,
  CASE WHEN first_games >= 3 AND first_win * 10 >= first_games * 7 THEN '序盤好調' END AS first_week_game_win_disp,
  CASE WHEN mid_games   >= 3 AND mid_win   * 10 >= mid_games   * 7 THEN '中盤好調' END AS mid_week_game_win_disp,
  CASE WHEN last_games  >= 3 AND last_win  * 10 >= last_games  * 7 THEN '終盤好調' END AS last_week_game_win_disp,
  CASE WHEN home_win > 0 AND home_comeback_win * 10 >= home_win * 3 THEN 'ホーム逆境' END AS home_adversity_disp,
  CASE WHEN away_win > 0 AND away_comeback_win * 10 >= away_win * 3 THEN 'アウェー逆境' END AS away_adversity_disp
FROM s;

-- ===== 月 × チーム（旧 surface_overview と同じ単位）=====
--  月の成績: その月の試合だけで集計（ラウンド番号が無い試合も含む。記録時間が無い試合は入らない）。
--  as_of_* / season_* / 連続記録・表示: その月の最後の試合（ラウンド番号が最大の試合）終了時点のシーズン状態。
--  得点・失点の割合: 前半/後半が分かる試合だけで計算（前半 ÷ (前半 + 後半) × 100、四捨五入。分母 0 は 0）。
--  逆転: チーム視点。home_win_behind_0vs1_count = ホーム戦で 0-1 から勝った数、away_win_behind_0vs1_count = アウェー戦で 0-1 から勝った数
--        （旧 away_win_behind_1vs0 はスコア表記がホーム基準だったが、チーム視点にそろえた）。
CREATE VIEW surface_overview AS
WITH m AS (
  SELECT season, country, league, team, game_year, game_month,
    COUNT(*)::INTEGER                                   AS games,
    COUNT(*) FILTER (WHERE result = 'W')::INTEGER       AS win,
    COUNT(*) FILTER (WHERE result = 'L')::INTEGER       AS lose,
    COUNT(*) FILTER (WHERE result = 'D')::INTEGER       AS draw,
    SUM(points)::INTEGER                                AS winning_points,
    COALESCE(SUM(goals_for_1st)     FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_1st_half_score,
    COALESCE(SUM(goals_for_2nd)     FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_2nd_half_score,
    COALESCE(SUM(goals_for)         FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_sum_score,
    COUNT(*) FILTER (WHERE ha = 'H' AND goals_against = 0)::INTEGER      AS home_clean_sheet,
    COALESCE(SUM(goals_for_1st)     FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_1st_half_score,
    COALESCE(SUM(goals_for_2nd)     FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_2nd_half_score,
    COALESCE(SUM(goals_for)         FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_sum_score,
    COUNT(*) FILTER (WHERE ha = 'A' AND goals_against = 0)::INTEGER      AS away_clean_sheet,
    COALESCE(SUM(goals_against_1st) FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_1st_half_lost,
    COALESCE(SUM(goals_against_2nd) FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_2nd_half_lost,
    COALESCE(SUM(goals_against)     FILTER (WHERE ha = 'H'), 0)::INTEGER AS home_sum_lost,
    COALESCE(SUM(goals_against_1st) FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_1st_half_lost,
    COALESCE(SUM(goals_against_2nd) FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_2nd_half_lost,
    COALESCE(SUM(goals_against)     FILTER (WHERE ha = 'A'), 0)::INTEGER AS away_sum_lost,
    COUNT(*) FILTER (WHERE goals_for = 0)::INTEGER                       AS fail_to_score_game_count,
    COUNT(*) FILTER (WHERE phase = 'FIRST' AND result = 'W')::INTEGER    AS first_week_game_win_count,
    COUNT(*) FILTER (WHERE phase = 'FIRST' AND result = 'L')::INTEGER    AS first_week_game_lost_count,
    COUNT(*) FILTER (WHERE phase = 'MID'   AND result = 'W')::INTEGER    AS mid_week_game_win_count,
    COUNT(*) FILTER (WHERE phase = 'MID'   AND result = 'L')::INTEGER    AS mid_week_game_lost_count,
    COUNT(*) FILTER (WHERE phase = 'LAST'  AND result = 'W')::INTEGER    AS last_week_game_win_count,
    COUNT(*) FILTER (WHERE phase = 'LAST'  AND result = 'L')::INTEGER    AS last_week_game_lost_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'W')::INTEGER           AS home_win_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'L')::INTEGER           AS home_lose_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND first_goal = 'T')::INTEGER       AS home_first_goal_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'W' AND ever_trailed)::INTEGER AS home_win_behind_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'L' AND ever_led)::INTEGER     AS home_lose_behind_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'W' AND ever_trailed AND NOT trailed_0_2 AND trailed_0_1)::INTEGER AS home_win_behind_0vs1_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'L' AND ever_led AND NOT led_2_0 AND led_1_0)::INTEGER             AS home_lose_behind_1vs0_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'W' AND ever_trailed AND trailed_0_2)::INTEGER                     AS home_win_behind_0vs2_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'L' AND ever_led AND led_2_0)::INTEGER                             AS home_lose_behind_2vs0_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'W' AND ever_trailed AND NOT trailed_0_1 AND NOT trailed_0_2)::INTEGER AS home_win_behind_other_count,
    COUNT(*) FILTER (WHERE ha = 'H' AND result = 'L' AND ever_led AND NOT led_1_0 AND NOT led_2_0)::INTEGER         AS home_lose_behind_other_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'W')::INTEGER           AS away_win_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'L')::INTEGER           AS away_lose_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND first_goal = 'T')::INTEGER       AS away_first_goal_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'W' AND ever_trailed)::INTEGER AS away_win_behind_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'L' AND ever_led)::INTEGER     AS away_lose_behind_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'W' AND ever_trailed AND NOT trailed_0_2 AND trailed_0_1)::INTEGER AS away_win_behind_0vs1_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'L' AND ever_led AND NOT led_2_0 AND led_1_0)::INTEGER             AS away_lose_behind_1vs0_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'W' AND ever_trailed AND trailed_0_2)::INTEGER                     AS away_win_behind_0vs2_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'L' AND ever_led AND led_2_0)::INTEGER                             AS away_lose_behind_2vs0_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'W' AND ever_trailed AND NOT trailed_0_1 AND NOT trailed_0_2)::INTEGER AS away_win_behind_other_count,
    COUNT(*) FILTER (WHERE ha = 'A' AND result = 'L' AND ever_led AND NOT led_1_0 AND NOT led_2_0)::INTEGER         AS away_lose_behind_other_count,
    MAX(round_no)                                                        AS as_of_round
  FROM surface_overview_match
  WHERE game_year IS NOT NULL AND game_month IS NOT NULL
  GROUP BY season, country, league, team, game_year, game_month
),
pct AS (
  SELECT m.*,
    CASE WHEN home_1st_half_score + home_2nd_half_score > 0 THEN ROUND(home_1st_half_score * 100.0 / (home_1st_half_score + home_2nd_half_score)) ELSE 0 END::INTEGER AS home_1st_half_score_ratio,
    CASE WHEN home_1st_half_score + home_2nd_half_score > 0 THEN ROUND(home_2nd_half_score * 100.0 / (home_1st_half_score + home_2nd_half_score)) ELSE 0 END::INTEGER AS home_2nd_half_score_ratio,
    CASE WHEN away_1st_half_score + away_2nd_half_score > 0 THEN ROUND(away_1st_half_score * 100.0 / (away_1st_half_score + away_2nd_half_score)) ELSE 0 END::INTEGER AS away_1st_half_score_ratio,
    CASE WHEN away_1st_half_score + away_2nd_half_score > 0 THEN ROUND(away_2nd_half_score * 100.0 / (away_1st_half_score + away_2nd_half_score)) ELSE 0 END::INTEGER AS away_2nd_half_score_ratio,
    CASE WHEN home_1st_half_lost + home_2nd_half_lost > 0 THEN ROUND(home_1st_half_lost * 100.0 / (home_1st_half_lost + home_2nd_half_lost)) ELSE 0 END::INTEGER AS home_1st_half_lost_ratio,
    CASE WHEN home_1st_half_lost + home_2nd_half_lost > 0 THEN ROUND(home_2nd_half_lost * 100.0 / (home_1st_half_lost + home_2nd_half_lost)) ELSE 0 END::INTEGER AS home_2nd_half_lost_ratio,
    CASE WHEN away_1st_half_lost + away_2nd_half_lost > 0 THEN ROUND(away_1st_half_lost * 100.0 / (away_1st_half_lost + away_2nd_half_lost)) ELSE 0 END::INTEGER AS away_1st_half_lost_ratio,
    CASE WHEN away_1st_half_lost + away_2nd_half_lost > 0 THEN ROUND(away_2nd_half_lost * 100.0 / (away_1st_half_lost + away_2nd_half_lost)) ELSE 0 END::INTEGER AS away_2nd_half_lost_ratio
  FROM m
)
SELECT
  p.season, p.country, p.league, p.game_year, p.game_month, p.team,
  p.games, p.win, p.lose, p.draw, p.winning_points,
  p.home_1st_half_score, p.home_2nd_half_score, p.home_sum_score, p.home_1st_half_score_ratio, p.home_2nd_half_score_ratio, p.home_clean_sheet,
  p.away_1st_half_score, p.away_2nd_half_score, p.away_sum_score, p.away_1st_half_score_ratio, p.away_2nd_half_score_ratio, p.away_clean_sheet,
  p.home_1st_half_lost, p.home_2nd_half_lost, p.home_sum_lost, p.home_1st_half_lost_ratio, p.home_2nd_half_lost_ratio,
  p.away_1st_half_lost, p.away_2nd_half_lost, p.away_sum_lost, p.away_1st_half_lost_ratio, p.away_2nd_half_lost_ratio,
  p.fail_to_score_game_count,
  p.first_week_game_win_count, p.first_week_game_lost_count, p.mid_week_game_win_count, p.mid_week_game_lost_count,
  p.last_week_game_win_count, p.last_week_game_lost_count,
  p.home_win_count, p.home_lose_count, p.home_first_goal_count, p.home_win_behind_count, p.home_lose_behind_count,
  p.home_win_behind_0vs1_count, p.home_lose_behind_1vs0_count, p.home_win_behind_0vs2_count, p.home_lose_behind_2vs0_count,
  p.home_win_behind_other_count, p.home_lose_behind_other_count,
  p.away_win_count, p.away_lose_count, p.away_first_goal_count, p.away_win_behind_count, p.away_lose_behind_count,
  p.away_win_behind_0vs1_count, p.away_lose_behind_1vs0_count, p.away_win_behind_0vs2_count, p.away_lose_behind_2vs0_count,
  p.away_win_behind_other_count, p.away_lose_behind_other_count,
  -- その月の最後の試合終了時点の状態（その月にラウンド番号のある試合が無ければ NULL）
  p.as_of_round,
  st.season_games, st.season_win, st.season_draw, st.season_lose, st.season_points,
  st.season_goals_for, st.season_goals_against, st.season_goal_diff,
  st.consecutive_win_count, st.consecutive_win_disp,
  st.consecutive_lose_count, st.consecutive_lose_disp, st.lose_streak_disp,
  st.unbeaten_streak_count, st.unbeaten_streak_disp,
  st.consecutive_score_count, st.consecutive_score_count_disp,
  st.first_win_disp,
  st.first_week_game_win_disp, st.mid_week_game_win_disp, st.last_week_game_win_disp,
  st.home_adversity_disp, st.away_adversity_disp
FROM pct p
LEFT JOIN surface_overview_match_state st
  ON st.season = p.season AND st.country = p.country AND st.league = p.league
 AND st.team = p.team AND st.round_no = p.as_of_round;

-- ===== シーズン × チームの最新状態（最後のラウンド終了時点）=====
CREATE VIEW surface_overview_season AS
SELECT DISTINCT ON (season, country, league, team)
  season, country, league, team,
  round_no AS as_of_round, match_time AS last_match_time,
  season_games, season_win, season_draw, season_lose, season_points,
  season_goals_for, season_goals_against, season_goal_diff,
  consecutive_win_count, consecutive_win_disp,
  consecutive_lose_count, consecutive_lose_disp, lose_streak_disp,
  unbeaten_streak_count, unbeaten_streak_disp,
  consecutive_score_count, consecutive_score_count_disp,
  first_win_disp, first_week_game_win_disp, mid_week_game_win_disp, last_week_game_win_disp,
  home_adversity_disp, away_adversity_disp
FROM surface_overview_match_state
ORDER BY season, country, league, team, round_no DESC;

-- ===== ラウンド N 終了時点の順位（旧 BM_M028 過去順位・旧 BM_M033 順位履歴の置き換え）=====
-- as_of_round = N: round_no <= N の試合だけで集計。rank_no は 勝ち点 → 得失点差 → 得点 の順（同じなら同順位）。
-- site_rank: そのチームのラウンド N の試合の、サイト表示の順位（試合データの順位。そのラウンドに試合が無い・取れなければ NULL）。
-- display_rank: site_rank があればそれ、無ければ rank_no（旧 BM_M033 と同じ「サイトの順位を優先し、無ければ計算で補う」）。
-- 欠けていた試合を後から入れると、その試合以降の全ラウンドの rank_no が自動で変わる。
CREATE VIEW surface_overview_standing AS
WITH rounds AS (
  SELECT DISTINCT season, country, league, round_no
  FROM surface_overview_match
  WHERE round_no IS NOT NULL
),
agg AS (
  SELECT r.round_no AS as_of_round, m.season, m.country, m.league, m.team,
    COUNT(*)::INTEGER                                AS games,
    COUNT(*) FILTER (WHERE m.result = 'W')::INTEGER  AS win,
    COUNT(*) FILTER (WHERE m.result = 'D')::INTEGER  AS draw,
    COUNT(*) FILTER (WHERE m.result = 'L')::INTEGER  AS lose,
    SUM(m.points)::INTEGER                           AS winning_points,
    SUM(m.goals_for)::INTEGER                        AS goals_for,
    SUM(m.goals_against)::INTEGER                    AS goals_against
  FROM rounds r
  JOIN surface_overview_match m
    ON m.season = r.season AND m.country = r.country AND m.league = r.league
   AND m.round_no <= r.round_no
  GROUP BY r.round_no, m.season, m.country, m.league, m.team
)
SELECT a.*,
  (a.goals_for - a.goals_against) AS goal_diff,
  RANK() OVER (PARTITION BY a.season, a.country, a.league, a.as_of_round
               ORDER BY a.winning_points DESC, (a.goals_for - a.goals_against) DESC, a.goals_for DESC) AS rank_no,
  sr.site_rank,
  COALESCE(sr.site_rank,
           RANK() OVER (PARTITION BY a.season, a.country, a.league, a.as_of_round
                        ORDER BY a.winning_points DESC, (a.goals_for - a.goals_against) DESC, a.goals_for DESC)) AS display_rank
FROM agg a
LEFT JOIN LATERAL (
  SELECT MIN(m.team_rank)::INTEGER AS site_rank
  FROM surface_overview_match m
  WHERE m.season = a.season AND m.country = a.country AND m.league = a.league
    AND m.team = a.team AND m.round_no = a.as_of_round
) sr ON TRUE;

-- ===== 直前ラウンドからの差分（旧 BM_M032 surface_overview_process の置き換え）=====
-- 1行 = チーム × 1試合（ラウンド番号のある試合）。*_diff はその試合で増えた分（＝明細そのもの）。
-- previous_round_no: そのチームの直前のラウンド（明細にある中で）。adjacent_flg = TRUE は直前ラウンドとつながっている
--   （round_gap = 1）。旧 M032 は隣接しない場合は作らなかったが、ここでは全行出して adjacent_flg で絞れるようにした。
-- before_* / after_*: 直前ラウンド終了時点 / この試合終了時点のシーズン累計・連続記録・順位。
-- 欠けていた試合を後から入れると、その前後の行の previous_round_no・before_*・順位も自動で変わる。
-- 旧 M032 の「1時間で削除」は不要（ビューなので保存しない）。
CREATE VIEW surface_overview_process AS
WITH x AS (
  SELECT m.*,
    st.season_games, st.season_win, st.season_draw, st.season_lose, st.season_points, st.season_goal_diff,
    st.consecutive_win_count, st.consecutive_lose_count, st.unbeaten_streak_count, st.consecutive_score_count,
    sd.rank_no
  FROM surface_overview_match m
  JOIN surface_overview_match_state st ON st.seq = m.seq
  LEFT JOIN surface_overview_standing sd
    ON sd.season = m.season AND sd.country = m.country AND sd.league = m.league
   AND sd.team = m.team AND sd.as_of_round = m.round_no
)
SELECT
  season, country, league, game_year, game_month, team, opponent, ha, match_id, match_time,
  LAG(round_no) OVER w                                AS previous_round_no,
  round_no                                            AS current_round_no,
  (round_no - LAG(round_no) OVER w)                   AS round_gap,
  COALESCE(round_no - LAG(round_no) OVER w = 1, FALSE) AS adjacent_flg,
  1                                                   AS games_diff,
  (result = 'W')::INTEGER                             AS win_diff,
  (result = 'L')::INTEGER                             AS lose_diff,
  (result = 'D')::INTEGER                             AS draw_diff,
  points                                              AS winning_points_diff,
  CASE WHEN ha = 'H' THEN goals_for_1st     ELSE 0 END AS home_1st_half_score_diff,
  CASE WHEN ha = 'H' THEN goals_for_2nd     ELSE 0 END AS home_2nd_half_score_diff,
  CASE WHEN ha = 'H' THEN goals_for         ELSE 0 END AS home_sum_score_diff,
  CASE WHEN ha = 'A' THEN goals_for_1st     ELSE 0 END AS away_1st_half_score_diff,
  CASE WHEN ha = 'A' THEN goals_for_2nd     ELSE 0 END AS away_2nd_half_score_diff,
  CASE WHEN ha = 'A' THEN goals_for         ELSE 0 END AS away_sum_score_diff,
  CASE WHEN ha = 'H' THEN goals_against_1st ELSE 0 END AS home_1st_half_lost_diff,
  CASE WHEN ha = 'H' THEN goals_against_2nd ELSE 0 END AS home_2nd_half_lost_diff,
  CASE WHEN ha = 'H' THEN goals_against     ELSE 0 END AS home_sum_lost_diff,
  CASE WHEN ha = 'A' THEN goals_against_1st ELSE 0 END AS away_1st_half_lost_diff,
  CASE WHEN ha = 'A' THEN goals_against_2nd ELSE 0 END AS away_2nd_half_lost_diff,
  CASE WHEN ha = 'A' THEN goals_against     ELSE 0 END AS away_sum_lost_diff,
  (ha = 'H' AND goals_against = 0)::INTEGER           AS home_clean_sheet_diff,
  (ha = 'A' AND goals_against = 0)::INTEGER           AS away_clean_sheet_diff,
  (goals_for = 0)::INTEGER                            AS fail_to_score_game_count_diff,
  (phase = 'FIRST' AND result = 'W')::INTEGER         AS first_week_game_win_count_diff,
  (phase = 'FIRST' AND result = 'L')::INTEGER         AS first_week_game_lost_count_diff,
  (phase = 'MID'   AND result = 'W')::INTEGER         AS mid_week_game_win_count_diff,
  (phase = 'MID'   AND result = 'L')::INTEGER         AS mid_week_game_lost_count_diff,
  (phase = 'LAST'  AND result = 'W')::INTEGER         AS last_week_game_win_count_diff,
  (phase = 'LAST'  AND result = 'L')::INTEGER         AS last_week_game_lost_count_diff,
  (ha = 'H' AND result = 'W')::INTEGER                AS home_win_count_diff,
  (ha = 'H' AND result = 'L')::INTEGER                AS home_lose_count_diff,
  (ha = 'H' AND first_goal = 'T')::INTEGER            AS home_first_goal_count_diff,
  (ha = 'H' AND result = 'W' AND ever_trailed)::INTEGER AS home_win_behind_count_diff,
  (ha = 'H' AND result = 'L' AND ever_led)::INTEGER   AS home_lose_behind_count_diff,
  (ha = 'A' AND result = 'W')::INTEGER                AS away_win_count_diff,
  (ha = 'A' AND result = 'L')::INTEGER                AS away_lose_count_diff,
  (ha = 'A' AND first_goal = 'T')::INTEGER            AS away_first_goal_count_diff,
  (ha = 'A' AND result = 'W' AND ever_trailed)::INTEGER AS away_win_behind_count_diff,
  (ha = 'A' AND result = 'L' AND ever_led)::INTEGER   AS away_lose_behind_count_diff,
  LAG(season_games) OVER w          AS before_season_games,          season_games          AS after_season_games,
  LAG(season_win) OVER w            AS before_season_win,            season_win            AS after_season_win,
  LAG(season_draw) OVER w           AS before_season_draw,           season_draw           AS after_season_draw,
  LAG(season_lose) OVER w           AS before_season_lose,           season_lose           AS after_season_lose,
  LAG(season_points) OVER w         AS before_season_points,         season_points         AS after_season_points,
  LAG(season_goal_diff) OVER w      AS before_season_goal_diff,      season_goal_diff      AS after_season_goal_diff,
  LAG(rank_no) OVER w               AS before_rank_no,               rank_no               AS after_rank_no,
  LAG(consecutive_win_count) OVER w   AS before_consecutive_win_count,   consecutive_win_count   AS after_consecutive_win_count,
  LAG(consecutive_lose_count) OVER w  AS before_consecutive_lose_count,  consecutive_lose_count  AS after_consecutive_lose_count,
  LAG(unbeaten_streak_count) OVER w   AS before_unbeaten_streak_count,   unbeaten_streak_count   AS after_unbeaten_streak_count,
  LAG(consecutive_score_count) OVER w AS before_consecutive_score_count, consecutive_score_count AS after_consecutive_score_count
FROM x
WINDOW w AS (PARTITION BY season, country, league, team ORDER BY round_no);


COMMIT;

-- ############################################################
-- 20_bm_m033_drop.sql
-- ############################################################
-- 20: BM_M033 廃止（rank_history テーブルの削除）
-- BM_M033 廃止（rank_history テーブルの削除）
--  旧 BM_M033 が、国・リーグ・シーズン・節・チームごとの順位（サイト表示の順位、無ければ計算した順位）を保存していたテーブル。
--  新しくは surface_overview_match.team_rank（サイト表示の順位）とビュー surface_overview_standing
--  （site_rank / 計算した rank_no / display_rank）で出すため不要。
--  ※ 他のクラスが rank_history を使っていないことを確認してから実行すること（データは戻せない）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname = 'rank_history'
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DELETE FROM seq_counter WHERE table_name = 'rank_history';

COMMIT;

-- ############################################################
-- 21_bm_m034.sql
-- ############################################################
-- 21: BM_M034 試合中スナップショット match_team_snapshot_fact ＋ 差分ビュー（既存データは削除）
BEGIN;

-- BM_M034 match_team_snapshot_fact 作り直し（既存データは削除される）
--  1行 = 1試合 × 1チーム視点 × 1時点（元データ1行 → ホーム視点・アウェー視点の2行）。その時点の累計値。
--  旧: INSERT のみ（再送のたびに重複）・パス系が全部 NULL・xG なし・シーズン=記録時間の年
--  新: (data_seq, ha) で UPSERT。パス系は成功数/試行数/成功率。xG・全項目を追加。シーズン・seq は共通ルール。
--      直前の時点との差分はビュー match_team_snapshot_diff、試合ごとの最新の差分は match_team_snapshot_latest（旧 BM_M029 の置き換え）。

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('match_team_snapshot_last', 'match_team_snapshot_latest', 'match_team_snapshot_diff', 'match_team_snapshot_fact')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'match_team_snapshot_fact';

CREATE TABLE match_team_snapshot_fact (
  seq                               VARCHAR(40) NOT NULL,                    -- seq（<シーズン>-<6桁枝番>。seq_counter で採番）
  season                            VARCHAR(20) NOT NULL,                    -- シーズン（SeasonResolverIF）
  country                           TEXT NOT NULL,                           -- 国
  league                            TEXT NOT NULL,                           -- リーグ
  match_id                          TEXT,                                    -- マッチID（参照用）
  team                              TEXT NOT NULL,                           -- チーム（この行の視点）
  opponent                          TEXT NOT NULL,                           -- 対戦相手
  ha                                CHAR(1) NOT NULL,                        -- H: ホーム / A: アウェー
  data_seq                          BIGINT NOT NULL,                         -- 元データ（data テーブル）の通番。時点の並び順と一意キーに使う
  round_no                          SMALLINT NOT NULL,                       -- ラウンド番号（キーの「ラウンド N」。同じ対戦が複数回あるリーグで試合を区別するため、ビューの区切りに含む）
  half                              SMALLINT,                                -- 1: 前半 / 2: 後半（ハーフタイム行の前後で判定。ハーフタイム行がまだ無ければ null）
  match_time_label                  TEXT,                                    -- 表示上の試合時間（例: 23'、45+2'、ハーフタイム、終了済）
  match_minute                      NUMERIC(6,2),                            -- 試合時間（分）。読めない表記は null（0 分にはしない）
  fin_flg                           BOOLEAN NOT NULL,                        -- 試合終了（FIN）の行か
  record_time                       TIMESTAMP(0) WITH TIME ZONE,             -- 記録時間
  team_score                        SMALLINT,                                -- この時点の自チーム得点
  opponent_score                    SMALLINT,                                -- この時点の相手得点
  score_diff                        SMALLINT,                                -- スコア差（自 − 相手）
  possession                        NUMERIC(5,1),                            -- ポゼッション（%）
  exp                               NUMERIC(6,2),                            -- 期待値（xG）（累計）
  in_goal_exp                       NUMERIC(6,2),                            -- 枠内ゴール期待値（累計）
  shoot_all                         INTEGER,                                 -- シュート数（累計）
  shoot_in                          INTEGER,                                 -- 枠内シュート（累計）
  shoot_out                         INTEGER,                                 -- 枠外シュート（累計）
  block_shoot                       INTEGER,                                 -- ブロックシュート（累計）
  big_chance                        INTEGER,                                 -- ビッグチャンス（累計）
  corner                            INTEGER,                                 -- コーナーキック（累計）
  box_shoot_in                      INTEGER,                                 -- ボックス内シュート（累計）
  box_shoot_out                     INTEGER,                                 -- ボックス外シュート（累計）
  goal_post                         INTEGER,                                 -- ゴールポスト（累計）
  goal_head                         INTEGER,                                 -- ヘディングゴール（累計）
  keeper_save                       INTEGER,                                 -- キーパーセーブ（累計）
  free_kick                         INTEGER,                                 -- フリーキック（累計）
  offside                           INTEGER,                                 -- オフサイド（累計）
  foul                              INTEGER,                                 -- ファウル（累計）
  yellow_card                       INTEGER,                                 -- イエローカード（累計）
  red_card                          INTEGER,                                 -- レッドカード（累計）
  slow_in                           INTEGER,                                 -- スローイン（累計）
  box_touch                         INTEGER,                                 -- ボックスタッチ（累計）
  clear_count                       INTEGER,                                 -- クリア数（累計）
  duel_count                        INTEGER,                                 -- デュエル勝利数（累計）
  intercept_count                   INTEGER,                                 -- インターセプト数（累計）
  pass_count_success                INTEGER,                                 -- パス 成功数（累計）
  pass_count_try                    INTEGER,                                 -- パス 試行数（累計）
  pass_count_rate                   NUMERIC(5,1),                            -- パス 成功率（%。累計）
  long_pass_count_success           INTEGER,                                 -- ロングパス 成功数（累計）
  long_pass_count_try               INTEGER,                                 -- ロングパス 試行数（累計）
  long_pass_count_rate              NUMERIC(5,1),                            -- ロングパス 成功率（%。累計）
  final_third_pass_count_success    INTEGER,                                 -- ファイナルサードパス 成功数（累計）
  final_third_pass_count_try        INTEGER,                                 -- ファイナルサードパス 試行数（累計）
  final_third_pass_count_rate       NUMERIC(5,1),                            -- ファイナルサードパス 成功率（%。累計）
  cross_count_success               INTEGER,                                 -- クロス 成功数（累計）
  cross_count_try                   INTEGER,                                 -- クロス 試行数（累計）
  cross_count_rate                  NUMERIC(5,1),                            -- クロス 成功率（%。累計）
  tackle_count_success              INTEGER,                                 -- タックル 成功数（累計）
  tackle_count_try                  INTEGER,                                 -- タックル 試行数（累計）
  tackle_count_rate                 NUMERIC(5,1),                            -- タックル 成功率（%。累計）
  register_id                       VARCHAR(100) NOT NULL,
  register_time                     TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id                         VARCHAR(100) NOT NULL,
  update_time                       TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_match_team_snapshot_fact PRIMARY KEY (seq),
  CONSTRAINT uq_match_team_snapshot_fact UNIQUE (data_seq, ha),
  CONSTRAINT ck_match_team_snapshot_fact_ha CHECK (ha IN ('H', 'A')),
  CONSTRAINT ck_match_team_snapshot_fact_half CHECK (half IS NULL OR half IN (1, 2))
);

-- 1試合 × チームの時系列を引く用（差分ビューの並び順）
CREATE INDEX idx_mtsf_match ON match_team_snapshot_fact (season, country, league, round_no, team, opponent, ha, data_seq);
CREATE INDEX idx_mtsf_match_id ON match_team_snapshot_fact (match_id);

-- ===== 直前の時点との差分 =====
-- 1行 = 1試合 × チーム × 1時点。*_diff = この時点 − 直前の時点（同じ試合・同じチーム視点の中で data_seq の1つ前）。
-- 最初の時点は prev_* / *_diff が NULL。値が減った（データの訂正など）場合はマイナスのまま。
-- *_rate_interval: その区間の成功率（成功数の増加 ÷ 試行数の増加 × 100）。試行数が増えていない・成功数の増加がマイナスや試行数の増加より多い（データの訂正など）場合は NULL。
-- 欠けていた時点を後から入れると、その前後の差分も自動で変わる。
CREATE VIEW match_team_snapshot_diff AS
SELECT
  seq, season, country, league, match_id, team, opponent, ha, round_no, half,
  data_seq, match_time_label, match_minute, record_time, fin_flg, team_score, opponent_score, score_diff,
  LAG(data_seq) OVER w AS prev_data_seq,
  LAG(match_time_label) OVER w AS prev_match_time_label,
  LAG(match_minute) OVER w AS prev_match_minute,
  LAG(record_time) OVER w AS prev_record_time,
  LAG(fin_flg) OVER w AS prev_fin_flg,
  LAG(team_score) OVER w AS prev_team_score,
  LAG(opponent_score) OVER w AS prev_opponent_score,
  match_minute - LAG(match_minute) OVER w AS minutes_elapsed,
  possession - LAG(possession) OVER w AS possession_diff,
  exp - LAG(exp) OVER w AS exp_diff,
  in_goal_exp - LAG(in_goal_exp) OVER w AS in_goal_exp_diff,
  shoot_all - LAG(shoot_all) OVER w AS shoot_all_diff,
  shoot_in - LAG(shoot_in) OVER w AS shoot_in_diff,
  shoot_out - LAG(shoot_out) OVER w AS shoot_out_diff,
  block_shoot - LAG(block_shoot) OVER w AS block_shoot_diff,
  big_chance - LAG(big_chance) OVER w AS big_chance_diff,
  corner - LAG(corner) OVER w AS corner_diff,
  box_shoot_in - LAG(box_shoot_in) OVER w AS box_shoot_in_diff,
  box_shoot_out - LAG(box_shoot_out) OVER w AS box_shoot_out_diff,
  goal_post - LAG(goal_post) OVER w AS goal_post_diff,
  goal_head - LAG(goal_head) OVER w AS goal_head_diff,
  keeper_save - LAG(keeper_save) OVER w AS keeper_save_diff,
  free_kick - LAG(free_kick) OVER w AS free_kick_diff,
  offside - LAG(offside) OVER w AS offside_diff,
  foul - LAG(foul) OVER w AS foul_diff,
  yellow_card - LAG(yellow_card) OVER w AS yellow_card_diff,
  red_card - LAG(red_card) OVER w AS red_card_diff,
  slow_in - LAG(slow_in) OVER w AS slow_in_diff,
  box_touch - LAG(box_touch) OVER w AS box_touch_diff,
  clear_count - LAG(clear_count) OVER w AS clear_count_diff,
  duel_count - LAG(duel_count) OVER w AS duel_count_diff,
  intercept_count - LAG(intercept_count) OVER w AS intercept_count_diff,
  pass_count_success - LAG(pass_count_success) OVER w AS pass_count_success_diff,
  pass_count_try - LAG(pass_count_try) OVER w AS pass_count_try_diff,
  long_pass_count_success - LAG(long_pass_count_success) OVER w AS long_pass_count_success_diff,
  long_pass_count_try - LAG(long_pass_count_try) OVER w AS long_pass_count_try_diff,
  final_third_pass_count_success - LAG(final_third_pass_count_success) OVER w AS final_third_pass_count_success_diff,
  final_third_pass_count_try - LAG(final_third_pass_count_try) OVER w AS final_third_pass_count_try_diff,
  cross_count_success - LAG(cross_count_success) OVER w AS cross_count_success_diff,
  cross_count_try - LAG(cross_count_try) OVER w AS cross_count_try_diff,
  tackle_count_success - LAG(tackle_count_success) OVER w AS tackle_count_success_diff,
  tackle_count_try - LAG(tackle_count_try) OVER w AS tackle_count_try_diff,
  team_score - LAG(team_score) OVER w AS team_score_diff,
  opponent_score - LAG(opponent_score) OVER w AS opponent_score_diff,
  CASE WHEN pass_count_try - LAG(pass_count_try) OVER w > 0 AND pass_count_success - LAG(pass_count_success) OVER w >= 0
        AND pass_count_success - LAG(pass_count_success) OVER w <= pass_count_try - LAG(pass_count_try) OVER w
       THEN ROUND((pass_count_success - LAG(pass_count_success) OVER w) * 100.0 / (pass_count_try - LAG(pass_count_try) OVER w), 1) END AS pass_count_rate_interval,
  CASE WHEN long_pass_count_try - LAG(long_pass_count_try) OVER w > 0 AND long_pass_count_success - LAG(long_pass_count_success) OVER w >= 0
        AND long_pass_count_success - LAG(long_pass_count_success) OVER w <= long_pass_count_try - LAG(long_pass_count_try) OVER w
       THEN ROUND((long_pass_count_success - LAG(long_pass_count_success) OVER w) * 100.0 / (long_pass_count_try - LAG(long_pass_count_try) OVER w), 1) END AS long_pass_count_rate_interval,
  CASE WHEN final_third_pass_count_try - LAG(final_third_pass_count_try) OVER w > 0 AND final_third_pass_count_success - LAG(final_third_pass_count_success) OVER w >= 0
        AND final_third_pass_count_success - LAG(final_third_pass_count_success) OVER w <= final_third_pass_count_try - LAG(final_third_pass_count_try) OVER w
       THEN ROUND((final_third_pass_count_success - LAG(final_third_pass_count_success) OVER w) * 100.0 / (final_third_pass_count_try - LAG(final_third_pass_count_try) OVER w), 1) END AS final_third_pass_count_rate_interval,
  CASE WHEN cross_count_try - LAG(cross_count_try) OVER w > 0 AND cross_count_success - LAG(cross_count_success) OVER w >= 0
        AND cross_count_success - LAG(cross_count_success) OVER w <= cross_count_try - LAG(cross_count_try) OVER w
       THEN ROUND((cross_count_success - LAG(cross_count_success) OVER w) * 100.0 / (cross_count_try - LAG(cross_count_try) OVER w), 1) END AS cross_count_rate_interval,
  CASE WHEN tackle_count_try - LAG(tackle_count_try) OVER w > 0 AND tackle_count_success - LAG(tackle_count_success) OVER w >= 0
        AND tackle_count_success - LAG(tackle_count_success) OVER w <= tackle_count_try - LAG(tackle_count_try) OVER w
       THEN ROUND((tackle_count_success - LAG(tackle_count_success) OVER w) * 100.0 / (tackle_count_try - LAG(tackle_count_try) OVER w), 1) END AS tackle_count_rate_interval
FROM match_team_snapshot_fact
WINDOW w AS (PARTITION BY season, country, league, round_no, team, opponent, ha ORDER BY data_seq);

-- ===== 試合ごとの最新の差分（旧 BM_M029 real_data_process の置き換え）=====
-- 1行 = 1試合 × チーム。最新の時点と1つ前の時点の差分。
-- 試合終了（FIN）の行が続けて来た場合は、2件目以降を無視する（差分 0 で試合終了直前の差分が消えないように。旧 M029 と同じ）。
-- has_previous = FALSE はその試合の最初の時点（差分は NULL。累計値は *_diff ではなく元の値を見ること）。
CREATE VIEW match_team_snapshot_latest AS
SELECT DISTINCT ON (season, country, league, round_no, team, opponent, ha)
  d.*,
  (d.prev_data_seq IS NOT NULL) AS has_previous
FROM match_team_snapshot_diff d
WHERE NOT (d.fin_flg AND COALESCE(d.prev_fin_flg, FALSE))
ORDER BY season, country, league, round_no, team, opponent, ha, data_seq DESC;

-- ===== 試合ごとの最新の時点（累計値）＋ 試合の情報（BM_M035 / BM_M036 などの試合単位の統計の元）=====
-- 1行 = 1試合 × チーム。最新の時点（data_seq が最大）の全項目に、次を足したもの:
--   actual_minutes: その試合で読めた試合時間の最大値（分。アディショナルタイムを含む。読めなければ NULL）
--   finished: 試合終了（FIN）の行があるか（FALSE なら試合中の途中の値）
--   snapshot_count: 時点の数
-- （集計値は同じ区切りのウィンドウ関数で出す。集計結果と結合する形にすると、結合の件数見積もりが 1 件になり、
--   このビューを使う他のビューが「試合数 × 試合数」の遅い実行計画になるため）
CREATE VIEW match_team_snapshot_last AS
SELECT DISTINCT ON (season, country, league, round_no, team, opponent, ha)
  f.*,
  MAX(f.match_minute) OVER p AS actual_minutes,
  BOOL_OR(f.fin_flg)  OVER p AS finished,
  (COUNT(*) OVER p)::INTEGER AS snapshot_count
FROM match_team_snapshot_fact f
WINDOW p AS (PARTITION BY season, country, league, round_no, team, opponent, ha)
ORDER BY season, country, league, round_no, team, opponent, ha, data_seq DESC;


COMMIT;

-- ############################################################
-- 22_analyze_error_match.sql
-- ############################################################
-- 22: 登録できなかった試合の記録 analyze_error_match ＋ 集計ビュー（既存データは削除）
-- 統計処理で登録できなかった試合の記録（analyze_error_match）
--  シーズンが取得できない等の理由で、各 BM の統計テーブルに登録できなかった試合を記録する。
--  同じ BM・エラー種別・試合のエラーは1行にまとめ、発生回数（occurred_count）と最後の発生日時を更新する。
--  画面で対応済み（resolved_flg）にした行でも、同じエラーがまた起きたら未対応に戻る。
--  問題が直って同じ試合が正常に登録できたら、自動で対応済みにする（resolved_by = 'AUTO'。行は履歴として残す）。
--  ※ 既存データを残したい場合は、このファイルを流し直さないこと（DROP してから作り直す）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('analyze_error_summary', 'analyze_error_match')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 採番も 1 から振り直す（seq_counter が未作成なら ddl_seq_counter.sql を先に実行）
DELETE FROM seq_counter WHERE table_name = 'analyze_error_match';

CREATE TABLE analyze_error_match (
  seq               VARCHAR(40)                 NOT NULL,               -- <発生年>-<6桁枝番>（seq_counter。シーズンが取れないエラーも入るため発生年で採番）
  bm_number         VARCHAR(40)                 NOT NULL,               -- BM 番号（例: BM_M004）
  error_type        VARCHAR(40)                 NOT NULL,               -- SEASON_RESOLVER_MISSING / SEASON_NOT_FOUND / SEASON_RESOLVE_FAILED / INVALID_CATEGORY / MISSING_VALUE / INVALID_VALUE / UNEXPECTED
  error_message     TEXT,                                               -- エラー内容（最大 2000 文字）
  country           TEXT                        NOT NULL DEFAULT '',    -- 国（無ければ空文字）
  league            TEXT                        NOT NULL DEFAULT '',    -- リーグ（無ければ空文字）
  data_category     TEXT                        NOT NULL DEFAULT '',    -- 元のキー（「国: リーグ - ラウンドN」。無ければ空文字）
  home_team_name    TEXT                        NOT NULL DEFAULT '',    -- ホームチーム（チーム単位の BM はそのチーム。無ければ空文字）
  away_team_name    TEXT                        NOT NULL DEFAULT '',    -- アウェーチーム（無ければ空文字）
  error_field       TEXT                        NOT NULL DEFAULT '',    -- 原因の項目名（カンマ区切り。例: homeScore / country,league / dataCategory。無ければ空文字）
  error_value       TEXT,                                               -- 原因の項目のそのときの値（「項目名=値」を "; " 区切り）
  match_id          TEXT,                                               -- マッチID（参照用）
  season            VARCHAR(20),                                        -- シーズン（取得できていれば）
  detail            TEXT,                                               -- 補足（H/A・年など）
  exception_class   TEXT,                                               -- 例外クラス名
  stack_trace       TEXT,                                               -- スタックトレース（先頭 4000 文字）
  occurred_count    INTEGER                     NOT NULL DEFAULT 1,     -- 発生回数
  first_occurred_at TIMESTAMP(0) WITH TIME ZONE NOT NULL,               -- 最初の発生日時
  last_occurred_at  TIMESTAMP(0) WITH TIME ZONE NOT NULL,               -- 最後の発生日時
  resolved_flg      BOOLEAN                     NOT NULL DEFAULT FALSE, -- 対応済み（画面で更新）
  resolved_at       TIMESTAMP(0) WITH TIME ZONE,                        -- 対応日時（画面で更新）
  resolved_by       VARCHAR(100),                                       -- 対応者（画面で更新。自動解決は 'AUTO'）
  note              TEXT,                                               -- メモ（画面で更新）
  register_id       VARCHAR(100)                NOT NULL,
  register_time     TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id         VARCHAR(100)                NOT NULL,
  update_time       TIMESTAMP(0) WITH TIME ZONE NOT NULL,

  CONSTRAINT pk_analyze_error_match PRIMARY KEY (seq),
  CONSTRAINT uq_analyze_error_match
    UNIQUE (bm_number, error_type, country, league, data_category, home_team_name, away_team_name, error_field),
  CONSTRAINT ck_analyze_error_match_count CHECK (occurred_count >= 1)
);

-- 画面の一覧用（未対応を新しい順に）
CREATE INDEX idx_analyze_error_match_list ON analyze_error_match (resolved_flg, last_occurred_at DESC);
CREATE INDEX idx_analyze_error_match_league ON analyze_error_match (country, league);
-- 自動解決用（正常に登録できた試合の未対応エラーを探す）
CREATE INDEX idx_analyze_error_match_unresolved ON analyze_error_match (bm_number, country, league, home_team_name, away_team_name)
  WHERE resolved_flg = FALSE;

-- 画面の集計用: BM × エラー種別 × 国・リーグ × 原因の項目ごとの件数（どのリーグのマスタが足りないか・どの項目が空になりやすいか等を一目で見る）
CREATE VIEW analyze_error_summary AS
SELECT
  bm_number, error_type, country, league, error_field,
  COUNT(*)::INTEGER                                   AS match_count,       -- 登録できなかった試合（行）数
  COUNT(*) FILTER (WHERE NOT resolved_flg)::INTEGER   AS unresolved_count,  -- うち未対応
  SUM(occurred_count)::INTEGER                        AS occurred_total,    -- 発生回数の合計
  MIN(first_occurred_at)                              AS first_occurred_at,
  MAX(last_occurred_at)                               AS last_occurred_at,
  (ARRAY_AGG(error_message ORDER BY last_occurred_at DESC))[1] AS latest_message
FROM analyze_error_match
GROUP BY bm_number, error_type, country, league, error_field;

COMMIT;

-- ############################################################
-- 23_bm_m035.sql
-- ############################################################
-- 23: BM_M035 攻撃生成力統計ビュー（21 の後）
-- BM_M035 作り直し（攻撃生成力統計）
--  旧: match_team_attack_stats テーブルに、試合の最後のデータから Java で計算した値を INSERT（再送のたびに重複）
--  新: BM_M034 のビュー match_team_snapshot_last（試合ごとの最新の時点）から計算するビュー。Java のクラス・テーブルは不要。
--      同じ試合が何度流れても、欠けていたデータを後から入れても、値は常に明細から計算し直される。
--  ※ BM_M034 の DDL（ddl_match_team_snapshot_fact_rebuild.sql）の後に実行すること。
--     M034 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。
--
--  項目の決め方
--   - 対象時点: その試合・チームの最新の時点（data_seq が最大の行）。試合中なら途中の値（finished = FALSE）。
--   - actual_minutes: その試合で読めた試合時間の最大値（分。アディショナルタイムを含む。読めなければ NULL）。
--   - *_per90: 件数 × 90 ÷ actual_minutes（actual_minutes が NULL・0 なら NULL）。
--   - パス系: ファイナルサードパス・クロスは「試行数」（攻撃の量）を件数にする（成功数・成功率も列で出す）。
--     旧実装は "83% (120/145)" 形式を整数に変換できず、常に NULL だった。
--   - attack_volume_index（旧実装の暫定式のまま）:
--       0.40 × シュート/90 + 0.25 × 枠内シュート/90 + 0.15 × ボックスタッチ/90 + 0.10 × コーナー/90
--       + 0.05 × クロス試行/90 + 0.05 × (ファイナルサードパス試行/90 ÷ 10)
--     NULL の項目は 0 として足す（旧実装と同じ）。actual_minutes が NULL なら NULL。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_attack_stats', 'match_team_attack_stats')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'match_team_attack_stats';

-- ===== 1試合 × 1チーム =====
CREATE VIEW match_team_attack_stats AS
WITH base AS (
  SELECT
    l.season, l.country, l.league, l.match_id, l.round_no, l.team, l.opponent, l.ha,
    l.finished, l.snapshot_count, l.data_seq AS last_data_seq, l.match_time_label AS last_time_label, l.record_time,
    l.actual_minutes,
    l.team_score, l.opponent_score,
    l.shoot_all                   AS shots_count,
    l.shoot_in                    AS shots_on_target_count,
    l.box_touch                   AS box_touches_count,
    l.corner                      AS corners_count,
    l.final_third_pass_count_try  AS final_third_passes_count,
    l.final_third_pass_count_success AS final_third_passes_success,
    l.final_third_pass_count_rate AS final_third_passes_rate,
    l.cross_count_try             AS crosses_count,
    l.cross_count_success         AS crosses_success,
    l.cross_count_rate            AS crosses_rate
  FROM match_team_snapshot_last l
),
p AS (
  SELECT b.*,
    CASE WHEN actual_minutes > 0 THEN ROUND(shots_count              * 90.0 / actual_minutes, 4) END AS shots_per90,
    CASE WHEN actual_minutes > 0 THEN ROUND(shots_on_target_count    * 90.0 / actual_minutes, 4) END AS shots_on_target_per90,
    CASE WHEN actual_minutes > 0 THEN ROUND(box_touches_count        * 90.0 / actual_minutes, 4) END AS box_touches_per90,
    CASE WHEN actual_minutes > 0 THEN ROUND(corners_count            * 90.0 / actual_minutes, 4) END AS corners_per90,
    CASE WHEN actual_minutes > 0 THEN ROUND(final_third_passes_count * 90.0 / actual_minutes, 4) END AS final_third_passes_per90,
    CASE WHEN actual_minutes > 0 THEN ROUND(crosses_count            * 90.0 / actual_minutes, 4) END AS crosses_per90
  FROM base b
)
SELECT p.*,
  CASE WHEN actual_minutes > 0 THEN ROUND(
      0.40 * COALESCE(shots_per90, 0)
    + 0.25 * COALESCE(shots_on_target_per90, 0)
    + 0.15 * COALESCE(box_touches_per90, 0)
    + 0.10 * COALESCE(corners_per90, 0)
    + 0.05 * COALESCE(crosses_per90, 0)
    + 0.05 * COALESCE(final_third_passes_per90, 0) / 10, 4) END AS attack_volume_index
FROM p;

-- ===== チーム × シーズン（試合終了した試合だけ。ha = H: ホーム戦 / A: アウェー戦 / *: 合算）=====
-- *_per90 は 件数の合計 × 90 ÷ 分数の合計（試合ごとの per90 の平均ではない）。attack_volume_index は試合ごとの値の平均。
CREATE VIEW team_attack_stats AS
SELECT
  season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha,
  COUNT(*)::INTEGER                          AS match_count,
  SUM(actual_minutes)                        AS total_minutes,
  SUM(shots_count)::INTEGER                  AS shots_count,
  SUM(shots_on_target_count)::INTEGER        AS shots_on_target_count,
  SUM(box_touches_count)::INTEGER            AS box_touches_count,
  SUM(corners_count)::INTEGER                AS corners_count,
  SUM(final_third_passes_count)::INTEGER     AS final_third_passes_count,
  SUM(crosses_count)::INTEGER                AS crosses_count,
  ROUND(SUM(shots_count)              * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS shots_per90,
  ROUND(SUM(shots_on_target_count)    * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS shots_on_target_per90,
  ROUND(SUM(box_touches_count)        * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS box_touches_per90,
  ROUND(SUM(corners_count)            * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS corners_per90,
  ROUND(SUM(final_third_passes_count) * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS final_third_passes_per90,
  ROUND(SUM(crosses_count)            * 90.0 / NULLIF(SUM(actual_minutes), 0), 4) AS crosses_per90,
  ROUND(AVG(attack_volume_index), 4)         AS attack_volume_index_avg
FROM match_team_attack_stats
WHERE finished AND actual_minutes > 0
GROUP BY GROUPING SETS (
  (season, country, league, team, ha),
  (season, country, league, team));

COMMIT;

-- ############################################################
-- 24_bm_m036.sql
-- ############################################################
-- 24: BM_M036 攻撃効率統計ビュー（08・21 の後）
-- BM_M036 作り直し（攻撃効率統計）
--  旧: match_team_efficiency_stats テーブルに、試合の最後のデータから Java で計算した率を INSERT（再送のたびに重複）
--  新: BM_M034 のビュー match_team_snapshot_last（試合ごとの最新の時点）から計算するビュー。Java のクラス・テーブルは不要。
--  ※ BM_M034 の DDL の後に実行すること。M034 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。
--
--  率はすべて %（0〜100、小数2桁。分母が 0・NULL なら NULL。他テーブルと同じ単位）
--   on_target_rate          枠内シュート ÷ シュート
--   off_target_rate         枠外シュート ÷ シュート
--   blocked_rate            ブロックされたシュート ÷ シュート（旧実装に無し。枠内 + 枠外 + ブロック = 100% の確認用）
--   box_shot_rate           ボックス内シュート ÷ シュート
--   box_touch_to_shot_rate  ボックス内シュート ÷ ボックスタッチ（旧実装は「全シュート ÷ ボックスタッチ」で、ボックス外のシュートも数えていた）
--   shot_to_goal_rate       得点 ÷ シュート
--   on_target_to_goal_rate  得点 ÷ 枠内シュート
--   big_chance_to_goal_rate 得点 ÷ ビッグチャンス（参考。ビッグチャンス以外の得点も含む）
--  期待値（xG）との比較（旧実装に無し）
--   xg_per_shot       期待値 ÷ シュート（1本あたりのシュートの質）
--   goals_minus_xg    得点 − 期待値（プラスなら期待値以上に決めている）
--  得点はスコア（オウンゴールを含む）。セットプレー由来のシュート・得点は元データに無いため出さない（旧実装は常に NULL）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_efficiency_stats', 'match_team_efficiency_stats')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'match_team_efficiency_stats';

-- ===== 1試合 × 1チーム（試合中は途中の値。finished で区別）=====
CREATE VIEW match_team_efficiency_stats AS
SELECT
  season, country, league, match_id, round_no, team, opponent, ha,
  finished, snapshot_count, data_seq AS last_data_seq, match_time_label AS last_time_label, record_time, actual_minutes,
  team_score        AS goals_count,
  opponent_score    AS goals_against_count,
  shoot_all         AS shots_count,
  shoot_in          AS shots_on_target_count,
  shoot_out         AS shots_off_target_count,
  block_shoot       AS blocked_shots_count,
  box_shoot_in      AS box_shots_count,
  box_shoot_out     AS non_box_shots_count,
  box_touch         AS box_touches_count,
  big_chance        AS big_chances_count,
  exp               AS xg,
  in_goal_exp       AS xg_on_target,
  bm_rate_pct(shoot_in,     shoot_all)  AS on_target_rate,
  bm_rate_pct(shoot_out,    shoot_all)  AS off_target_rate,
  bm_rate_pct(block_shoot,  shoot_all)  AS blocked_rate,
  bm_rate_pct(box_shoot_in, shoot_all)  AS box_shot_rate,
  bm_rate_pct(box_shoot_in, box_touch)  AS box_touch_to_shot_rate,
  bm_rate_pct(team_score,   shoot_all)  AS shot_to_goal_rate,
  bm_rate_pct(team_score,   shoot_in)   AS on_target_to_goal_rate,
  bm_rate_pct(team_score,   big_chance) AS big_chance_to_goal_rate,
  CASE WHEN shoot_all > 0 THEN ROUND(exp / shoot_all, 4) END AS xg_per_shot,
  team_score - exp                                        AS goals_minus_xg
FROM match_team_snapshot_last;

-- ===== チーム × シーズン（試合終了した試合だけ。ha = H / A / *: 合算）=====
-- 率は「合計 ÷ 合計」（試合ごとの率の平均ではない。シュートの少ない試合の率に引っ張られない）。
CREATE VIEW team_efficiency_stats AS
SELECT
  season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha,
  COUNT(*)::INTEGER                       AS match_count,
  SUM(goals_count)::INTEGER               AS goals_count,
  SUM(shots_count)::INTEGER               AS shots_count,
  SUM(shots_on_target_count)::INTEGER     AS shots_on_target_count,
  SUM(box_shots_count)::INTEGER           AS box_shots_count,
  SUM(box_touches_count)::INTEGER         AS box_touches_count,
  SUM(big_chances_count)::INTEGER         AS big_chances_count,
  SUM(xg)                                 AS xg,
  bm_rate_pct(SUM(shots_on_target_count),  SUM(shots_count))       AS on_target_rate,
  bm_rate_pct(SUM(shots_off_target_count), SUM(shots_count))       AS off_target_rate,
  bm_rate_pct(SUM(blocked_shots_count),    SUM(shots_count))       AS blocked_rate,
  bm_rate_pct(SUM(box_shots_count),        SUM(shots_count))       AS box_shot_rate,
  bm_rate_pct(SUM(box_shots_count),        SUM(box_touches_count)) AS box_touch_to_shot_rate,
  bm_rate_pct(SUM(goals_count),            SUM(shots_count))       AS shot_to_goal_rate,
  bm_rate_pct(SUM(goals_count),            SUM(shots_on_target_count)) AS on_target_to_goal_rate,
  bm_rate_pct(SUM(goals_count),            SUM(big_chances_count)) AS big_chance_to_goal_rate,
  CASE WHEN SUM(shots_count) > 0 THEN ROUND(SUM(xg) / SUM(shots_count), 4) END AS xg_per_shot,
  SUM(goals_count) - SUM(xg)              AS goals_minus_xg
FROM match_team_efficiency_stats
WHERE finished
GROUP BY GROUPING SETS (
  (season, country, league, team, ha),
  (season, country, league, team));

COMMIT;

-- ############################################################
-- 25_bm_m037.sql
-- ############################################################
-- 25: BM_M037 守備統計ビュー（08・21 の後）
-- BM_M037 作り直し（守備統計）
--  旧: match_team_defense_stats テーブルに、試合の全データから Java で計算した値を INSERT（再送のたびに重複）
--  新: BM_M034 の明細 match_team_snapshot_fact / ビュー match_team_snapshot_last から計算するビュー。Java のクラス・テーブルは不要。
--      同じ試合が何度流れても、欠けていた時点を後から入れても、値は常に明細から計算し直される。
--  ※ BM_M034 の DDL と 08（関数 bm_rate_pct）の後に実行すること。
--     M034 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。
--
--  「相手の値」は同じ時点（同じ data_seq）の相手視点の行（ha が逆）から取る。
--
--  ビュー
--   match_team_defense_timeline      1試合 × チーム × 1時点。自チームの守備の値と相手の攻撃の値を横に並べたもの（下の2つの元）
--   match_team_defense_event_window  1試合 × チーム × 1イベント（失点・自チームの退場）。イベント後 10 分間の相手の攻撃量
--   match_team_defense_stats         1試合 × チーム。被シュート・セーブ率・ブロック率・守備アクション・リード中の被シュート・イベント後の圧力
--   team_defense_stats               チーム × シーズン × H/A/*（試合終了した試合だけ。率は合計 ÷ 合計）
--
--  項目の決め方
--   - save_rate      セーブ ÷ 相手の枠内シュート（枠内シュートには得点も含む）
--   - block_rate     相手のシュートのうちブロックされた割合（相手の block_shoot ÷ 相手の shoot_all）
--   - defensive_actions  タックル成功 + インターセプト + クリア + ブロック（旧実装の独自の重み付けはやめ、単純な合計にした）
--   - defensive_actions_per_opp_final_third  守備アクション ÷ 相手のファイナルサードパス試行 × 100
--       （旧実装は "83% (120/145)" 形式を整数に変換できず、相手ファイナルサードパス・タックルが常に NULL だった）
--   - *_per90        件数 × 90 ÷ actual_minutes
--   - リード中の被シュート: 時点と時点の間（区間）を、区間の開始時点のスコアで「リード中 / 同点 / ビハインド」に分け、
--       区間ごとの相手シュートの増加と経過分を合計する。
--       lead_shots_conceded_change_pct = (リード中の被シュート/90 ÷ 全体の被シュート/90 − 1) × 100
--       （プラスならリードすると押し込まれる）。試合時間が読めない時点・時間が戻る区間（45+2' → ハーフタイム）は数えない。
--   - イベント後の圧力: 失点（相手得点が増えた最初の時点）・自チームの退場（レッドカードが増えた最初の時点）を起点に、
--       起点の時点 〜 起点 + 10 分以内の最後の時点 の相手の攻撃量の増加。10 分以内に次の時点が無いときは起点より後の最初の時点まで。
--       窓の長さが 10 分ちょうどにならないので、実際の分（window_minutes）で 10 分あたりに換算する（*_per10）。
--       起点より後に試合時間の読める時点がまだ無いイベントは出さない（次のデータが来たら出る）。
--       window_complete = FALSE（10 分に満たず、試合もまだ終わっていない）のイベントは試合単位・チーム単位の平均から除く。
--       同じ区間で 2 点入った場合は 1 イベント（event_count = 2）。起点の時点に試合時間が無いイベントは出さない。
--       ※ 起点はデータ上で失点が見えた時点（実際の失点はその前の時点との間）。時点の間隔が粗い試合ほど起点は遅れる。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_defense_stats', 'match_team_defense_stats',
                        'match_team_defense_event_window', 'match_team_defense_timeline')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'match_team_defense_stats';

-- ===== 1試合 × チーム × 1時点：自チームの守備 ＋ 相手の攻撃 =====
CREATE VIEW match_team_defense_timeline AS
SELECT
  s.season, s.country, s.league, s.match_id, s.round_no, s.team, s.opponent, s.ha,
  s.data_seq, s.half, s.match_time_label, s.match_minute, s.fin_flg,
  s.team_score, s.opponent_score, s.score_diff,
  s.red_card,
  o.red_card                AS opp_red_card,
  o.shoot_all               AS opp_shoot_all,
  o.shoot_in                AS opp_shoot_in,
  o.box_shoot_in            AS opp_box_shoot_in,
  o.box_touch               AS opp_box_touch,
  o.big_chance              AS opp_big_chance,
  o.corner                  AS opp_corner,
  o.exp                     AS opp_exp
FROM match_team_snapshot_fact s
JOIN match_team_snapshot_fact o
  ON o.data_seq = s.data_seq AND o.ha <> s.ha;

-- ===== イベント（失点・自チームの退場）後 10 分間の相手の攻撃量 =====
CREATE VIEW match_team_defense_event_window AS
WITH t AS (
  SELECT tl.*,
         LAG(opponent_score) OVER w AS prev_opponent_score,
         LAG(red_card)       OVER w AS prev_red_card,
         MAX(match_minute)   OVER (PARTITION BY season, country, league, round_no, team, opponent, ha) AS last_minute,
         BOOL_OR(fin_flg)    OVER (PARTITION BY season, country, league, round_no, team, opponent, ha) AS finished
  FROM match_team_defense_timeline tl
  WINDOW w AS (PARTITION BY season, country, league, round_no, team, opponent, ha ORDER BY data_seq)
),
ev AS (
  -- t を1回だけ参照する（2回参照すると t が全件で作られ、チーム・ラウンドでの絞り込みが明細の検索まで届かない）
  SELECT t.*, v.event_type, v.event_count
  FROM t
  CROSS JOIN LATERAL (VALUES
    ('CONCEDED'::VARCHAR(10), t.opponent_score - t.prev_opponent_score),
    ('RED_CARD'::VARCHAR(10), t.red_card - t.prev_red_card)
  ) v(event_type, event_count)
  WHERE v.event_count > 0
)
SELECT
  ev.season, ev.country, ev.league, ev.match_id, ev.round_no, ev.team, ev.opponent, ev.ha,
  ev.event_type, ev.event_count::INTEGER AS event_count,
  ev.data_seq          AS event_data_seq,
  ev.match_time_label  AS event_time_label,
  ev.match_minute      AS event_minute,
  ev.team_score        AS event_team_score,
  ev.opponent_score    AS event_opponent_score,
  e.data_seq           AS end_data_seq,
  e.match_minute       AS end_minute,
  (e.match_minute - ev.match_minute) AS window_minutes,
  (ev.last_minute >= ev.match_minute + 10 OR ev.finished) AS window_complete,
  (e.opp_shoot_all    - ev.opp_shoot_all)    AS opp_shots_in_window,
  (e.opp_shoot_in     - ev.opp_shoot_in)     AS opp_shots_on_target_in_window,
  (e.opp_box_touch    - ev.opp_box_touch)    AS opp_box_touches_in_window,
  (e.opp_big_chance   - ev.opp_big_chance)   AS opp_big_chances_in_window,
  (e.opp_exp          - ev.opp_exp)          AS opp_xg_in_window,
  (e.opponent_score   - ev.opponent_score)   AS goals_conceded_in_window,
  CASE WHEN e.match_minute - ev.match_minute > 0
       THEN ROUND((e.opp_shoot_all - ev.opp_shoot_all) * 10.0 / (e.match_minute - ev.match_minute), 2) END AS opp_shots_per10,
  CASE WHEN e.match_minute - ev.match_minute > 0
       THEN ROUND((e.opp_box_touch - ev.opp_box_touch) * 10.0 / (e.match_minute - ev.match_minute), 2) END AS opp_box_touches_per10,
  CASE WHEN e.match_minute - ev.match_minute > 0
       THEN ROUND((e.opp_exp - ev.opp_exp) * 10.0 / (e.match_minute - ev.match_minute), 3) END AS opp_xg_per10
FROM ev
CROSS JOIN LATERAL (
  -- 窓の終わり: 起点より後で起点 + 10 分以内の最後の時点。
  -- 10 分以内に次の時点が無い（時点の間隔が粗い）ときは、起点より後の最初の時点（10 分を超える。window_minutes で換算）。
  SELECT x.*
  FROM match_team_defense_timeline x
  WHERE x.season = ev.season AND x.country = ev.country AND x.league = ev.league
    AND x.round_no = ev.round_no AND x.team = ev.team AND x.opponent = ev.opponent AND x.ha = ev.ha
    AND x.data_seq > ev.data_seq
    AND x.match_minute > ev.match_minute
  ORDER BY CASE WHEN x.match_minute <= ev.match_minute + 10 THEN 0 ELSE 1 END,
           CASE WHEN x.match_minute <= ev.match_minute + 10 THEN -x.data_seq ELSE x.data_seq END
  LIMIT 1
) e
WHERE ev.match_minute IS NOT NULL;

-- ===== 1試合 × 1チーム（試合中は途中の値。finished で区別）=====
CREATE VIEW match_team_defense_stats AS
WITH intervals AS (
  -- 区間（直前の時点 → この時点）。区間の開始時点のスコアで状態を決める
  SELECT season, country, league, round_no, team, opponent, ha,
         SIGN(LAG(score_diff) OVER w)                     AS state,
         match_minute - LAG(match_minute) OVER w          AS minutes,
         opp_shoot_all - LAG(opp_shoot_all) OVER w        AS opp_shots
  FROM match_team_defense_timeline
  WINDOW w AS (PARTITION BY season, country, league, round_no, team, opponent, ha ORDER BY data_seq)
),
state_agg AS (
  SELECT season, country, league, round_no, team, opponent, ha,
         SUM(minutes)                          AS measured_minutes,
         SUM(opp_shots)                        AS measured_opp_shots,
         SUM(minutes)   FILTER (WHERE state > 0) AS lead_minutes,
         SUM(opp_shots) FILTER (WHERE state > 0) AS lead_opp_shots,
         SUM(minutes)   FILTER (WHERE state = 0) AS level_minutes,
         SUM(opp_shots) FILTER (WHERE state = 0) AS level_opp_shots,
         SUM(minutes)   FILTER (WHERE state < 0) AS trail_minutes,
         SUM(opp_shots) FILTER (WHERE state < 0) AS trail_opp_shots
  FROM intervals
  WHERE state IS NOT NULL AND minutes >= 0 AND opp_shots IS NOT NULL
  GROUP BY season, country, league, round_no, team, opponent, ha
),
event_agg AS (
  SELECT season, country, league, round_no, team, opponent, ha,
         COUNT(*) FILTER (WHERE event_type = 'CONCEDED')                          AS conceded_events,
         SUM(opp_shots_in_window)   FILTER (WHERE event_type = 'CONCEDED' AND window_complete) AS after_conceded_opp_shots,
         SUM(window_minutes)        FILTER (WHERE event_type = 'CONCEDED' AND window_complete) AS after_conceded_minutes,
         SUM(opp_box_touches_in_window) FILTER (WHERE event_type = 'CONCEDED' AND window_complete) AS after_conceded_opp_box_touches,
         COUNT(*) FILTER (WHERE event_type = 'RED_CARD')                          AS red_card_events,
         SUM(opp_shots_in_window)   FILTER (WHERE event_type = 'RED_CARD' AND window_complete) AS after_red_opp_shots,
         SUM(window_minutes)        FILTER (WHERE event_type = 'RED_CARD' AND window_complete) AS after_red_minutes,
         SUM(opp_box_touches_in_window) FILTER (WHERE event_type = 'RED_CARD' AND window_complete) AS after_red_opp_box_touches
  FROM match_team_defense_event_window
  GROUP BY season, country, league, round_no, team, opponent, ha
),
base AS (
  SELECT
    s.season, s.country, s.league, s.match_id, s.round_no, s.team, s.opponent, s.ha,
    s.finished, s.snapshot_count, s.data_seq AS last_data_seq, s.match_time_label AS last_time_label,
    s.record_time, s.actual_minutes,
    s.opponent_score                 AS goals_conceded_count,
    o.shoot_all                      AS shots_conceded_count,
    o.shoot_in                       AS shots_on_target_conceded_count,
    o.box_shoot_in                   AS box_shots_conceded_count,
    o.box_touch                      AS box_touches_conceded_count,
    o.big_chance                     AS big_chances_conceded_count,
    o.exp                            AS xg_conceded,
    s.keeper_save                    AS saves_count,
    o.block_shoot                    AS blocks_count,
    s.clear_count                    AS clearances_count,
    s.intercept_count                AS interceptions_count,
    s.tackle_count_success           AS tackles_won_count,
    s.tackle_count_try               AS tackles_count,
    s.duel_count                     AS duels_won_count,
    s.foul                           AS fouls_count,
    s.yellow_card                    AS yellow_cards_count,
    s.red_card                       AS red_cards_count,
    o.final_third_pass_count_try     AS opp_final_third_passes_count,
    COALESCE(s.tackle_count_success, 0) + COALESCE(s.intercept_count, 0)
      + COALESCE(s.clear_count, 0) + COALESCE(o.block_shoot, 0) AS defensive_actions_count
  FROM match_team_snapshot_last s
  JOIN match_team_snapshot_fact o
    ON o.data_seq = s.data_seq AND o.ha <> s.ha
)
SELECT
  b.*,
  bm_rate_pct(b.saves_count, b.shots_on_target_conceded_count)          AS save_rate,
  bm_rate_pct(b.blocks_count, b.shots_conceded_count)                   AS block_rate,
  bm_rate_pct(b.tackles_won_count, b.tackles_count)                    AS tackle_rate,
  CASE WHEN b.actual_minutes > 0 THEN ROUND(b.shots_conceded_count * 90.0 / b.actual_minutes, 2) END        AS shots_conceded_per90,
  CASE WHEN b.actual_minutes > 0 THEN ROUND(b.box_touches_conceded_count * 90.0 / b.actual_minutes, 2) END  AS box_touches_conceded_per90,
  CASE WHEN b.actual_minutes > 0 THEN ROUND(b.clearances_count * 90.0 / b.actual_minutes, 2) END            AS clearances_per90,
  CASE WHEN b.actual_minutes > 0 THEN ROUND(b.defensive_actions_count * 90.0 / b.actual_minutes, 2) END     AS defensive_actions_per90,
  bm_rate_pct(b.defensive_actions_count, b.opp_final_third_passes_count) AS defensive_actions_per_opp_final_third,
  -- リード中 / 同点 / ビハインドの被シュート
  sa.measured_minutes, sa.lead_minutes, sa.lead_opp_shots, sa.level_minutes, sa.level_opp_shots, sa.trail_minutes, sa.trail_opp_shots,
  CASE WHEN sa.measured_minutes > 0 THEN ROUND(sa.measured_opp_shots * 90.0 / sa.measured_minutes, 2) END AS measured_shots_conceded_per90,
  CASE WHEN sa.lead_minutes  > 0 THEN ROUND(sa.lead_opp_shots  * 90.0 / sa.lead_minutes,  2) END AS lead_shots_conceded_per90,
  CASE WHEN sa.level_minutes > 0 THEN ROUND(sa.level_opp_shots * 90.0 / sa.level_minutes, 2) END AS level_shots_conceded_per90,
  CASE WHEN sa.trail_minutes > 0 THEN ROUND(sa.trail_opp_shots * 90.0 / sa.trail_minutes, 2) END AS trail_shots_conceded_per90,
  CASE WHEN sa.lead_minutes > 0 AND sa.measured_minutes > 0 AND sa.measured_opp_shots > 0
       THEN ROUND((sa.lead_opp_shots / sa.lead_minutes) / (sa.measured_opp_shots / sa.measured_minutes) * 100.0 - 100, 2) END
       AS lead_shots_conceded_change_pct,
  -- イベント後 10 分の圧力（10 分あたり。window_complete のイベントだけ）
  COALESCE(ea.conceded_events, 0)::INTEGER AS conceded_events,
  ea.after_conceded_minutes, ea.after_conceded_opp_shots,
  CASE WHEN ea.after_conceded_minutes > 0 THEN ROUND(ea.after_conceded_opp_shots * 10.0 / ea.after_conceded_minutes, 2) END AS after_conceded_opp_shots_per10,
  CASE WHEN ea.after_conceded_minutes > 0 THEN ROUND(ea.after_conceded_opp_box_touches * 10.0 / ea.after_conceded_minutes, 2) END AS after_conceded_opp_box_touches_per10,
  COALESCE(ea.red_card_events, 0)::INTEGER AS red_card_events,
  ea.after_red_minutes, ea.after_red_opp_shots,
  CASE WHEN ea.after_red_minutes > 0 THEN ROUND(ea.after_red_opp_shots * 10.0 / ea.after_red_minutes, 2) END AS after_red_opp_shots_per10,
  CASE WHEN ea.after_red_minutes > 0 THEN ROUND(ea.after_red_opp_box_touches * 10.0 / ea.after_red_minutes, 2) END AS after_red_opp_box_touches_per10
FROM base b
LEFT JOIN state_agg sa
  ON sa.season = b.season AND sa.country = b.country AND sa.league = b.league
 AND sa.round_no = b.round_no AND sa.team = b.team AND sa.opponent = b.opponent AND sa.ha = b.ha
LEFT JOIN event_agg ea
  ON ea.season = b.season AND ea.country = b.country AND ea.league = b.league
 AND ea.round_no = b.round_no AND ea.team = b.team AND ea.opponent = b.opponent AND ea.ha = b.ha;

-- ===== チーム × シーズン（試合終了した試合だけ。ha = H / A / *: 合算）=====
-- 率・90分換算は「合計 ÷ 合計」（試合ごとの値の平均ではない）。
CREATE VIEW team_defense_stats AS
SELECT
  season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha,
  COUNT(*)::INTEGER                              AS match_count,
  SUM(goals_conceded_count)::INTEGER             AS goals_conceded_count,
  SUM(shots_conceded_count)::INTEGER             AS shots_conceded_count,
  SUM(shots_on_target_conceded_count)::INTEGER   AS shots_on_target_conceded_count,
  SUM(box_touches_conceded_count)::INTEGER       AS box_touches_conceded_count,
  SUM(xg_conceded)                               AS xg_conceded,
  SUM(saves_count)::INTEGER                      AS saves_count,
  SUM(blocks_count)::INTEGER                     AS blocks_count,
  SUM(clearances_count)::INTEGER                 AS clearances_count,
  SUM(interceptions_count)::INTEGER              AS interceptions_count,
  SUM(tackles_won_count)::INTEGER                AS tackles_won_count,
  SUM(tackles_count)::INTEGER                    AS tackles_count,
  SUM(defensive_actions_count)::INTEGER          AS defensive_actions_count,
  SUM(actual_minutes)                            AS minutes,
  bm_rate_pct(SUM(saves_count),       SUM(shots_on_target_conceded_count)) AS save_rate,
  bm_rate_pct(SUM(blocks_count),      SUM(shots_conceded_count))           AS block_rate,
  bm_rate_pct(SUM(tackles_won_count), SUM(tackles_count))                  AS tackle_rate,
  CASE WHEN SUM(actual_minutes) > 0 THEN ROUND(SUM(shots_conceded_count) * 90.0 / SUM(actual_minutes), 2) END    AS shots_conceded_per90,
  CASE WHEN SUM(actual_minutes) > 0 THEN ROUND(SUM(defensive_actions_count) * 90.0 / SUM(actual_minutes), 2) END AS defensive_actions_per90,
  CASE WHEN SUM(actual_minutes) > 0 THEN ROUND(SUM(xg_conceded) * 90.0 / SUM(actual_minutes), 3) END             AS xg_conceded_per90,
  bm_rate_pct(SUM(defensive_actions_count), SUM(opp_final_third_passes_count)) AS defensive_actions_per_opp_final_third,
  SUM(lead_minutes)                              AS lead_minutes,
  CASE WHEN SUM(lead_minutes) > 0 THEN ROUND(SUM(lead_opp_shots) * 90.0 / SUM(lead_minutes), 2) END AS lead_shots_conceded_per90,
  CASE WHEN SUM(level_minutes) > 0 THEN ROUND(SUM(level_opp_shots) * 90.0 / SUM(level_minutes), 2) END AS level_shots_conceded_per90,
  CASE WHEN SUM(trail_minutes) > 0 THEN ROUND(SUM(trail_opp_shots) * 90.0 / SUM(trail_minutes), 2) END AS trail_shots_conceded_per90,
  SUM(conceded_events)::INTEGER                  AS conceded_events,
  CASE WHEN SUM(after_conceded_minutes) > 0 THEN ROUND(SUM(after_conceded_opp_shots) * 10.0 / SUM(after_conceded_minutes), 2) END AS after_conceded_opp_shots_per10,
  SUM(red_card_events)::INTEGER                  AS red_card_events,
  CASE WHEN SUM(after_red_minutes) > 0 THEN ROUND(SUM(after_red_opp_shots) * 10.0 / SUM(after_red_minutes), 2) END AS after_red_opp_shots_per10
FROM match_team_defense_stats
WHERE finished
GROUP BY GROUPING SETS (
  (season, country, league, team, ha),
  (season, country, league, team));

COMMIT;

-- ############################################################
-- 26_bm_m038.sql
-- ############################################################
-- 26: BM_M038 得点/失点の時刻ビュー（21 の後）
-- BM_M038 作り直し（時間帯別統計・得点/失点の時刻）
--  旧: match_team_timeband_stats テーブルに、1試合 × チーム × 15分の時間帯（6行）を INSERT（再送のたびに重複）。
--      さらに試合全体の「初失点・同点時得点・リード時失点・80分以降失点」の時刻を 6 行すべてに同じ値で持っていた。
--  新:
--   - 時間帯ごとの得点・失点・シュート・枠内・ボックスタッチ・コーナー・カードは BM_M004 team_time_segment_stats
--     （10分刻み + AT、項目も多い）に含まれるので、M038 の時間帯テーブルは作らない。
--   - 得点/失点の時刻は BM_M034 の明細 match_team_snapshot_fact から計算するビューにする。Java のクラス・テーブルは不要。
--  ※ BM_M034 の DDL の後に実行すること。M034 の DDL を流し直すと、このビューも CASCADE で消えるので、続けてこの DDL も流すこと。
--
--  ビュー
--   match_team_goal_event    1試合 × チーム × 1得点/失点イベント（そのチーム視点。得点前のスコア状態つき）
--   match_team_goal_timing   1試合 × チーム。初得点・初失点・同点時の初得点・リード時の初失点・80分以降の初失点 の時刻
--   team_goal_timing_stats   チーム × シーズン × H/A/*（試合終了した試合だけ）。先制率・リード時に失点した試合の割合など
--
--  項目の決め方
--   - 時刻（*_minute）はスコアが増えたことがデータ上で見えた時点の試合時間（分）。実際の得点はその前の時点との間。
--     試合時間が読めない時点で見えた得点は minute = NULL（旧実装は 0 秒にしていた）。
--   - 1つの区間で 2 点入った場合は 1 イベント（goal_count = 2）。
--   - state_before: 得点/失点の直前の時点のスコアで LEAD（リード中）/ LEVEL（同点）/ TRAIL（ビハインド）。
--   - late: 試合時間 80 分以降（80:00 を含む。前半の追加時間は含まない）。
--   - 旧実装は時刻を秒で持っていたが、元データの精度に合わせて分（小数2桁）にした。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_goal_timing_stats', 'match_team_goal_timing', 'match_team_goal_event',
                        'match_team_timeband_stats')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'match_team_timeband_stats';

-- ===== 1試合 × チーム × 1得点/失点イベント =====
CREATE VIEW match_team_goal_event AS
WITH t AS (
  SELECT season, country, league, match_id, round_no, team, opponent, ha,
         data_seq, half, match_time_label, match_minute, team_score, opponent_score,
         COALESCE(LAG(team_score)     OVER w, 0) AS prev_team_score,
         COALESCE(LAG(opponent_score) OVER w, 0) AS prev_opponent_score
  FROM match_team_snapshot_fact
  WHERE team_score IS NOT NULL AND opponent_score IS NOT NULL
  WINDOW w AS (PARTITION BY season, country, league, round_no, team, opponent, ha ORDER BY data_seq)
),
ev AS (
  -- t を1回だけ参照する（2回参照すると t が全件で作られ、チーム・ラウンドでの絞り込みが明細の検索まで届かない）
  SELECT t.*, v.event_type, v.goal_count
  FROM t
  CROSS JOIN LATERAL (VALUES
    ('SCORED'::VARCHAR(8),   t.team_score - t.prev_team_score),
    ('CONCEDED'::VARCHAR(8), t.opponent_score - t.prev_opponent_score)
  ) v(event_type, goal_count)
  WHERE v.goal_count > 0
)
SELECT
  season, country, league, match_id, round_no, team, opponent, ha,
  event_type,
  goal_count::INTEGER                     AS goal_count,
  data_seq                                AS event_data_seq,
  half,
  match_time_label                        AS event_time_label,
  match_minute                            AS event_minute,
  prev_team_score::SMALLINT               AS team_score_before,
  prev_opponent_score::SMALLINT           AS opponent_score_before,
  team_score                              AS team_score_after,
  opponent_score                          AS opponent_score_after,
  CASE WHEN prev_team_score > prev_opponent_score THEN 'LEAD'
       WHEN prev_team_score = prev_opponent_score THEN 'LEVEL'
       ELSE 'TRAIL' END::VARCHAR(5)       AS state_before,
  (match_minute >= 80 AND COALESCE(half, 2) = 2) AS late,
  ROW_NUMBER() OVER (PARTITION BY season, country, league, round_no, team, opponent, ha, event_type ORDER BY data_seq)::INTEGER AS event_no
FROM ev;

-- ===== 1試合 × チーム：イベント時刻（旧 M038 の4項目 ＋ 初得点）=====
-- 該当するイベントが無ければ NULL。*_minute が NULL でも *_label に表記が入ることがある（試合時間が読めない表記）。
CREATE VIEW match_team_goal_timing AS
WITH first_ev AS (
  SELECT season, country, league, round_no, team, opponent, ha,
         MIN(event_data_seq) FILTER (WHERE event_type = 'SCORED')                              AS first_scored_seq,
         MIN(event_data_seq) FILTER (WHERE event_type = 'CONCEDED')                            AS first_conceded_seq,
         MIN(event_data_seq) FILTER (WHERE event_type = 'SCORED'   AND state_before = 'LEVEL') AS level_scored_seq,
         MIN(event_data_seq) FILTER (WHERE event_type = 'CONCEDED' AND state_before = 'LEAD')  AS lead_conceded_seq,
         MIN(event_data_seq) FILTER (WHERE event_type = 'CONCEDED' AND late)                   AS late_conceded_seq,
         COUNT(*) FILTER (WHERE event_type = 'CONCEDED' AND state_before = 'LEAD')::INTEGER    AS lead_conceded_events,
         SUM(goal_count) FILTER (WHERE event_type = 'SCORED'   AND late)::INTEGER              AS late_goals,
         SUM(goal_count) FILTER (WHERE event_type = 'CONCEDED' AND late)::INTEGER              AS late_goals_conceded
  FROM match_team_goal_event
  GROUP BY season, country, league, round_no, team, opponent, ha
)
SELECT
  l.season, l.country, l.league, l.match_id, l.round_no, l.team, l.opponent, l.ha,
  l.finished, l.team_score AS goals, l.opponent_score AS goals_conceded,
  fs.match_minute  AS first_scored_minute,          fs.match_time_label  AS first_scored_label,
  fc.match_minute  AS first_conceded_minute,        fc.match_time_label  AS first_conceded_label,
  ls.match_minute  AS level_state_scored_minute,    ls.match_time_label  AS level_state_scored_label,
  lc.match_minute  AS lead_state_conceded_minute,   lc.match_time_label  AS lead_state_conceded_label,
  lt.match_minute  AS late_conceded_minute,         lt.match_time_label  AS late_conceded_label,
  -- 先制: 自チームの初得点が相手の初得点より先（同じ時点で両方増えた場合は判定しない = NULL）
  CASE WHEN e.first_scored_seq IS NULL AND e.first_conceded_seq IS NULL THEN NULL
       WHEN e.first_conceded_seq IS NULL THEN TRUE
       WHEN e.first_scored_seq   IS NULL THEN FALSE
       WHEN e.first_scored_seq < e.first_conceded_seq THEN TRUE
       WHEN e.first_scored_seq > e.first_conceded_seq THEN FALSE END AS scored_first,
  COALESCE(e.lead_conceded_events, 0) AS lead_conceded_events,
  COALESCE(e.late_goals, 0)           AS late_goals,
  COALESCE(e.late_goals_conceded, 0)  AS late_goals_conceded
FROM match_team_snapshot_last l
LEFT JOIN first_ev e
  ON e.season = l.season AND e.country = l.country AND e.league = l.league
 AND e.round_no = l.round_no AND e.team = l.team AND e.opponent = l.opponent AND e.ha = l.ha
LEFT JOIN match_team_snapshot_fact fs ON fs.data_seq = e.first_scored_seq   AND fs.ha = l.ha
LEFT JOIN match_team_snapshot_fact fc ON fc.data_seq = e.first_conceded_seq AND fc.ha = l.ha
LEFT JOIN match_team_snapshot_fact ls ON ls.data_seq = e.level_scored_seq   AND ls.ha = l.ha
LEFT JOIN match_team_snapshot_fact lc ON lc.data_seq = e.lead_conceded_seq  AND lc.ha = l.ha
LEFT JOIN match_team_snapshot_fact lt ON lt.data_seq = e.late_conceded_seq  AND lt.ha = l.ha;

-- ===== チーム × シーズン（試合終了した試合だけ。ha = H / A / *: 合算）=====
CREATE VIEW team_goal_timing_stats AS
SELECT
  season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha,
  COUNT(*)::INTEGER                                                        AS match_count,
  COUNT(*) FILTER (WHERE scored_first)::INTEGER                            AS scored_first_count,
  COUNT(*) FILTER (WHERE scored_first = FALSE)::INTEGER                    AS conceded_first_count,
  COUNT(*) FILTER (WHERE scored_first IS NULL AND goals + goals_conceded = 0)::INTEGER AS goalless_count,
  ROUND(COUNT(*) FILTER (WHERE scored_first) * 100.0 / COUNT(*), 2)        AS scored_first_rate,
  COUNT(*) FILTER (WHERE lead_conceded_events > 0)::INTEGER                AS lead_conceded_match_count,
  COUNT(*) FILTER (WHERE late_goals_conceded > 0)::INTEGER                 AS late_conceded_match_count,
  SUM(late_goals)::INTEGER                                                 AS late_goals,
  SUM(late_goals_conceded)::INTEGER                                        AS late_goals_conceded,
  ROUND(AVG(first_scored_minute), 2)                                       AS avg_first_scored_minute,
  ROUND(AVG(first_conceded_minute), 2)                                     AS avg_first_conceded_minute
FROM match_team_goal_timing
WHERE finished
GROUP BY GROUPING SETS (
  (season, country, league, team, ha),
  (season, country, league, team));

COMMIT;

-- ############################################################
-- 27_bm_m039.sql
-- ############################################################
-- 27: BM_M039 モメンタムビュー（21 の後）
-- BM_M039 作り直し（試合中の勢い・モメンタム）
--  旧: match_team_momentum_stats テーブルに、1試合 × 全時点 × 窓幅（5分・10分）× 2チームを INSERT。
--      試合中にデータが届くたびに全時点を入れ直すので、行が雪だるま式に重複していた。
--  新: BM_M034 の明細 match_team_snapshot_fact から計算するビュー。
--      Java のクラス・テーブルは不要。欠けていた時点を後から入れても計算し直される。
--  ※ BM_M034（21）の DDL の後に実行すること。M034 の DDL を流し直したら続けてこの DDL も流すこと。
--
--  ビュー
--   match_team_momentum          1試合 × チーム × 1時点 × 窓幅（5分・10分）。finished = 試合終了の行があるか。直近 N 分の自チーム − 相手の差・モメンタム指数・傾向・得点/失点後の反応
--   match_team_momentum_summary  1試合 × チーム × 窓幅。モメンタム指数の平均・最大・最小、優勢だった時点の割合
--
--  項目の決め方
--   - 窓の起点（baseline）: 現在の時点より前で、試合時間が「現在 − N 分」以下の最後の時点。
--       無ければキックオフ（全部 0）。時点の間隔が空いていると窓は N 分より長くなるので、実際の長さを window_actual_minutes に出す。
--       試合時間が読めない時点は行を出さない（旧実装は 0 秒扱いで、読めない時点どうしが1行にまとめられていた）。
--   - recent_*_diff: 窓の中で増えた量の「自チーム − 相手」。値が減った（訂正）場合もそのまま引く（旧実装は 0 で切り捨て）。
--   - 前進量（progression）: ファイナルサードパスの試行数（旧実装は "83% (120/145)" 形式を読めず常に 0 だった）。
--   - momentum_index（旧実装の暫定の重みのまま）:
--       0.35 × シュート差 + 0.35 × 枠内シュート差 + 0.15 × ボックスタッチ差 + 0.10 × コーナー差 + 0.05 × (前進量差 ÷ 10)
--       NULL の項目は 0 として足す。
--   - momentum_trend: 同じ試合・チーム・窓幅の直前の時点の指数との差が +0.05 超 → RISING、−0.05 未満 → FALLING、それ以外 → STABLE
--       （最初の時点は STABLE。旧実装と同じ）。
--   - post_goal_attack_response / post_conceded_attack_response:
--       窓の中（現在 − N 分 より後 〜 現在）に自チームの得点 / 失点があれば、その時点から現在までの自チームの攻撃の増加の重み付き合計
--       （シュート × 1.0 + 枠内 × 1.5 + ボックスタッチ × 0.2 + コーナー × 0.3 + ファイナルサードパス試行 × 0.05。旧実装と同じ）。
--       窓の中に無ければ NULL。複数あれば最後のもの。
--  ※ 1試合 約100〜200時点 × 2チーム × 2窓幅 の計算になるので、画面からはシーズン・国・リーグ・ラウンド・チーム（区切りの列）で絞って引くこと。
--     これらの列での絞り込みは明細の検索まで届く（1試合なら数十ミリ秒）。match_id だけの絞り込みは全試合を計算してから絞るので遅い。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('match_team_momentum_summary', 'match_team_momentum', 'match_team_momentum_stats')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'match_team_momentum_stats';

-- ===== 1試合 × チーム × 1時点 × 窓幅 =====
CREATE VIEW match_team_momentum AS
WITH pt AS (
  -- 同じ時点の自チームと相手の値を横に並べ、その時点までの最後の得点 / 失点の時点（data_seq）を持たせる
  -- （この CTE は1回しか参照しないので、画面からの絞り込み（チーム・ラウンドなど）が明細の検索まで届く）
  SELECT s.season, s.country, s.league, s.match_id, s.round_no, s.team, s.opponent, s.ha,
         s.data_seq, s.half, s.match_time_label, s.match_minute, s.team_score, s.opponent_score,
         COALESCE(s.shoot_all, 0)                  AS t_shots,
         COALESCE(s.shoot_in, 0)                   AS t_on_target,
         COALESCE(s.box_touch, 0)                  AS t_box_touches,
         COALESCE(s.corner, 0)                     AS t_corners,
         COALESCE(s.final_third_pass_count_try, 0) AS t_progression,
         COALESCE(o.shoot_all, 0)                  AS o_shots,
         COALESCE(o.shoot_in, 0)                   AS o_on_target,
         COALESCE(o.box_touch, 0)                  AS o_box_touches,
         COALESCE(o.corner, 0)                     AS o_corners,
         COALESCE(o.final_third_pass_count_try, 0) AS o_progression,
         MAX(CASE WHEN s.team_score     > s.prev_team_score     THEN s.data_seq END) OVER w AS last_scored_seq,
         MAX(CASE WHEN s.opponent_score > s.prev_opponent_score THEN s.data_seq END) OVER w AS last_conceded_seq,
         BOOL_OR(s.fin_flg) OVER (PARTITION BY s.season, s.country, s.league, s.round_no, s.team, s.opponent, s.ha) AS finished
  FROM (
    SELECT f.*,
           COALESCE(LAG(f.team_score)     OVER (PARTITION BY f.season, f.country, f.league, f.round_no, f.team, f.opponent, f.ha ORDER BY f.data_seq), 0) AS prev_team_score,
           COALESCE(LAG(f.opponent_score) OVER (PARTITION BY f.season, f.country, f.league, f.round_no, f.team, f.opponent, f.ha ORDER BY f.data_seq), 0) AS prev_opponent_score
    FROM match_team_snapshot_fact f
  ) s
  JOIN match_team_snapshot_fact o
    ON o.data_seq = s.data_seq AND o.ha <> s.ha
  WINDOW w AS (PARTITION BY s.season, s.country, s.league, s.round_no, s.team, s.opponent, s.ha ORDER BY s.data_seq)
),
calc AS (
  SELECT
    c.season, c.country, c.league, c.match_id, c.round_no, c.team, c.opponent, c.ha,
    c.finished,
    w.window_minutes,
    c.data_seq, c.half, c.match_time_label, c.match_minute, c.team_score, c.opponent_score,
    b.data_seq                                   AS baseline_data_seq,
    COALESCE(b.match_minute, 0)                  AS baseline_minute,
    c.match_minute - COALESCE(b.match_minute, 0) AS window_actual_minutes,
    (c.t_shots       - COALESCE(b.t_shots, 0))       - (c.o_shots       - COALESCE(b.o_shots, 0))       AS recent_shots_diff,
    (c.t_on_target   - COALESCE(b.t_on_target, 0))   - (c.o_on_target   - COALESCE(b.o_on_target, 0))   AS recent_shots_on_target_diff,
    (c.t_box_touches - COALESCE(b.t_box_touches, 0)) - (c.o_box_touches - COALESCE(b.o_box_touches, 0)) AS recent_box_touches_diff,
    (c.t_corners     - COALESCE(b.t_corners, 0))     - (c.o_corners     - COALESCE(b.o_corners, 0))     AS recent_corners_diff,
    (c.t_progression - COALESCE(b.t_progression, 0)) - (c.o_progression - COALESCE(b.o_progression, 0)) AS recent_progression_diff,
    -- 窓の中の最後の得点 / 失点から現在までの自チームの攻撃の増加（窓の外なら NULL）
    CASE WHEN gs.match_minute > c.match_minute - w.window_minutes THEN ROUND(
         (c.t_shots - COALESCE(gs.shoot_all, 0)) * 1.0 + (c.t_on_target - COALESCE(gs.shoot_in, 0)) * 1.5
       + (c.t_box_touches - COALESCE(gs.box_touch, 0)) * 0.2 + (c.t_corners - COALESCE(gs.corner, 0)) * 0.3
       + (c.t_progression - COALESCE(gs.final_third_pass_count_try, 0)) * 0.05, 4) END AS post_goal_attack_response,
    CASE WHEN gs.match_minute > c.match_minute - w.window_minutes THEN gs.match_minute END AS post_goal_event_minute,
    CASE WHEN gc.match_minute > c.match_minute - w.window_minutes THEN ROUND(
         (c.t_shots - COALESCE(gc.shoot_all, 0)) * 1.0 + (c.t_on_target - COALESCE(gc.shoot_in, 0)) * 1.5
       + (c.t_box_touches - COALESCE(gc.box_touch, 0)) * 0.2 + (c.t_corners - COALESCE(gc.corner, 0)) * 0.3
       + (c.t_progression - COALESCE(gc.final_third_pass_count_try, 0)) * 0.05, 4) END AS post_conceded_attack_response,
    CASE WHEN gc.match_minute > c.match_minute - w.window_minutes THEN gc.match_minute END AS post_conceded_event_minute
  FROM pt c
  CROSS JOIN (VALUES (5), (10)) w(window_minutes)
  LEFT JOIN LATERAL (
    -- 窓の起点: 現在より前で、試合時間が「現在 − N 分」以下の最後の時点（明細のインデックスを後ろから引く）
    SELECT bs.data_seq, bs.match_minute,
           COALESCE(bs.shoot_all, 0)                   AS t_shots,
           COALESCE(bs.shoot_in, 0)                    AS t_on_target,
           COALESCE(bs.box_touch, 0)                   AS t_box_touches,
           COALESCE(bs.corner, 0)                      AS t_corners,
           COALESCE(bs.final_third_pass_count_try, 0)  AS t_progression,
           COALESCE(bo.shoot_all, 0)                   AS o_shots,
           COALESCE(bo.shoot_in, 0)                    AS o_on_target,
           COALESCE(bo.box_touch, 0)                   AS o_box_touches,
           COALESCE(bo.corner, 0)                      AS o_corners,
           COALESCE(bo.final_third_pass_count_try, 0)  AS o_progression
    FROM match_team_snapshot_fact bs
    JOIN match_team_snapshot_fact bo ON bo.data_seq = bs.data_seq AND bo.ha <> bs.ha
    WHERE bs.season = c.season AND bs.country = c.country AND bs.league = c.league AND bs.round_no = c.round_no
      AND bs.team = c.team AND bs.opponent = c.opponent AND bs.ha = c.ha
      AND bs.data_seq < c.data_seq
      AND bs.match_minute <= c.match_minute - w.window_minutes
    ORDER BY bs.data_seq DESC
    LIMIT 1
  ) b ON TRUE
  -- その時点までの最後の得点 / 失点の時点の自チームの値（一意キー (data_seq, ha) で引く）
  LEFT JOIN match_team_snapshot_fact gs ON gs.data_seq = c.last_scored_seq   AND gs.ha = c.ha
  LEFT JOIN match_team_snapshot_fact gc ON gc.data_seq = c.last_conceded_seq AND gc.ha = c.ha
  WHERE c.match_minute IS NOT NULL
),
idx AS (
  SELECT calc.*,
         ROUND(0.35 * recent_shots_diff + 0.35 * recent_shots_on_target_diff + 0.15 * recent_box_touches_diff
             + 0.10 * recent_corners_diff + 0.05 * (recent_progression_diff / 10.0), 4) AS momentum_index
  FROM calc
)
SELECT idx.*,
       LAG(momentum_index) OVER mw AS prev_momentum_index,
       CASE WHEN momentum_index - LAG(momentum_index) OVER mw >  0.05 THEN 'RISING'
            WHEN momentum_index - LAG(momentum_index) OVER mw < -0.05 THEN 'FALLING'
            ELSE 'STABLE' END::VARCHAR(7) AS momentum_trend
FROM idx
WINDOW mw AS (PARTITION BY season, country, league, round_no, team, opponent, ha, window_minutes ORDER BY data_seq);

-- ===== 1試合 × チーム × 窓幅：モメンタムの要約 =====
CREATE VIEW match_team_momentum_summary AS
SELECT
  season, country, league, match_id, round_no, team, opponent, ha, window_minutes,
  finished,
  COUNT(*)::INTEGER                                              AS point_count,
  ROUND(AVG(momentum_index), 4)                                  AS avg_momentum_index,
  MAX(momentum_index)                                            AS max_momentum_index,
  MIN(momentum_index)                                            AS min_momentum_index,
  ROUND(COUNT(*) FILTER (WHERE momentum_index > 0) * 100.0 / COUNT(*), 2) AS dominant_point_rate,
  COUNT(*) FILTER (WHERE momentum_trend = 'RISING')::INTEGER     AS rising_count,
  COUNT(*) FILTER (WHERE momentum_trend = 'FALLING')::INTEGER    AS falling_count,
  MAX(post_goal_attack_response)                                 AS max_post_goal_attack_response,
  MAX(post_conceded_attack_response)                             AS max_post_conceded_attack_response
FROM match_team_momentum
GROUP BY season, country, league, match_id, round_no, team, opponent, ha, window_minutes, finished;

COMMIT;

-- ############################################################
-- 28_bm_m040.sql
-- ############################################################
-- 28: BM_M040 チームのプレースタイルビュー（08・21 の後）
-- BM_M040 作り直し（チームのプレースタイル）
--  旧: team_style_profile テーブルに、Java で集計した特徴量とルールベースのスタイルラベルを INSERT（実行のたびに重複）。
--      calcStat のキーの読み方が違っていて（外側「国: リーグ」を国、内側の試合キーをリーグとして扱っていた）、実際は
--      「1試合 × チーム」ごとに1行（sample_match_count = 1）になっていた。パス系は "83% (120/145)" 形式を読めず 0。
--  新: BM_M034 のビュー match_team_snapshot_last（試合ごとの最新の時点）から計算するビュー。Java のクラス・テーブルは不要。
--  ※ BM_M034（21）と 08（関数 bm_rate_pct・bm_score_high）の後に実行すること。M034 の DDL を流し直したら続けてこの DDL も流すこと。
--
--  ビュー
--   match_team_style_features  1試合 × チーム。スタイルの特徴量（将来クラスタリングする場合の入力にもなる）
--   team_style_profile         チーム × シーズン × H/A/*（試合終了した試合だけ）。特徴量・5つのスタイルのスコア・ラベル・信頼度
--
--  特徴量（率は %。分母が 0・NULL なら NULL。旧実装は 0 にしていたため、データが無いチームが「堅守速攻」などに判定されていた）
--   possession               ポゼッション（チーム単位は試合の平均）
--   passes_per90             パス試行 × 90 ÷ 試合時間
--   pass_accuracy            パス成功 ÷ パス試行（旧実装に無し）
--   long_pass_rate           ロングパス試行 ÷ パス試行
--   final_third_pass_rate    ファイナルサードパス試行 ÷ パス試行
--   cross_rate               クロス試行 ÷ パス試行
--   shots_per_box_touch      シュート ÷ ボックスタッチ（%）
--   defensive_actions_per90  (タックル試行 + インターセプト + クリア) × 90 ÷ 試合時間
--   clearance_rate           クリア ÷ (タックル試行 + インターセプト + クリア)
--   duels_won_per90          デュエル勝利 × 90 ÷ 試合時間
--   チーム単位は「合計 ÷ 合計」。90分換算は試合時間が読めた試合だけで計算する（旧実装は読めなければ 90 分としていた）。
--
--  スタイル判定（旧実装のルールと閾値のまま。正規化 score = (値 − 下限) ÷ (上限 − 下限) を 0〜1 に丸めたもの。率は 0〜1 に直して使う）
--   1 ポゼッション    : high(pos, .48, .62) + high(pass90, 320, 620) + high(ft, .14, .30) − 0.3 × high(long, .10, .24)
--   2 ロングボール    : high(long, .10, .24) + low(pass90, 320, 620) + 0.5 × high(shot/box, .12, .35)
--   3 堅守速攻        : low(pos, .48, .62) + high(def90, 18, 42) + high(clear, .18, .45) + 0.5 × high(shot/box, .12, .35)
--   4 サイドアタック  : high(cross, .03, .12) + 0.5 × high(ft, .14, .30) + 0.3 × high(duel90, 10, 28)
--   5 バランス        : 0.8 − |pos − .50| − |long − .15| − |cross − .06|
--   一番高いスコアのスタイルをラベルにする（同点は番号の小さい方）。
--   style_confidence = 0.55 + (1位 − 2位) × 0.20 を 0.55〜0.95 に丸めたもの（旧実装と同じ。確率ではない）。
--   ポゼッション・パス試行が取れていないチームはラベルを付けない（NULL）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_style_profile', 'match_team_style_features')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'team_style_profile';

-- ===== 1試合 × チーム：スタイルの特徴量 =====
CREATE VIEW match_team_style_features AS
SELECT
  season, country, league, match_id, round_no, team, opponent, ha,
  finished, actual_minutes, record_time,
  possession,
  pass_count_try, pass_count_success, long_pass_count_try, final_third_pass_count_try, cross_count_try,
  shoot_all, box_touch, tackle_count_try, intercept_count, clear_count, duel_count,
  COALESCE(tackle_count_try, 0) + COALESCE(intercept_count, 0) + COALESCE(clear_count, 0) AS defensive_actions_count,
  CASE WHEN actual_minutes > 0 THEN ROUND(pass_count_try * 90.0 / actual_minutes, 2) END AS passes_per90,
  bm_rate_pct(pass_count_success,         pass_count_try) AS pass_accuracy,
  bm_rate_pct(long_pass_count_try,        pass_count_try) AS long_pass_rate,
  bm_rate_pct(final_third_pass_count_try, pass_count_try) AS final_third_pass_rate,
  bm_rate_pct(cross_count_try,            pass_count_try) AS cross_rate,
  bm_rate_pct(shoot_all,                  box_touch)      AS shots_per_box_touch,
  CASE WHEN actual_minutes > 0
       THEN ROUND((COALESCE(tackle_count_try, 0) + COALESCE(intercept_count, 0) + COALESCE(clear_count, 0)) * 90.0 / actual_minutes, 2) END
       AS defensive_actions_per90,
  bm_rate_pct(clear_count, COALESCE(tackle_count_try, 0) + COALESCE(intercept_count, 0) + COALESCE(clear_count, 0)) AS clearance_rate,
  CASE WHEN actual_minutes > 0 THEN ROUND(duel_count * 90.0 / actual_minutes, 2) END AS duels_won_per90
FROM match_team_snapshot_last;

-- ===== チーム × シーズン × H/A/*：スタイルプロファイル =====
CREATE VIEW team_style_profile AS
WITH agg AS (
  SELECT
    season, country, league, team, COALESCE(ha, '*')::CHAR(1) AS ha,
    COUNT(*)::INTEGER                           AS sample_match_count,
    MIN(record_time)::DATE                      AS from_date,
    MAX(record_time)::DATE                      AS to_date,
    ROUND(AVG(possession), 2)                   AS possession,
    SUM(actual_minutes)                         AS minutes,
    SUM(pass_count_try)          FILTER (WHERE actual_minutes > 0) AS pass_try_timed,
    SUM(defensive_actions_count) FILTER (WHERE actual_minutes > 0) AS def_timed,
    SUM(duel_count)              FILTER (WHERE actual_minutes > 0) AS duel_timed,
    SUM(pass_count_try)                         AS pass_try,
    SUM(pass_count_success)                     AS pass_success,
    SUM(long_pass_count_try)                    AS long_try,
    SUM(final_third_pass_count_try)             AS ft_try,
    SUM(cross_count_try)                        AS cross_try,
    SUM(shoot_all)                              AS shots,
    SUM(box_touch)                              AS box_touches,
    SUM(clear_count)                            AS clears,
    SUM(defensive_actions_count)                AS def_actions
  FROM match_team_style_features
  WHERE finished
  GROUP BY GROUPING SETS (
    (season, country, league, team, ha),
    (season, country, league, team))
),
feat AS (
  SELECT agg.*,
    CASE WHEN minutes > 0 THEN ROUND(pass_try_timed * 90.0 / minutes, 2) END AS passes_per90,
    bm_rate_pct(pass_success, pass_try)  AS pass_accuracy,
    bm_rate_pct(long_try,     pass_try)  AS long_pass_rate,
    bm_rate_pct(ft_try,       pass_try)  AS final_third_pass_rate,
    bm_rate_pct(cross_try,    pass_try)  AS cross_rate,
    bm_rate_pct(shots,        box_touches) AS shots_per_box_touch,
    CASE WHEN minutes > 0 THEN ROUND(def_timed  * 90.0 / minutes, 2) END AS defensive_actions_per90,
    bm_rate_pct(clears, def_actions)     AS clearance_rate,
    CASE WHEN minutes > 0 THEN ROUND(duel_timed * 90.0 / minutes, 2) END AS duels_won_per90
  FROM agg
),
score AS (
  SELECT feat.*,
    ROUND(bm_score_high(possession / 100, .48, .62) + bm_score_high(passes_per90, 320, 620)
        + bm_score_high(final_third_pass_rate / 100, .14, .30) - 0.3 * bm_score_high(long_pass_rate / 100, .10, .24), 4) AS possession_score,
    ROUND(bm_score_high(long_pass_rate / 100, .10, .24) + (1 - bm_score_high(passes_per90, 320, 620))
        + 0.5 * bm_score_high(shots_per_box_touch / 100, .12, .35), 4) AS long_ball_score,
    ROUND((1 - bm_score_high(possession / 100, .48, .62)) + bm_score_high(defensive_actions_per90, 18, 42)
        + bm_score_high(clearance_rate / 100, .18, .45) + 0.5 * bm_score_high(shots_per_box_touch / 100, .12, .35), 4) AS counter_score,
    ROUND(bm_score_high(cross_rate / 100, .03, .12) + 0.5 * bm_score_high(final_third_pass_rate / 100, .14, .30)
        + 0.3 * bm_score_high(duels_won_per90, 10, 28), 4) AS wing_score,
    ROUND(0.8 - ABS(COALESCE(possession, 0) / 100 - .50) - ABS(COALESCE(long_pass_rate, 0) / 100 - .15)
        - ABS(COALESCE(cross_rate, 0) / 100 - .06), 4) AS balanced_score
  FROM feat
)
SELECT
  s.season, s.country, s.league, s.team, s.ha, s.sample_match_count, s.from_date, s.to_date, s.minutes,
  s.possession, s.passes_per90, s.pass_accuracy, s.long_pass_rate, s.final_third_pass_rate, s.cross_rate,
  s.shots_per_box_touch, s.defensive_actions_per90, s.clearance_rate, s.duels_won_per90,
  s.possession_score, s.long_ball_score, s.counter_score, s.wing_score, s.balanced_score,
  CASE WHEN s.possession IS NOT NULL AND s.pass_try IS NOT NULL THEN r.best_id END    AS style_cluster_id,
  CASE WHEN s.possession IS NOT NULL AND s.pass_try IS NOT NULL THEN r.best_label END AS style_label,
  CASE WHEN s.possession IS NOT NULL AND s.pass_try IS NOT NULL
       THEN ROUND(GREATEST(0.55, LEAST(0.95, 0.55 + (r.best_score - r.second_score) * 0.20)), 4) END AS style_confidence
FROM score s
CROSS JOIN LATERAL (
  SELECT
    (ARRAY_AGG(v.id    ORDER BY v.sc DESC, v.id))[1] AS best_id,
    (ARRAY_AGG(v.label ORDER BY v.sc DESC, v.id))[1] AS best_label,
    (ARRAY_AGG(v.sc    ORDER BY v.sc DESC, v.id))[1] AS best_score,
    (ARRAY_AGG(v.sc    ORDER BY v.sc DESC, v.id))[2] AS second_score
  FROM (VALUES (1, 'ポゼッション',   s.possession_score),
               (2, 'ロングボール',   s.long_ball_score),
               (3, '堅守速攻',       s.counter_score),
               (4, 'サイドアタック', s.wing_score),
               (5, 'バランス',       s.balanced_score)) v(id, label, sc)
) r;

COMMIT;

-- ############################################################
-- 29_bm_m041.sql
-- ############################################################
-- 29: BM_M041 チームの強さビュー（19 の後）
-- BM_M041 作り直し（チームの強さ・安定性）
--  旧: team_strength_profile テーブルに、Java で集計した直近5試合・ホーム/アウェー強度・上位/下位相手成績・レーティングを INSERT
--      （実行のたびに重複）。BM_M040 と同じくキーの読み方が違っていて「1試合 × チーム」ごとに1行になっていた。
--      順位は試合終了時点のサイト順位（その試合の結果を含む）で上位/下位を分けていた。シーズンは未設定。
--  新: BM_M031 の明細 surface_overview_match（1試合 × チーム。勝敗・勝ち点・得失点・ラウンド）と
--      順位ビュー surface_overview_standing から計算するビュー。Java のクラス・テーブルは不要。
--      欠けていた試合を後から入れても、直近5試合・試合前の順位・指数はすべて計算し直される。
--  ※ BM_M031（19）の DDL の後に実行すること。M031 の DDL を流し直したら続けてこの DDL も流すこと。
--
--  ビュー
--   team_strength_match    1試合 × チーム（ラウンド番号のある試合）。試合前の自分/相手の順位、相手の格（UPPER/LOWER/SAME）、
--                          この試合までの直近5試合の勝ち点・得失点差・フォーム指数（推移をグラフにできる）
--   team_strength_profile  チーム × シーズン（最新ラウンドの時点）。旧 M041 の全項目
--
--  項目の決め方
--   - 勝ち点は M031 の points（勝ち点マスタ・PK 決着の設定を反映）。勝ちは result = 'W'。
--   - 直近5試合: ラウンド番号の順で、その試合を含む直近5試合（旧実装は記録日の順）。
--   - 試合前の順位: その試合より前の最後のラウンド終了時点の計算順位（surface_overview_standing.rank_no）。
--       第1ラウンドなど前のラウンドが無い試合は NULL（上位/下位の集計に入らない）。
--       旧実装は「試合終了時点のサイト順位」を使っていたため、その試合の結果で順位が入れ替わった相手を逆に分類することがあった。
--   - strength_index（ホーム/アウェー別）: 0.6 × 勝ち点/試合 + 0.3 × 得失点差/試合 + 0.1 × 勝率（0〜1）。旧実装と同じ。
--   - vs_upper_performance: 試合前に自分より上位だった相手との試合の平均勝ち点。
--   - vs_lower_drop_rate: 試合前に自分より下位だった相手との試合で失った勝ち点 ÷ (3 × 試合数)（%）。
--       旧実装と同じく最大を 3 点とする（勝ち点マスタで勝ちが 3 点でないリーグでは目安）。
--   - form_index: 0.7 × min(直近5試合の勝ち点 ÷ 15, 1) + 0.3 × (直近5試合の得失点差 + 10) ÷ 20 を 0〜1 に丸めたもの。
--   - elo_like_rating: 1500 + (勝ち点/試合 − 1.5) × 100 + 得失点差/試合 × 25 + 勝率 × 50 + form_index × 30（旧実装の暫定式のまま）。
--       本当の Elo（試合ごとに相手の強さで更新）ではない。
--   - 試合が無い場合の値は NULL（旧実装は 0 で、「強度 0」と「試合なし」が区別できなかった）。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('team_strength_profile', 'team_strength_match')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'team_strength_profile';

-- ===== 1試合 × チーム =====
CREATE VIEW team_strength_match AS
WITH m AS (
  SELECT season, country, league, team, opponent, ha, match_id, round_no, match_time,
         result, points, goals_for, goals_against, (goals_for - goals_against) AS goal_diff
  FROM surface_overview_match
  WHERE round_no IS NOT NULL
),
rounds AS (
  -- そのリーグの直前のラウンド（明細にあるラウンドの中で）
  SELECT season, country, league, round_no,
         LAG(round_no) OVER (PARTITION BY season, country, league ORDER BY round_no) AS prev_round_no
  FROM (SELECT DISTINCT season, country, league, round_no FROM surface_overview_match) d
),
st AS (
  SELECT season, country, league, as_of_round, team, rank_no FROM surface_overview_standing
),
pre AS (
  -- 順位は1回だけ計算して、直前のラウンドの順位を等号で結合する（試合ごとに順位を計算し直さない）
  SELECT m.*,
    tr.rank_no AS team_rank_before,
    orr.rank_no AS opponent_rank_before
  FROM m
  JOIN rounds rd
    ON rd.season = m.season AND rd.country = m.country AND rd.league = m.league AND rd.round_no = m.round_no
  LEFT JOIN st tr
    ON tr.season = m.season AND tr.country = m.country AND tr.league = m.league
   AND tr.as_of_round = rd.prev_round_no AND tr.team = m.team
  LEFT JOIN st orr
    ON orr.season = m.season AND orr.country = m.country AND orr.league = m.league
   AND orr.as_of_round = rd.prev_round_no AND orr.team = m.opponent
),
roll AS (
  SELECT pre.*,
    CASE WHEN opponent_rank_before < team_rank_before THEN 'UPPER'
         WHEN opponent_rank_before > team_rank_before THEN 'LOWER'
         WHEN opponent_rank_before = team_rank_before THEN 'SAME' END::VARCHAR(5) AS opponent_tier,
    COUNT(*)       OVER w5 AS last5_matches,
    SUM(points)    OVER w5 AS last5_points,
    SUM(goal_diff) OVER w5 AS last5_goal_diff,
    ROW_NUMBER() OVER (PARTITION BY season, country, league, team ORDER BY round_no DESC, match_time DESC NULLS LAST) AS recency_no
  FROM pre
  WINDOW w5 AS (PARTITION BY season, country, league, team ORDER BY round_no, match_time NULLS FIRST
                ROWS BETWEEN 4 PRECEDING AND CURRENT ROW)
)
SELECT roll.*,
  ROUND(0.7 * LEAST(1.0, GREATEST(0.0, last5_points / 15.0))
      + 0.3 * LEAST(1.0, GREATEST(0.0, (last5_goal_diff + 10) / 20.0)), 4) AS form_index
FROM roll;

-- ===== チーム × シーズン（最新ラウンドの時点）=====
CREATE VIEW team_strength_profile AS
WITH agg AS (
  SELECT season, country, league, team,
    MAX(match_time)::DATE                                        AS snapshot_date,
    MAX(round_no)                                                AS as_of_round,
    COUNT(*)::INTEGER                                            AS total_matches,
    SUM(points)::INTEGER                                         AS total_points,
    COUNT(*) FILTER (WHERE result = 'W')::INTEGER                AS total_wins,
    SUM(goal_diff)::INTEGER                                      AS total_goal_diff,
    COUNT(*) FILTER (WHERE ha = 'H')::INTEGER                    AS home_matches,
    SUM(points)    FILTER (WHERE ha = 'H')                       AS home_points,
    COUNT(*)       FILTER (WHERE ha = 'H' AND result = 'W')      AS home_wins,
    SUM(goal_diff) FILTER (WHERE ha = 'H')                       AS home_goal_diff,
    COUNT(*) FILTER (WHERE ha = 'A')::INTEGER                    AS away_matches,
    SUM(points)    FILTER (WHERE ha = 'A')                       AS away_points,
    COUNT(*)       FILTER (WHERE ha = 'A' AND result = 'W')      AS away_wins,
    SUM(goal_diff) FILTER (WHERE ha = 'A')                       AS away_goal_diff,
    COUNT(*) FILTER (WHERE opponent_tier = 'UPPER')::INTEGER     AS upper_matches,
    SUM(points) FILTER (WHERE opponent_tier = 'UPPER')           AS upper_points,
    COUNT(*) FILTER (WHERE opponent_tier = 'LOWER')::INTEGER     AS lower_matches,
    SUM(3 - points) FILTER (WHERE opponent_tier = 'LOWER')       AS lower_dropped_points
  FROM team_strength_match
  GROUP BY season, country, league, team
),
latest AS (
  SELECT season, country, league, team, last5_matches, last5_points, last5_goal_diff, form_index
  FROM team_strength_match
  WHERE recency_no = 1
)
SELECT
  a.season, a.country, a.league, a.team, a.snapshot_date, a.as_of_round,
  a.total_matches, a.total_points, a.total_wins, a.total_goal_diff,
  l.last5_matches::INTEGER   AS last5_matches,
  l.last5_points::INTEGER    AS last5_points,
  l.last5_goal_diff::INTEGER AS last5_goal_diff,
  a.home_matches, a.away_matches,
  CASE WHEN a.home_matches > 0 THEN ROUND((0.6 * a.home_points + 0.3 * a.home_goal_diff + 0.1 * a.home_wins) / a.home_matches, 4) END AS home_strength_index,
  CASE WHEN a.away_matches > 0 THEN ROUND((0.6 * a.away_points + 0.3 * a.away_goal_diff + 0.1 * a.away_wins) / a.away_matches, 4) END AS away_strength_index,
  a.upper_matches,
  CASE WHEN a.upper_matches > 0 THEN ROUND(a.upper_points::NUMERIC / a.upper_matches, 4) END AS vs_upper_performance,
  a.lower_matches,
  CASE WHEN a.lower_matches > 0 THEN ROUND(a.lower_dropped_points * 100.0 / (3 * a.lower_matches), 2) END AS vs_lower_drop_rate,
  l.form_index,
  ROUND(1500
      + (a.total_points::NUMERIC / a.total_matches - 1.5) * 100
      + (a.total_goal_diff::NUMERIC / a.total_matches) * 25
      + (a.total_wins::NUMERIC / a.total_matches) * 50
      + COALESCE(l.form_index, 0) * 30, 2) AS elo_like_rating
FROM agg a
LEFT JOIN latest l
  ON l.season = a.season AND l.country = a.country AND l.league = a.league AND l.team = a.team;

COMMIT;

-- ############################################################
-- 30_bm_m042.sql
-- ############################################################
-- 30: BM_M042 試合前要約ビュー（08・19・26 の後）
-- BM_M042 作り直し（試合前レポート用の要約）
--  旧: pre_match_summary_profile テーブルに、Java で集計した直近5試合・先制率・追いつき率・終盤得失点率などを INSERT
--      （実行のたびに重複）。BM_M040/M041 と同じくキーの読み方が違っていて「1試合 × チーム」ごとに1行になっていた。
--      セットプレー得点は元データに無いため常に NULL（率は 0）。
--  新: BM_M031 の明細 surface_overview_match（勝敗・得失点・先制）と BM_M038 のビュー match_team_goal_event（得点/失点の時刻）から
--      計算するビュー。Java のクラス・テーブルは不要。欠けていた試合を後から入れても計算し直される。
--  ※ 08（関数 bm_rate_pct）・BM_M031（19）・BM_M038（26）の DDL の後に実行すること。
--
--  ビュー
--   pre_match_summary_profile  チーム × シーズン × H/A/*（* = ホーム・アウェー合算。H = ホーム戦だけ、A = アウェー戦だけ）
--
--  項目の決め方（率は %。分母が 0 なら NULL。旧実装は 0 にしていた）
--   - 直近5試合: ラウンド番号の順（ラウンドが無い試合は試合日時の順で後ろ）。ha = H/A はホーム戦/アウェー戦だけの直近5試合。
--       last5_result_string は新しい順に W/D/L を "-" でつなぐ（旧実装と同じ）。PK 決着は PK の勝敗（M031 の result）。
--   - first_goal_rate:            先制した試合 ÷ 先制が分かる試合（どちらかが先に得点した試合。0-0・不明は分母に入らない）
--   - win_after_scoring_first_rate: 先制した試合のうち勝った割合
--   - comeback_rate（追いつき率）:  先制された試合のうち引き分け以上（旧実装と同じ）。comeback_win_rate は勝ちだけ（追加）
--   - late_scoring_rate / late_conceding_rate: 76 分以降（後半）に得点 / 失点した試合 ÷ 得点の時刻が分かる試合
--       （BM_M034 の時系列データがある試合だけ。旧実装は時刻の読めない行を 0 秒扱いにしていた）。
--       ※ BM_M038 の late（80 分以降）とは区切りが違う（旧 M042 の 76 分のまま）。
--   - clean_sheet_rate: 無失点の試合 ÷ 試合数、both_teams_to_score_rate: 両チーム得点の試合 ÷ 試合数
--   - セットプレー得点関与率は元データに無いので出さない。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('pre_match_summary_profile')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 旧テーブル用の採番が作られていれば消す
DELETE FROM seq_counter WHERE table_name = 'pre_match_summary_profile';

CREATE VIEW pre_match_summary_profile AS
WITH late AS (
  -- 76 分以降（後半）の得点 / 失点の有無。時刻が分かる試合（M034 のデータがある試合）だけ行がある
  SELECT l.season, l.country, l.league, l.round_no, l.team, l.opponent, l.ha,
         BOOL_OR(e.event_type = 'SCORED'   AND e.event_minute >= 76 AND COALESCE(e.half, 2) = 2) AS late_scored,
         BOOL_OR(e.event_type = 'CONCEDED' AND e.event_minute >= 76 AND COALESCE(e.half, 2) = 2) AS late_conceded
  FROM match_team_snapshot_last l
  LEFT JOIN match_team_goal_event e
    ON e.season = l.season AND e.country = l.country AND e.league = l.league
   AND e.round_no = l.round_no AND e.team = l.team AND e.opponent = l.opponent AND e.ha = l.ha
  WHERE l.finished
  GROUP BY l.season, l.country, l.league, l.round_no, l.team, l.opponent, l.ha
),
m AS (
  SELECT s.season, s.country, s.league, s.team, s.opponent, s.ha, s.round_no, s.match_time,
         s.result, s.goals_for, s.goals_against, s.first_goal,
         COALESCE(lt.late_scored, FALSE)   AS late_scored,
         COALESCE(lt.late_conceded, FALSE) AS late_conceded,
         (lt.team IS NOT NULL)             AS late_known,
         ROW_NUMBER() OVER (PARTITION BY s.season, s.country, s.league, s.team
                            ORDER BY s.round_no DESC NULLS LAST, s.match_time DESC NULLS LAST) AS rn_all,
         ROW_NUMBER() OVER (PARTITION BY s.season, s.country, s.league, s.team, s.ha
                            ORDER BY s.round_no DESC NULLS LAST, s.match_time DESC NULLS LAST) AS rn_ha
  FROM surface_overview_match s
  LEFT JOIN late lt
    ON lt.season = s.season AND lt.country = s.country AND lt.league = s.league
   AND lt.round_no = s.round_no AND lt.team = s.team AND lt.opponent = s.opponent AND lt.ha = s.ha
),
-- ha = '*' は全試合の直近5、H / A はそれぞれの直近5
m2 AS (
  SELECT m.*, '*'::CHAR(1) AS grp_ha, rn_all AS rn FROM m
  UNION ALL
  SELECT m.*, m.ha, rn_ha FROM m
)
SELECT
  season, country, league, team, grp_ha AS ha,
  MAX(match_time)::DATE                                                     AS snapshot_date,
  COUNT(*)::INTEGER                                                         AS match_count,
  STRING_AGG(result::TEXT, '-' ORDER BY rn) FILTER (WHERE rn <= 5)                AS last5_result_string,
  ROUND(AVG(goals_for)     FILTER (WHERE rn <= 5), 2)                       AS last5_avg_goals,
  ROUND(AVG(goals_against) FILTER (WHERE rn <= 5), 2)                       AS last5_avg_goals_conceded,
  COUNT(*) FILTER (WHERE first_goal = 'T')::INTEGER                         AS scored_first_count,
  COUNT(*) FILTER (WHERE first_goal = 'O')::INTEGER                         AS conceded_first_count,
  bm_rate_pct(COUNT(*) FILTER (WHERE first_goal = 'T'),
              COUNT(*) FILTER (WHERE first_goal IN ('T', 'O')))             AS first_goal_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE first_goal = 'T' AND result = 'W'),
              COUNT(*) FILTER (WHERE first_goal = 'T'))                     AS win_after_scoring_first_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE first_goal = 'O' AND result IN ('W', 'D')),
              COUNT(*) FILTER (WHERE first_goal = 'O'))                     AS comeback_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE first_goal = 'O' AND result = 'W'),
              COUNT(*) FILTER (WHERE first_goal = 'O'))                     AS comeback_win_rate,
  COUNT(*) FILTER (WHERE late_known)::INTEGER                               AS late_known_count,
  bm_rate_pct(COUNT(*) FILTER (WHERE late_scored),   COUNT(*) FILTER (WHERE late_known)) AS late_scoring_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE late_conceded), COUNT(*) FILTER (WHERE late_known)) AS late_conceding_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE goals_against = 0), COUNT(*))          AS clean_sheet_rate,
  bm_rate_pct(COUNT(*) FILTER (WHERE goals_for > 0 AND goals_against > 0), COUNT(*)) AS both_teams_to_score_rate
FROM m2
GROUP BY season, country, league, team, grp_ha;

COMMIT;

-- ############################################################
-- 31_bm_m043_drop.sql
-- ############################################################
-- 31: BM_M043 削除（model_evaluation_summary）
-- BM_M043 削除（予測モデル評価サマリ）
--  旧: model_evaluation_summary テーブル。BookDataEntity（試合のスナップショット）から modelName・accuracy などを
--      リフレクションで探していたが、BookDataEntity にそれらの項目は無いため、1行も登録されない（処理が空振りしていた）。
--  予測モデルの評価結果は、モデルの学習・評価の処理が作る別の記録なので、試合データの BM からは作らない。
--  必要になったら、評価処理から UPSERT するテーブル（モデル × バージョン × 目的変数 × 検証方法 × fold/シーズン）を別途作る。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('model_evaluation_summary')
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DELETE FROM seq_counter WHERE table_name = 'model_evaluation_summary';

COMMIT;

-- ############################################################
-- 32_bm_m044_drop.sql
-- ############################################################
-- 32: BM_M044 削除（model_calibration_detail）
-- BM_M044 削除（予測モデルのキャリブレーション詳細）
--  旧: model_calibration_detail テーブル。BM_M043 と同じく、BookDataEntity（試合のスナップショット）から modelName・predictedProb などを
--      リフレクションで探していたが、BookDataEntity にそれらの項目は無いため、1行も登録されない（処理が空振りしていた）。
--  キャリブレーション（予測確率のビンごとの平均予測値と実測率）は、モデルの予測結果から作るものなので、試合データの BM からは作らない。
--  必要になったら、予測結果を保存するテーブル（1行 = 1予測: モデル・バージョン・目的変数・試合・予測確率・実際の結果）を作り、
--  ビン集計はビュー（WIDTH_BUCKET(predicted_prob, 0, 1, 10) で GROUP BY）で出す。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('model_calibration_detail')
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

DELETE FROM seq_counter WHERE table_name = 'model_calibration_detail';

COMMIT;

-- ############################################################
-- 33_bm_m019_old_drop.sql
-- ############################################################
-- 33: BM_M019 / BM_M020 旧テーブルの削除（match_classification_result / match_classification_result_count）
-- 旧 MatchClassificationResultRepository（insertBatch）と MatchClassificationResultCountRepository が書いていたテーブル。
-- 今は classify_result_data（明細）とビュー classify_result_data_detail（分類モード別の件数）に置き換わり、どこからも書かれない。
-- ※ 画面などがこの2テーブルを読んでいる場合は、先にそちらを classify_result_data / classify_result_data_detail に切り替えてから流すこと。
-- 付属のシーケンス（*_id_seq）は列の持ち物なので、テーブルと一緒に消える。何度流してもよい。
BEGIN;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname, c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname IN ('match_classification_result_count', 'match_classification_result')
    ORDER BY CASE c.relkind WHEN 'v' THEN 0 ELSE 1 END, c.relname
  LOOP
    IF r.relkind = 'v' THEN
      EXECUTE format('DROP VIEW IF EXISTS %I CASCADE', r.relname);
    ELSIF r.relkind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', r.relname);
    END IF;
  END LOOP;
END
$$;

-- 列の持ち物になっていない（手で作った）シーケンスが残っていれば消す
DROP SEQUENCE IF EXISTS match_classification_result_id_seq;
DROP SEQUENCE IF EXISTS match_classification_result_count_id_seq;

COMMIT;

-- ############################################################
-- 34_analyze_error_field.sql
-- ############################################################
-- 34: analyze_error_match に「どの項目が原因か」の列を追加（既存データは残す。22 を流し直す場合は不要。何度流してもよい）
--   error_field: 原因の項目名（カンマ区切り。例: homeScore / country,league / dataCategory。無ければ空文字）。一意キーに追加
--   error_value: 原因の項目のそのときの値（「項目名=値」を "; " 区切り）
--   エラー種別に MISSING_VALUE（必須項目が空）/ INVALID_VALUE（値が読めない）を追加（列は文字列なので DDL の変更は不要）
BEGIN;

ALTER TABLE analyze_error_match ADD COLUMN IF NOT EXISTS error_field TEXT NOT NULL DEFAULT '';
ALTER TABLE analyze_error_match ADD COLUMN IF NOT EXISTS error_value TEXT;

COMMENT ON COLUMN analyze_error_match.error_field IS '原因の項目名（カンマ区切り。無ければ空文字）';
COMMENT ON COLUMN analyze_error_match.error_value IS '原因の項目のそのときの値（項目名=値 を ; 区切り）';

-- 一意キーを作り直す（error_field を追加）
ALTER TABLE analyze_error_match DROP CONSTRAINT IF EXISTS uq_analyze_error_match;
ALTER TABLE analyze_error_match ADD CONSTRAINT uq_analyze_error_match
  UNIQUE (bm_number, error_type, country, league, data_category, home_team_name, away_team_name, error_field);

-- 集計ビューに error_field を追加
DROP VIEW IF EXISTS analyze_error_summary;
CREATE VIEW analyze_error_summary AS
SELECT
  bm_number, error_type, country, league, error_field,
  COUNT(*)::INTEGER                                   AS match_count,       -- 登録できなかった試合（行）数
  COUNT(*) FILTER (WHERE NOT resolved_flg)::INTEGER   AS unresolved_count,  -- うち未対応
  SUM(occurred_count)::INTEGER                        AS occurred_total,    -- 発生回数の合計
  MIN(first_occurred_at)                              AS first_occurred_at,
  MAX(last_occurred_at)                               AS last_occurred_at,
  (ARRAY_AGG(error_message ORDER BY last_occurred_at DESC))[1] AS latest_message
FROM analyze_error_match
GROUP BY bm_number, error_type, country, league, error_field;

COMMIT;

-- ############################################################
-- 35_bm_b011_csv_export.sql
-- ############################################################
-- 35: BM_B011 CSV出力（試合ごとの判定ビュー・出力管理テーブル）
-- BM_B011 作り直し（リアルタイムデータ static_data から、同じ試合のデータ群を1つの CSV にして S3 に置く）
--  旧: 毎回 static_data の全試合を EXISTS で集計し、S3 の全 CSV をダウンロードして seq を読み、既存かどうかを判定していた。
--      ファイル名「ホーム-アウェー.csv」を既存 CSV の読み込み（数字.csv だけ対象）が拾えず、毎回全試合を作り直していた。
--      「(ハーフタイム or 第一ハーフ) かつ (終了済 or 第二ハーフ)」で対象にしていたため、試合中でも CSV ができ、
--      終了済の1行だけの CSV もできていた。
--  新: 試合ごとに「前半・ハーフタイム・後半・終了済」が揃っているかをビューで判定し、作った CSV は csv_export_manage に記録する。
--      行数か最後の seq が変わった試合（データが後から追加された試合を含む）だけ作り直す。
--
--  ビュー
--   csv_export_target  match_id × ホーム × アウェーごとの判定（4つ揃っていれば eligible = TRUE）
--  テーブル
--   csv_export_manage  作った CSV（または対象外と判定した試合）の記録。match_id × ホーム × アウェーごとに1行（UPSERT）
--
--  判定（seq は seq_key 末尾の数字。"zRLNYw4L-37" → 37。文字列順ではなく数値順で並べる）
--   ハーフタイム: times = 'ハーフタイム'      終了済: times = '終了済'
--   前半:  時間が数字表記（"28:12" "45+1'" など）の行で、最初のハーフタイムより前
--   後半:  時間が数字表記の行で、最後のハーフタイムより後、かつ最初の終了済より前
--   「第一ハーフ」「第二ハーフ」など数字でない表記の行は数えない（CSV にも入れない）。
--   PK 戦の行（times に「ペナルティ」を含む）は数えない。
BEGIN;

DROP VIEW IF EXISTS csv_export_target;

CREATE VIEW csv_export_target AS
WITH r AS (
  SELECT
    d.match_id,
    d.home_team_name,
    d.away_team_name,
    d.data_category,
    d.record_time,
    BTRIM(d.times) AS times,
    NULLIF(SUBSTRING(d.seq_key FROM '([0-9]+)$'), '')::BIGINT AS seq_no
  FROM static_data d
  WHERE d.match_id IS NOT NULL AND BTRIM(d.match_id) <> ''
    AND d.home_team_name IS NOT NULL AND BTRIM(d.home_team_name) <> ''
    AND d.away_team_name IS NOT NULL AND BTRIM(d.away_team_name) <> ''
),
w AS (
  -- 試合ごとのハーフタイム・終了済の位置（seq）を各行に付ける（1回の走査で済むようにウィンドウ関数）
  SELECT r.*,
    MIN(seq_no) FILTER (WHERE times = 'ハーフタイム') OVER g AS ht_first,
    MAX(seq_no) FILTER (WHERE times = 'ハーフタイム') OVER g AS ht_last,
    MIN(seq_no) FILTER (WHERE times = '終了済')       OVER g AS fin_first
  FROM r
  WINDOW g AS (PARTITION BY match_id, home_team_name, away_team_name)
),
m AS (
  SELECT
    match_id, home_team_name, away_team_name,
    COUNT(*)::INTEGER AS row_count,
    MAX(seq_no)       AS max_seq_no,
    MAX(ht_first)     AS ht_first,
    MAX(fin_first)    AS fin_first,
    COUNT(*) FILTER (WHERE times ~ '^[0-9:+'']+$' AND seq_no < ht_first)::INTEGER AS first_half_rows,
    COUNT(*) FILTER (WHERE times ~ '^[0-9:+'']+$' AND seq_no > ht_last AND seq_no < fin_first)::INTEGER AS second_half_rows,
    COALESCE(MAX(data_category) FILTER (WHERE data_category ~ 'ラウンド\s*[0-9０-９]+'),
             MAX(data_category)) AS data_category,
    MAX(record_time)  AS last_record_time
  FROM w
  GROUP BY match_id, home_team_name, away_team_name
)
SELECT
  match_id, home_team_name, away_team_name, data_category,
  row_count, max_seq_no, last_record_time,
  (ht_first IS NOT NULL)  AS has_halftime,
  (fin_first IS NOT NULL) AS has_finished,
  first_half_rows,
  second_half_rows,
  (ht_first IS NOT NULL AND fin_first IS NOT NULL AND first_half_rows > 0 AND second_half_rows > 0) AS eligible
FROM m;

-- 作った CSV の記録（match_id × ホーム × アウェーごとに1行。作り直しは UPSERT で上書き）
--  status: EXPORTED = S3 に置いた / SKIPPED = 条件（国・リーグ・スコアの制限、ラウンド不明など）で対象外
--  row_count・max_seq_no・source_data_category（static_data 側のカテゴリ）が csv_export_target と同じなら次回は何もしない
--  （データが追加された・カテゴリが直された試合だけ作り直す）
--  data_category は CSV に使ったカテゴリ（future_master で補った場合はその値）
CREATE TABLE IF NOT EXISTS csv_export_manage (
  match_id         VARCHAR(100)  NOT NULL,
  home_team_name   VARCHAR(200)  NOT NULL,
  away_team_name   VARCHAR(200)  NOT NULL,
  status           VARCHAR(20)   NOT NULL,
  csv_key          VARCHAR(500),
  data_category    VARCHAR(300),
  source_data_category VARCHAR(300),
  season           VARCHAR(20),
  row_count        INTEGER       NOT NULL,
  max_seq_no       BIGINT,
  csv_row_count    INTEGER,
  skip_reason      VARCHAR(500),
  exported_at      TIMESTAMP(0) WITH TIME ZONE,
  register_id      VARCHAR(100)  NOT NULL,
  register_time    TIMESTAMP(0) WITH TIME ZONE NOT NULL,
  update_id        VARCHAR(100),
  update_time      TIMESTAMP(0) WITH TIME ZONE,
  CONSTRAINT pk_csv_export_manage PRIMARY KEY (match_id, home_team_name, away_team_name),
  CONSTRAINT ck_csv_export_manage_status CHECK (status IN ('EXPORTED', 'SKIPPED'))
);

-- 同じ CSV キーを2つの試合が使わないように（同じフォルダで同じ対戦があれば _2 を付ける）
CREATE UNIQUE INDEX IF NOT EXISTS uq_csv_export_manage_csv_key
  ON csv_export_manage (csv_key) WHERE csv_key IS NOT NULL;

-- ビューの集計用（無ければ作る）
CREATE INDEX IF NOT EXISTS idx_static_data_match_id ON static_data (match_id, home_team_name, away_team_name);

COMMIT;

-- ############################################################
-- 36_web_dashboard_team_rate.sql
-- ############################################################
-- 36: トップ画面（Dashboard）用 チームの今季の得点・失点（BM_M031 の後）
--  ビュー dashboard_team_rate
--   国・リーグごとの最新シーズンについて、チーム × H/A の試合数・1試合平均得点・平均失点と、
--   同じリーグ × H/A の平均（リーグ平均）を返す。Web の DashboardForecastService が
--   「得点しやすさ（攻）」「失点しにくさ（守）」と、勝ち・分け・負けの確率・ゴール数の見込みの計算に使う。
--   ha = 'H' はホーム戦だけ、'A' はアウェー戦だけの成績。
--  ※ BM_M031（surface_overview_match）の DDL の後に実行すること。
BEGIN;

DROP VIEW IF EXISTS dashboard_team_rate;

CREATE VIEW dashboard_team_rate AS
WITH cur AS (
  -- 国・リーグごとの最新シーズン
  SELECT country, league, MAX(season) AS season
  FROM surface_overview_match
  GROUP BY country, league
),
s AS (
  SELECT m.country, m.league, m.season, m.team, m.ha, m.goals_for, m.goals_against
  FROM surface_overview_match m
  JOIN cur c ON c.country = m.country AND c.league = m.league AND c.season = m.season
)
SELECT
  t.country, t.league, t.season, t.team, t.ha,
  t.match_count,
  t.avg_goals_for,
  t.avg_goals_against,
  l.league_avg_goals_for,
  l.league_avg_goals_against
FROM (
  SELECT country, league, season, team, ha,
         COUNT(*)::INTEGER                      AS match_count,
         ROUND(AVG(goals_for), 3)               AS avg_goals_for,
         ROUND(AVG(goals_against), 3)           AS avg_goals_against
  FROM s
  GROUP BY country, league, season, team, ha
) t
JOIN (
  SELECT country, league, season, ha,
         ROUND(AVG(goals_for), 3)               AS league_avg_goals_for,
         ROUND(AVG(goals_against), 3)           AS league_avg_goals_against
  FROM s
  GROUP BY country, league, season, ha
) l ON l.country = t.country AND l.league = t.league AND l.season = t.season AND l.ha = t.ha;

COMMIT;
