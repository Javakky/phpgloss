#!/usr/bin/env python3
"""PHP プロジェクトの全変数代入について、PHPStan の推論型 (正解) を JSON で採る。

ソースは一切変更しない。DumpAssignRule を一時的な設定で差し込み、
報告された「エラー」をパースする。

使い方:
    python3 extract.py <phpプロジェクトのルート> <対象パス...> [-o answers.json]

例:
    python3 extract.py /path/to/php-project src tests -o answers.json

出力 (JSON): [{"file": "tests/MapTest.php", "line": 15, "var": "result", "type": "list<int>"}]
テストケース作成の使い方: type に @template 由来の総称が現れる行が fixture 候補。
プラグインの推論結果 (IDE の表示や BasePlatformTestCase の global 型) と突き合わせ、
「構造が一致し、PHPStan 型と矛盾しない」ことを確認する。
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

RULE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'DumpAssignRule.php')


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('project', help='composer.json のあるプロジェクトルート')
    parser.add_argument('paths', nargs='+', help='解析対象 (プロジェクトルートからの相対パス)')
    parser.add_argument('-o', '--output', default='-', help='出力先 JSON (省略時は標準出力)')
    parser.add_argument('--level', default='0',
                        help='PHPStan level (既定 0。型採取に検査は不要)')
    parser.add_argument('--memory-limit', default='2G',
                        help='PHPStan に渡すメモリ上限 (既定 2G。大きなプロジェクトは既定の 128M で落ちる)')
    parser.add_argument('--timeout', type=int, default=1800, help='秒 (既定 1800)')
    args = parser.parse_args()

    project = os.path.abspath(args.project)
    phpstan = os.path.join(project, 'vendor', 'bin', 'phpstan')
    if not os.path.exists(phpstan):
        print(f'error: {phpstan} が無い (composer install 済みのプロジェクトを指定する)', file=sys.stderr)
        return 1

    # プロジェクトの設定を土台にする。大きなプロジェクトは autoload / bootstrap を設定に
    # 持っており、それを落とすと解析が「severe errors」で不完全になる (実測)。
    # baseline は既存のエラー文面を無視する仕組みなので、こちらの報告は消されない
    base = next((name for name in ('phpstan.neon', 'phpstan.neon.dist', 'phpstan.dist.neon')
                 if os.path.exists(os.path.join(project, name))), None)
    with tempfile.NamedTemporaryFile('w', suffix='.neon', dir=project, delete=False) as neon:
        if base:
            neon.write(f'includes:\n    - {base}\n')
        neon.write(
            'parameters:\n'
            f'    level: {args.level}\n'
            '    reportUnmatchedIgnoredErrors: false\n'
            'rules:\n'
            '    - PhpStanTypeDoc\\Conformance\\DumpAssignRule\n'
        )
        neon_path = neon.name

    try:
        result = subprocess.run(
            [phpstan, 'analyse', '--no-progress', '--error-format=raw',
             '--memory-limit', args.memory_limit,
             '-c', neon_path, '--autoload-file', RULE_FILE, *args.paths],
            cwd=project, capture_output=True, text=True, timeout=args.timeout,
        )
        rows = []
        for line in result.stdout.splitlines():
            m = re.match(r'(.+?):(\d+):VARTYPE\|(\w+)\|(.*)$', line.strip())
            if not m:
                continue
            file, lineno, var, typ = m.groups()
            # 設定によっては報告の末尾に識別子が付く (`... [identifier=conformance.varType]`)
            typ = re.sub(r'\s*\[identifier=[^\]]*\]\s*$', '', typ)
            rows.append({
                'file': os.path.relpath(file, project),
                'line': int(lineno),
                'var': var,
                'type': typ,
            })
        if not rows:
            print('error: 1 件も採れなかった。PHPStan の出力:', file=sys.stderr)
            print(result.stdout[-2000:], file=sys.stderr)
            print(result.stderr[-1000:], file=sys.stderr)
            return 1
        payload = json.dumps(rows, ensure_ascii=False, indent=1)
        if args.output == '-':
            print(payload)
        else:
            with open(args.output, 'w') as f:
                f.write(payload)
            print(f'{len(rows)} assignments -> {args.output}', file=sys.stderr)
        return 0
    finally:
        os.unlink(neon_path)


if __name__ == '__main__':
    sys.exit(main())
