#!/usr/bin/env bash
# soccer_bm CSV import/export helper (Docker版・コア5テーブル専用)
set -euo pipefail

# ===== 設定 =====
SERVICE_NAME="db"
DB_USER="postgres"

# デフォルト（master 側）
DB_NAME_MASTER="soccer_bm_master"

# data テーブル用
DB_NAME_DATA="soccer_bm"

# ★ スキーマ名（ここを public に修正）
SCHEMA="public"

DUMPDIR="/Users/shiraishitoshio/dumps/soccer_bm_dumps"
FILE_PREFIX="soccer_bm_"
FILE_SUFFIX=".csv"
ZIP_EXT=".zip"

TABLES_CORE=(
  country_league_master
  country_league_season_master
  team_member_master
  future_master
  data
)

# ===== 共通関数 =====
dc() {
  if docker compose version >/dev/null 2>/dev/null; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# テーブルごとに使うDBを振り分け
get_db_name() {
  local t="$1"
  if [[ "$t" == "data" ]]; then
    echo "$DB_NAME_DATA"
  else
    echo "$DB_NAME_MASTER"
  fi
}

# DB名を引数に取る psql ラッパ
compose_psql() {
  local dbname="$1"
  local sql="$2"
  dc exec -T "$SERVICE_NAME" \
    psql -U "$DB_USER" -d "$dbname" -v ON_ERROR_STOP=1 -c "$sql"
}

# ===== FORCE_NULL 対象列検出 =====
get_force_null_cols() {
  local t="$1"
  local dbname; dbname="$(get_db_name "$t")"

  dc exec -T "$SERVICE_NAME" \
    psql -U "$DB_USER" -d "$dbname" -At -v ON_ERROR_STOP=1 -c "
      SELECT COALESCE(string_agg('\"' || column_name || '\"', ',' ORDER BY ordinal_position),'')
      FROM information_schema.columns
      WHERE table_schema='${SCHEMA}'
        AND table_name='${t}'
        AND data_type IN ('timestamp with time zone','timestamp without time zone','date');
    " | tr -d '\r\n'
}

build_copy_opts() {
  local t="$1"
  local fnc; fnc="$(get_force_null_cols "$t")"
  local opts="(FORMAT csv, HEADER true, DELIMITER ',', ENCODING 'UTF8', NULL '', QUOTE '\"'"
  if [[ -n "${fnc//[[:space:]]/}" ]]; then
    opts="${opts}, FORCE_NULL (${fnc})"
  fi
  echo "${opts})"
}

# ===== シーケンス同期 =====
sync_seq_auto() {
  local t="$1"
  local dbname; dbname="$(get_db_name "$t")"

  echo "🔧 Syncing sequences for ${dbname}:${SCHEMA}.${t}"
  compose_psql "$dbname" "
    DO \$\$
    DECLARE
      tname text := '${t}';
      sch   text := '${SCHEMA}';
      col   text;
      seqreg regclass;
      sqltext text;
    BEGIN
      -- seq または id を優先して検出
      FOR col IN
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = sch
          AND table_name   = tname
          AND column_name IN ('seq','id')
        ORDER BY CASE column_name WHEN 'seq' THEN 1 WHEN 'id' THEN 2 ELSE 3 END
      LOOP
        SELECT pg_get_serial_sequence(format('%I.%I', sch, tname), col) INTO seqreg;
        IF seqreg IS NOT NULL THEN
          sqltext := format(\$fmt\$
            DO \$do\$
            DECLARE v_max bigint;
            BEGIN
              SELECT MAX(%I) INTO v_max FROM %I.%I;
              IF v_max IS NULL THEN
                PERFORM setval(%L, 1, false);
              ELSE
                PERFORM setval(%L, v_max, true);
              END IF;
            END
            \$do\$;
          \$fmt\$,
            col, sch, tname,
            seqreg::text,
            seqreg::text
          );
          EXECUTE sqltext;
          RETURN;
        END IF;
      END LOOP;

      -- Identity列にも対応
      SELECT column_name INTO col
      FROM information_schema.columns
      WHERE table_schema = sch
        AND table_name   = tname
        AND is_identity = 'YES'
      LIMIT 1;

      IF col IS NOT NULL THEN
        SELECT pg_get_serial_sequence(format('%I.%I', sch, tname), col) INTO seqreg;
        IF seqreg IS NOT NULL THEN
          sqltext := format(\$fmt\$
            DO \$do\$
            DECLARE v_max bigint;
            BEGIN
              SELECT MAX(%I) INTO v_max FROM %I.%I;
              IF v_max IS NULL THEN
                PERFORM setval(%L, 1, false);
              ELSE
                PERFORM setval(%L, v_max, true);
              END IF;
            END
            \$do\$;
          \$fmt\$,
            col, sch, tname,
            seqreg::text,
            seqreg::text
          );
          EXECUTE sqltext;
        END IF;
      END IF;
    END
    \$\$;
  "
  echo "✅ Sequences synced for ${t}"
}

# ===== CSV Export =====
export_table() {
  local t="$1"
  local dbname; dbname="$(get_db_name "$t")"
  local outfile="${DUMPDIR}/${FILE_PREFIX}${t}${FILE_SUFFIX}"

  echo "🔼 Export ${dbname}:${SCHEMA}.${t} -> ${outfile}"
  dc exec -T "$SERVICE_NAME" \
    psql -U "$DB_USER" -d "$dbname" -v ON_ERROR_STOP=1 \
      -c "\copy (SELECT * FROM \"${SCHEMA}\".\"${t}\") TO STDOUT WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')" \
    > "$outfile"

  # data テーブルだけ zip 化
  if [[ "$t" == "data" ]]; then
    local zipfile="${outfile}${ZIP_EXT}"
    echo "🗜️  Zipping ${outfile} -> ${zipfile}"
    (cd "$DUMPDIR" && zip -q -j "$(basename "$zipfile")" "$(basename "$outfile")")
    rm -f "$outfile"
  fi
}

export_core() {
  mkdir -p "$DUMPDIR"
  for t in "${TABLES_CORE[@]}"; do
    export_table "$t"
  done
  echo "✅ Export completed for 5 core tables."
}

# ===== CSV Import =====
import_table() {
  local t="$1"
  local dbname; dbname="$(get_db_name "$t")"
  local infile="${DUMPDIR}/${FILE_PREFIX}${t}${FILE_SUFFIX}"
  local zipfile="${infile}${ZIP_EXT}"

  if [[ ! -f "$infile" && -f "$zipfile" ]]; then
    echo "🗜️  Unzipping ${zipfile}"
    unzip -oq -d "$DUMPDIR" "$zipfile"
  fi

  if [[ ! -f "$infile" ]]; then
    echo "⚠️  Skip ${t}: CSV not found -> ${infile}"
    return 0
  fi

  echo "🔽 Import ${infile} -> ${dbname}:${SCHEMA}.${t}"
  local opts; opts="$(build_copy_opts "$t")"
  dc exec -T "$SERVICE_NAME" \
    psql -U "$DB_USER" -d "$dbname" -v ON_ERROR_STOP=1 \
      -c "\copy \"${SCHEMA}\".\"${t}\" FROM STDIN WITH ${opts}" < "$infile"

  sync_seq_auto "$t"
}

truncate_core() {
  echo "🧹 TRUNCATE core tables (DBごとに実行)"
  for t in "${TABLES_CORE[@]}"; do
    local dbname; dbname="$(get_db_name "$t")"
    echo "  - ${dbname}:${SCHEMA}.${t}"
    compose_psql "$dbname" "TRUNCATE \"${SCHEMA}\".\"${t}\" RESTART IDENTITY CASCADE;"
  done
}

reset_import_core() {
  truncate_core
  for t in "${TABLES_CORE[@]}"; do
    import_table "$t"
  done
  echo "🎉 reset-import-core done."
}

# ===== Usage =====
usage() {
  cat <<EOF
Usage:
  $(basename "$0") export-core        # 5テーブルをCSVエクスポート
  $(basename "$0") reset-import-core  # 5テーブル(TRUNCATE→CSVインポート→シーケンス同期)

対象テーブル:
  - country_league_master        (DB: ${DB_NAME_MASTER}, schema: ${SCHEMA})
  - country_league_season_master (DB: ${DB_NAME_MASTER}, schema: ${SCHEMA})
  - team_member_master           (DB: ${DB_NAME_MASTER}, schema: ${SCHEMA})
  - future_master                (DB: ${DB_NAME_MASTER}, schema: ${SCHEMA})
  - data                         (DB: ${DB_NAME_DATA},   schema: ${SCHEMA})

Notes:
  - data は ${DB_NAME_DATA} に存在。
  - 各テーブル取込後に自動でシーケンスを MAX(id/seq) に同期。
  - timestamp/date列は自動で FORCE_NULL を付与。
EOF
}

cmd="${1:-}"; shift || true
case "$cmd" in
  export-core)        export_core ;;
  reset-import-core)  reset_import_core ;;
  *)                  usage ;;
esac
