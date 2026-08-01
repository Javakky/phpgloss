#!/bin/bash
# 指定したディレクトリ直下の PHP プロジェクトを走査し、プロジェクトごとに
# 「全変数代入の PHPStan 型」と「テストケース候補」を出す。
#
#   ./sweep-projects.sh <プロジェクトを並べたディレクトリ> [出力先]
#
# 出力はプロジェクトごとに分ける。採った型をそのまま plugin の fixture にしないこと
# (そのプロジェクトの識別子が公開物に混ざる)。
set -u
here="$(cd "$(dirname "$0")" && pwd)"
projects="${1:?プロジェクトを並べたディレクトリを指定する}"
out="${2:-$here/out}"
mkdir -p "$out"

for dir in "$projects"/*/; do
    name="$(basename "$dir")"
    [ -f "$dir/composer.json" ] || continue
    if [ ! -x "$dir/vendor/bin/phpstan" ]; then
        echo "skip $name (vendor/bin/phpstan が無い。composer install が要る)"
        continue
    fi
    targets=""
    for candidate in src tests app lib; do
        [ -d "$dir/$candidate" ] && targets="$targets $candidate"
    done
    [ -n "$targets" ] || { echo "skip $name (src/tests/app/lib が無い)"; continue; }

    echo "== $name ($targets )"
    # shellcheck disable=SC2086
    python3 "$here/extract.py" "$dir" $targets -o "$out/$name-all.json" || continue
    python3 "$here/suggest.py" "$out/$name-all.json" "$dir" -o "$out/$name-suggestions.md"
done

echo
echo "出力: $out"
