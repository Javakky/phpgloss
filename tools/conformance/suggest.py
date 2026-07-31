#!/usr/bin/env python3
"""採取した型から、テストケースの候補と fixture の雛形を作る。

extract.py が採った「全変数代入の PHPStan 型」を入力に、

1. この plugin が解くべき形 (総称・条件型・union) の代入を選ぶ
2. その代入に関わる関数の PHPDoc と呼び出し行を取り出す
3. BasePlatformTestCase の fixture としてそのまま貼れる形に整える

使い方:
    python3 suggest.py <answers.json> <phpプロジェクトのルート> [-o suggestions.md] [--limit N]

出力は Markdown。関数ごとにまとめ、同じ形の呼び出しは 1 つに畳む
(同じ機構を何度も fixture にしても検証の幅は増えないため)。
"""

import argparse
import json
import os
import re
import sys
from collections import OrderedDict

# 解くべき形。素の array や scalar は PhpStorm 自身が解くので候補にしない
INTERESTING = re.compile(r'(list<|array<[^,>]+,|non-empty-|\|null|int<|\bof \()')
CALL = re.compile(r'=\s*\\?([A-Za-z_][A-Za-z0-9_\\]*)\s*\(')


def doc_comment_of(lines, function_line):
    """関数宣言の直前の PHPDoc を返す (1-indexed の行番号)。"""
    end = function_line - 2
    while end >= 0 and lines[end].strip() == '':
        end -= 1
    if end < 0 or not lines[end].strip().endswith('*/'):
        return None
    start = end
    while start >= 0 and not lines[start].strip().startswith('/**'):
        start -= 1
    return '\n'.join(lines[start:end + 1]) if start >= 0 else None


def declarations_of(project):
    """関数名 -> (PHPDoc, 宣言行) 。src 配下の関数だけを見る。"""
    declarations = {}
    for root, _, files in os.walk(os.path.join(project, 'src')):
        for name in sorted(files):
            if not name.endswith('.php'):
                continue
            path = os.path.join(root, name)
            lines = open(path).read().split('\n')
            for i, line in enumerate(lines):
                match = re.match(r'\s*function ([a-zA-Z_]\w*)\s*\(', line)
                if not match:
                    continue
                doc = doc_comment_of(lines, i + 1)
                if doc and ('@template' in doc or '@phpstan-return' in doc):
                    declarations[match.group(1)] = (doc, line.strip())
    return declarations


def call_signature(snippet):
    """同じ形の呼び出しをまとめるための鍵。リテラルの中身は形だけ見る。"""
    normalized = re.sub(r'\[[^\]]*\]', '[…]', snippet)
    normalized = re.sub(r"'[^']*'", "'…'", normalized)
    return re.sub(r'\s+', ' ', normalized).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('answers', help='extract.py が出した JSON')
    parser.add_argument('project', help='PHP プロジェクトのルート')
    parser.add_argument('-o', '--output', default='-')
    parser.add_argument('--limit', type=int, default=3,
                        help='1 関数あたりに出す呼び出しの数 (既定 3)')
    args = parser.parse_args()

    rows = json.load(open(args.answers))
    project = os.path.abspath(args.project)
    declarations = declarations_of(project)
    if not declarations:
        print('error: 総称や条件型を持つ関数が src 配下に見つからない', file=sys.stderr)
        return 1

    by_function = OrderedDict()
    for row in rows:
        if not INTERESTING.search(row['type']) or row['file'].startswith('src/'):
            continue
        path = os.path.join(project, row['file'])
        if not os.path.exists(path):
            continue
        lines = open(path).read().split('\n')
        # 呼び出しは複数行に折り返されることがある。代入から括弧が閉じるまでを採る
        snippet, depth = [], 0
        for line in lines[row['line'] - 1:]:
            snippet.append(line.strip())
            depth += line.count('(') - line.count(')')
            if depth <= 0 and snippet:
                break
        text = ' '.join(snippet)
        call = CALL.search(text)
        if not call:
            continue
        name = call.group(1).split('\\')[-1]
        if name not in declarations:
            continue
        entry = by_function.setdefault(name, OrderedDict())
        entry.setdefault(call_signature(text), (row, text))

    out = ['# テストケース候補',
           '',
           f'対象: `{project}`。`{args.answers}` の型のうち、総称・条件型・null 付きのものを選んだ。',
           '',
           '各節は「その関数を fixture に写すときの PHPDoc」と「呼び出しと PHPStan の型」。',
           'PHPStan の型が期待値になる。値レベル (`list<1|2|3>`) までは目標にせず、',
           '構造 (list / array / null の有無) と要素の基本型で判定する。',
           '']
    for name, calls in by_function.items():
        doc, declaration = declarations[name]
        out.append(f'## {name}')
        out.append('')
        out.append('```php')
        out.append(doc)
        out.append(declaration.rstrip('{').strip())
        out.append('```')
        out.append('')
        out.append('| 呼び出し | PHPStan が出す型 | 位置 |')
        out.append('|---|---|---|')
        for _, (row, text) in list(calls.items())[:args.limit]:
            call_text = re.sub(r'\s+', ' ', text).replace('|', '\\|')
            out.append(f"| `{call_text[:110]}` | `{row['type'].replace('|', chr(92) + '|')[:80]}` "
                       f"| {row['file']}:{row['line']} |")
        dropped = max(0, len(calls) - args.limit)
        if dropped:
            out.append('')
            out.append(f'※ 同じ形の呼び出しを {dropped} 件省いた (--limit で増やせる)。')
        out.append('')

    payload = '\n'.join(out)
    if args.output == '-':
        print(payload)
    else:
        open(args.output, 'w').write(payload)
        print(f'{len(by_function)} functions -> {args.output}', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
