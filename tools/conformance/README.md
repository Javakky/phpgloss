# conformance — PHPStan を正解とする型の採取

PHP プロジェクトの**全変数代入**について、PHPStan が推論した型を JSON で採る。
ソースは一切変更しない (計測用ルール `DumpAssignRule` を一時設定で差し込む)。

```
python3 tools/conformance/extract.py <プロジェクトルート> <対象パス...> -o answers.json
```

出力の 1 行が 1 代入で、`file` / `line` / `var` / `type` (PHPStan の precise 表記) を持つ。

## テストケース作成への使い方

1. `type` に総称 (`list<...>` / `array<...>`) や `|null` が現れる行が、この plugin が
   解くべき形の候補になる
2. その行の呼び出しと関数の PHPDoc を fixture に写し、plugin の推論結果を
   「構造が一致し、PHPStan の型と矛盾しない」ことで判定するテストを書く
3. PHPStan は値レベル (`list<1|2|3>`) や intersection 合成まで追跡する。そこまでの再現は
   目標にしない。構造 (list / array / null の有無) と要素の基本型までを判定基準にする

前提: 対象プロジェクトに `vendor/bin/phpstan` があること (composer install 済み)。
