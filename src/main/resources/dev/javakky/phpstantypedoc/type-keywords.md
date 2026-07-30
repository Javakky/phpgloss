# PHPStan / Psalm 型キーワード

キーワードごとの説明。書式は `## <キーワード>` の見出しに続けて、`tools:` 行 (対応ツール) と
説明本文。説明は日本語で書く。公式に日本語訳が無いため訳出は本プラグイン独自。

出典 (いずれも MIT):

- Psalm `src/Psalm/Internal/Type/TypeTokenizer.php` の `PSALM_RESERVED_WORDS`
- Psalm `docs/annotating_code/type_syntax/` — 説明の元にした記述
- PHPStan `src/PhpDoc/TypeNodeResolver.php` の `resolveIdentifierTypeNode`

`tools:` は `psalm` / `phpstan` を列挙する。両方に無いキーワードは載せない。

## int
tools: psalm, phpstan
PHP の整数。範囲を絞るなら `int<0, 100>` のようにも書ける。

## integer
tools: psalm
`int` の別名。PHPDoc 由来の綴りで、Psalm が受け付ける。

## positive-int
tools: psalm, phpstan
1 以上の整数。`int<1, max>` と同じ。

## non-negative-int
tools: psalm, phpstan
0 以上の整数。`int<0, max>` と同じ。

## negative-int
tools: psalm, phpstan
-1 以下の整数。`int<min, -1>` と同じ。

## non-positive-int
tools: psalm, phpstan
0 以下の整数。`int<min, 0>` と同じ。

## non-zero-int
tools: phpstan
0 以外の整数。`int<min, -1>|int<1, max>` と同じ。

## literal-int
tools: psalm
コード中に整数リテラルとして書かれた値だけ。外部入力を `(int)` で変換した値は含まない。

## int-mask
tools: psalm, phpstan
引数のビット論理和で作れる値。`int-mask<1, 2, 4>` は `0|1|2|3|4|5|6|7` を表す。

## int-mask-of
tools: psalm, phpstan
`int-mask` と同じだが、値を定数参照で指定する。`int-mask-of<MyClass::FLAG_*>` の形。

## string
tools: psalm, phpstan
PHP の文字列。

## non-empty-string
tools: psalm, phpstan
空文字列 `''` でない文字列。`'0'` は含む。

## non-falsy-string
tools: psalm, phpstan
真と評価される文字列。`non-empty-string` から `'0'` も除いたもの。

## truthy-string
tools: psalm
`non-falsy-string` の別名。

## numeric-string
tools: psalm, phpstan
数値として解釈できる文字列。`'42'` や `'3.14'` など。

## decimal-int-string
tools: phpstan
10 進の整数表記そのままの文字列。`'42'` は該当し、`'042'` や `'4.2'` は該当しない。

## non-decimal-int-string
tools: phpstan
整数を表すが 10 進の正規表記でない文字列。`'042'` や `'0x2A'` など。

## lowercase-string
tools: psalm, phpstan
すべて小文字の文字列。

## non-empty-lowercase-string
tools: psalm
空でなく、すべて小文字の文字列。

## uppercase-string
tools: phpstan
すべて大文字の文字列。

## non-empty-uppercase-string
tools: phpstan
空でなく、すべて大文字の文字列。

## literal-string
tools: psalm, phpstan
コード中に文字列リテラルとして書かれた値と、その連結だけ。外部入力は含まない。SQL 組み立てなどの
インジェクション対策に使う。

## non-empty-literal-string
tools: psalm
空でない `literal-string`。

## class-string
tools: psalm, phpstan
実在するクラスの完全修飾名を表す文字列。`class-string<Foo>` と書くと `Foo` かその派生に限定できる。

## interface-string
tools: psalm
実在するインターフェースの完全修飾名を表す文字列。

## enum-string
tools: psalm
実在する enum の完全修飾名を表す文字列。

## trait-string
tools: psalm
実在するトレイトの完全修飾名を表す文字列。

## callable-string
tools: psalm, phpstan
呼び出し可能な関数名を表す文字列。

## class-string-map
tools: psalm
キーがクラス名、値がそのクラスのインスタンスであるマップ。`class-string-map<T as Foo, T>` の形。

## float
tools: psalm, phpstan
PHP の浮動小数点数。

## double
tools: psalm
`float` の別名。

## real
tools: psalm
`float` の別名。

## number
tools: phpstan
`int|float`。

## bool
tools: psalm, phpstan
真偽値。

## boolean
tools: psalm
`bool` の別名。

## true
tools: psalm, phpstan
値 `true` だけ。

## false
tools: psalm, phpstan
値 `false` だけ。

## null
tools: psalm, phpstan
値 `null` だけ。`?Foo` は `null|Foo` と同じ。

## scalar
tools: psalm, phpstan
`int|float|string|bool`。

## empty-scalar
tools: phpstan
偽と評価されるスカラー。`0` / `0.0` / `''` / `'0'` / `false`。

## non-empty-scalar
tools: phpstan
真と評価されるスカラー。`scalar` から偽値を除いたもの。

## numeric
tools: psalm, phpstan
`int` / `float` / `numeric-string` の上位型。

## array
tools: psalm, phpstan
PHP の配列。`array<TKey, TValue>` でキーと値の型を指定できる。`array{id: int}` で構造も書ける。

## non-empty-array
tools: psalm, phpstan
空でない配列。`non-empty-array<string, int>` のように型引数も取れる。

## associative-array
tools: psalm
連想配列。キーが連番であることを要求しない配列。

## list
tools: psalm, phpstan
0 から連番の整数キーを持つ配列。`$arr[] =` で作られる形。

## non-empty-list
tools: psalm, phpstan
空でない `list`。

## array-key
tools: psalm, phpstan
配列のキーになれる型。`int|string` の上位型。

## non-empty-countable
tools: psalm
`count()` が 1 以上を返すもの。配列と `Countable` の両方を含む。

## callable-array
tools: psalm, phpstan
`[$object, 'method']` のような、呼び出し可能な配列。

## arraylike-object
tools: psalm
配列のように扱えるオブジェクト。`Countable` かつ `ArrayAccess` かつ `Traversable`。

## iterable
tools: psalm, phpstan
`foreach` で回せるもの。配列と `Traversable` を含む。`iterable<string, Foo>` と型引数も取れる。

## object
tools: psalm, phpstan
任意のオブジェクト。`object{id: int}` の形でプロパティも書ける。

## callable-object
tools: psalm, phpstan
`__invoke` を持つオブジェクト。

## stringable-object
tools: psalm
`__toString` を持つオブジェクト。

## callable
tools: psalm, phpstan
呼び出し可能なもの。`callable(int): string` の形で引数と戻り値も書ける。

## pure-callable
tools: psalm
副作用を持たない `callable`。

## pure-Closure
tools: psalm
副作用を持たない `Closure`。

## pure-closure
tools: phpstan
副作用を持たない `Closure` (PHPStan の綴り)。

## static-closure
tools: phpstan
`static function () {}` で作られた、`$this` を束縛しない `Closure`。

## static-pure-closure
tools: phpstan
`static` かつ副作用を持たない `Closure`。

## resource
tools: psalm, phpstan
PHP のリソース。

## open-resource
tools: psalm
開いたままのリソース。

## closed-resource
tools: psalm
`fclose()` 等で閉じられたあとのリソース。

## mixed
tools: psalm, phpstan
型情報が無いことを表す最上位型。

## non-empty-mixed
tools: phpstan
`mixed` から偽値を除いたもの。

## never
tools: psalm, phpstan
値を持たない最下位型。常に例外を投げるか終了する関数の戻り値に使う。

## never-return
tools: psalm
`never` の別名。

## never-returns
tools: psalm
`never` の別名。

## no-return
tools: psalm
`never` の別名。

## noreturn
tools: phpstan
`never` の別名 (PHPStan の綴り)。

## void
tools: psalm, phpstan
値を返さない関数の戻り値。

## empty
tools: psalm
かつて Psalm が持っていた型。現在は `never` に置き換えられている。

## self
tools: psalm, phpstan
そのメソッドが書かれているクラス自身。

## static
tools: psalm, phpstan
実際に呼び出された側のクラス。遅延静的束縛と同じ意味。

## parent
tools: phpstan
そのクラスの親クラス。

## key-of
tools: psalm, phpstan
配列型や定数のキーを取り出す。`key-of<Foo::MAP>` の形。

## value-of
tools: psalm, phpstan
配列型や定数の値を取り出す。`value-of<Foo::MAP>` の形。backed enum にも使える。

## properties-of
tools: psalm
オブジェクトのプロパティ名と型からなる配列型を作る。

## public-properties-of
tools: psalm
`properties-of` のうち public なものだけ。

## protected-properties-of
tools: psalm
`properties-of` のうち protected なものだけ。

## private-properties-of
tools: psalm
`properties-of` のうち private なものだけ。
