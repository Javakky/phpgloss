# PHPStan / Psalm 型キーワード

キーワードごとの説明。書式は `## <キーワード>` の見出しに続けて、

- `tools:` 受け付けるツール (`psalm` / `phpstan`)
- `en:` 英語の説明。上流に文があるものは原則そのまま引く
- 残りの行が日本語の説明 (公式の日本語訳が無いため訳出は独自)

出典 (いずれも MIT):

- Psalm `src/Psalm/Type/Atomic/T*.php` のクラス docblock — 1 語 1 文の説明がある
- Psalm `src/Psalm/Type/Atomic.php` の `create()` — キーワードと型クラスの対応
- Psalm `docs/annotating_code/type_syntax/`
- PHPStan `src/PhpDoc/TypeNodeResolver.php`、`src/Type/Accessory/`

## int
tools: psalm, phpstan
en: Denotes the `int` type, where the exact value is unknown.
PHP の整数を表す。範囲を絞るなら `int<0, 100>` のようにも書ける。

## integer
tools: psalm
en: An alias of `int`, kept for PHPDoc compatibility. Psalm normalises it in `TypeTokenizer`.
`int` の別名として使える。PHPDoc 由来の綴りで、Psalm は `TypeTokenizer` で `int` に正規化する。

## positive-int
tools: psalm, phpstan
en: An integer of 1 or above. Psalm builds it as `TIntRange(1, null)`, PHPStan as `IntegerRangeType::fromInterval(1, null)`. Same as `int<1, max>`.
1 以上の整数を表す。`int<1, max>` と同じものになる。

## non-negative-int
tools: psalm, phpstan
en: An integer of 0 or above. Same as `int<0, max>`.
0 以上の整数を表す。`int<0, max>` と同じものになる。

## negative-int
tools: psalm, phpstan
en: An integer of -1 or below. Same as `int<min, -1>`.
-1 以下の整数を表す。`int<min, -1>` と同じものになる。

## non-positive-int
tools: psalm, phpstan
en: An integer of 0 or below. Same as `int<min, 0>`.
0 以下の整数を表す。`int<min, 0>` と同じものになる。

## non-zero-int
tools: phpstan
en: Any integer except 0. PHPStan builds it as the union `int<min, -1>|int<1, max>`.
0 以外の整数を表す。`int<min, -1>|int<1, max>` と同じものになる。

## min
tools: psalm, phpstan
en: The lower bound of an integer range, as in `int<min, 0>`. It stands for `PHP_INT_MIN`. Not a type on its own — Psalm holds it as `TIntRange::BOUND_MIN`.
`int<min, 0>` のように整数範囲の下限に書く。`PHP_INT_MIN` を指し、単独の型名としては使えない。

## max
tools: psalm, phpstan
en: The upper bound of an integer range, as in `int<1, max>`. It stands for `PHP_INT_MAX`. Not a type on its own.
`int<1, max>` のように整数範囲の上限に書く。`PHP_INT_MAX` を指し、単独の型名としては使えない。

## literal-int
tools: psalm
en: Denotes the `literal-int` type, where the exact value is unknown but we know that the int is not from user input
コード中に整数リテラルとして書かれた値を表す。正確な値は分からないが、利用者入力由来でないことは保証される。

## int-mask
tools: psalm, phpstan
en: Represents the type that is the result of a bitmask combination of its parameters. `int-mask<1, 2, 4>` corresponds to `0|1|2|3|4|5|6|7`
引数のビット論理和で作れる値を表す。`int-mask<1, 2, 4>` なら `0|1|2|3|4|5|6|7` になる。

## int-mask-of
tools: psalm, phpstan
en: Represents the type that is the result of a bitmask combination of its parameters. This is the same concept as TIntMask but TIntMaskOf is used with a reference to constants in code `int-mask-of<MyClass::CLASS_CONSTANT_*>` will corresponds to `0|1|2|3|4|5|6|7` if there are three constant 1, 2 and 4
`int-mask` と同じ考え方だが、値を定数参照で指定する。`int-mask-of<MyClass::CLASS_CONSTANT_*>` のように書く。

## string
tools: psalm, phpstan
en: Denotes the `string` type, where the exact value is unknown.
PHP の文字列を表す。

## non-empty-string
tools: psalm, phpstan
en: Denotes a string, that is also non-empty (every string except '')
空文字列 `''` でない文字列を表す。`'0'` は含まれる。

## non-falsy-string
tools: psalm, phpstan
en: Denotes a string, that is also non-falsy (every string except '' and '0')
真と評価される文字列を表す。`non-empty-string` から `'0'` も除いたものになる。

## truthy-string
tools: psalm
en: An alias of `non-falsy-string`; both resolve to the same type.
`non-falsy-string` の別名として使える。同じ型に解決される。

## numeric-string
tools: psalm, phpstan
en: Denotes a string that's also a numeric value e.g. `"5"`. It can result from `is_string($s) && is_numeric($s)`.
数値として解釈できる文字列を表す。`"5"` などが該当する。

## decimal-int-string
tools: phpstan
en: This represents strings containing decimal integers. These are guaranteed to be cast to an integer in an array key. Examples of constant values covered by this type: "0", "1", "1234", "-1"
10 進の整数表記そのままの文字列を表す。配列キーにすると整数へ変換されることが保証される。`"0"` `"1"` `"1234"` `"-1"` などが該当する。

## non-decimal-int-string
tools: phpstan
en: This represents strings containing non-decimal integers and other text. These are guaranteed to stay as string in an array key. Examples of constant values covered by this type: "+1", "00", "18E+3", "1.2", "1,3", "foo"
10 進の正規表記でない整数文字列やその他の文字列を表す。配列キーにしても文字列のまま残ることが保証される。`"+1"` `"00"` `"18E+3"` などが該当する。

## lowercase-string
tools: psalm, phpstan
en: A string where every character is lowercased.
すべて小文字の文字列を表す。

## non-empty-lowercase-string
tools: psalm
en: Denotes a non-empty-string where every character is lowercased. (which can also result from a `strtolower` call).
空でなく、すべて小文字の文字列を表す。`strtolower` の結果にもなる。

## uppercase-string
tools: phpstan
en: A string where every character is uppercased. PHPStan expresses it as `string` intersected with `AccessoryUppercaseStringType`.
すべて大文字の文字列を表す。

## non-empty-uppercase-string
tools: phpstan
en: A string that is both non-empty and uppercased.
空でなく、すべて大文字の文字列を表す。

## literal-string
tools: psalm, phpstan
en: Denotes the `literal-string` type, where the exact value is unknown but we know that the string is not from user input
コード中に文字列リテラルとして書かれた値と、その連結を表す。正確な値は分からないが、利用者入力由来でないことは保証される。SQL 組み立てなどのインジェクション対策に使う。

## non-empty-literal-string
tools: psalm
en: A `literal-string` that is also non-empty.
空でない `literal-string` を表す。

## class-string
tools: psalm, phpstan
en: Denotes the `class-string` type, used to describe a string representing a valid PHP class. The parent type from which the classes descend may or may not be specified in the constructor.
実在するクラスの完全修飾名を表す文字列を指す。`class-string<Foo>` と書くと `Foo` かその派生に限定できる。

## interface-string
tools: psalm
en: A string representing a valid PHP interface.
実在するインターフェースの完全修飾名を表す文字列を指す。

## enum-string
tools: psalm
en: A string representing a valid PHP enum.
実在する enum の完全修飾名を表す文字列を指す。

## trait-string
tools: psalm
en: Denotes the `trait-string` type, used to describe a string representing a valid PHP trait.
実在するトレイトの完全修飾名を表す文字列を指す。

## callable-string
tools: psalm, phpstan
en: Denotes the `callable-string` type, used to represent an unknown string that is also `callable`.
呼び出し可能な関数名を表す文字列を指す。

## class-string-map
tools: psalm
en: Represents an array where the type of each value is a function of its string key value
キーがクラス名で、各値の型がそのキーから決まる配列を表す。`class-string-map<T as Foo, T>` のように書く。

## float
tools: psalm, phpstan
en: Denotes the `float` type, where the exact value is unknown.
PHP の浮動小数点数を表す。

## double
tools: psalm
en: An alias of `float`. Psalm normalises it in `TypeTokenizer`.
`float` の別名として使える。Psalm は `TypeTokenizer` で `float` に正規化する。

## real
tools: psalm
en: An alias of `float`. Psalm normalises it in `TypeTokenizer`.
`float` の別名として使える。Psalm は `TypeTokenizer` で `float` に正規化する。

## number
tools: phpstan
en: The union `int|float`.
`int` と `float` のどちらかを表す。

## bool
tools: psalm, phpstan
en: Denotes the `bool` type where the exact value is unknown.
真偽値を表す。

## boolean
tools: psalm
en: An alias of `bool`, kept for PHPDoc compatibility.
`bool` の別名として使える。PHPDoc 由来の綴りになる。

## true
tools: psalm, phpstan
en: Denotes the `true` value type
値 `true` だけを表す。

## false
tools: psalm, phpstan
en: Denotes the `false` value type
値 `false` だけを表す。

## null
tools: psalm, phpstan
en: Denotes the `null` type
値 `null` だけを表す。`?Foo` は `null|Foo` と同じ意味になる。

## scalar
tools: psalm, phpstan
en: Denotes the `scalar` super type (which can also result from an `is_scalar` check). This type encompasses `float`, `int`, `bool` and `string`.
`float` `int` `bool` `string` を含む上位型を表す。

## empty-scalar
tools: phpstan
en: Denotes a `scalar` type that is also empty.
偽と評価されるスカラーを表す。`0` / `0.0` / `''` / `'0'` / `false` が該当する。

## non-empty-scalar
tools: phpstan
en: Denotes a `scalar` type that is also non-empty.
真と評価されるスカラーを表す。`scalar` から偽値を除いたものになる。

## numeric
tools: psalm, phpstan
en: Denotes the `numeric` type (which can also result from an `is_numeric` check).
`int` / `float` / `numeric-string` の上位型を表す。

## array
tools: psalm, phpstan
en: Denotes a simple array of the form `array<TKey, TValue>`. It expects an array with two elements, both union types.
PHP の配列を表す。`array<TKey, TValue>` でキーと値の型を指定でき、`array{id: int}` で構造も書ける。

## non-empty-array
tools: psalm, phpstan
en: Denotes array known to be non-empty of the form `non-empty-array<TKey, TValue>`. It expects an array with two elements, both union types.
空でない配列を表す。`non-empty-array<string, int>` のように型引数も取れる。

## associative-array
tools: psalm
en: An array that is not required to have consecutive integer keys. Psalm resolves it to `array<TKey, TValue>`.
キーが連番であることを要求しない配列を表す。Psalm では `array<TKey, TValue>` に解決される。

## list
tools: psalm, phpstan
en: A continuous, integer-indexed array such as `["red", "yellow", "blue"]`. A frequent way to create one is with the `$arr[] =` notation.
0 から連番の整数キーを持つ配列を表す。`$arr[] =` で作ると自然にこの形になる。

## non-empty-list
tools: psalm, phpstan
en: A `list` that is not allowed to be empty.
空でない `list` を表す。

## array-key
tools: psalm, phpstan
en: Denotes the `array-key` type, used for something that could be the offset of an `array`.
配列のキーになれる型を表す。`int|string` の上位型にあたる。

## non-empty-countable
tools: psalm
en: An assertion rather than a type: it holds when `count()` returns 1 or more. Psalm keeps it in `Storage\Assertion\NonEmptyCountable`.
型ではなく assertion として使う。`count()` が 1 以上を返すことを表し、配列と `Countable` の両方に使える。

## callable-array
tools: psalm, phpstan
en: Denotes an object-like array that is _also_ `callable`.
`[$object, 'method']` のような、呼び出し可能な配列を表す。

## arraylike-object
tools: psalm
en: An object usable like an array. Psalm accepts it alongside `iterable`, `Traversable`, `Iterator` and `IteratorAggregate` in `TypeParser`.
配列のように扱えるオブジェクトを表す。Psalm は `TypeParser` で `iterable` / `Traversable` / `Iterator` / `IteratorAggregate` と並べて扱う。

## iterable
tools: psalm, phpstan
en: denotes the `iterable` type(which can also result from an `is_iterable` check).
`foreach` で回せるものを表す。配列と `Traversable` を含み、`iterable<string, Foo>` と型引数も取れる。

## object
tools: psalm, phpstan
en: Denotes the `object` type
任意のオブジェクトを表す。`object{id: int}` の形でプロパティも書ける。

## callable-object
tools: psalm, phpstan
en: Denotes an object that is also `callable` (i.e. it has `__invoke` defined).
`__invoke` を持つオブジェクトを表す。

## stringable-object
tools: psalm
en: An object that defines `__toString`.
`__toString` を持つオブジェクトを表す。

## callable
tools: psalm, phpstan
en: Denotes the `callable` type. Can result from an `is_callable` check.
呼び出し可能なものを表す。`callable(int): string` の形で引数と戻り値も書ける。

## pure-callable
tools: psalm
en: A `callable` without side effects.
副作用を持たない `callable` を表す。

## pure-Closure
tools: psalm
en: A `Closure` without side effects.
副作用を持たない `Closure` を表す。

## pure-closure
tools: phpstan
en: A `Closure` without side effects (PHPStan's spelling).
副作用を持たない `Closure` を表す。PHPStan ではこの綴りを使う。

## static-closure
tools: phpstan
en: A `Closure` declared `static`, so it carries no `$this` binding.
`static function () {}` で作られた、`$this` を束縛しない `Closure` を表す。

## static-pure-closure
tools: phpstan
en: A `Closure` that is both `static` and free of side effects.
`static` かつ副作用を持たない `Closure` を表す。

## resource
tools: psalm, phpstan
en: Denotes the `resource` type (e.g. a file handle).
PHP のリソースを表す。ファイルハンドルなどが該当する。

## open-resource
tools: psalm
en: A resource that is still open, as opposed to `closed-resource`.
開いたままのリソースを表す。`closed-resource` と対になる。

## closed-resource
tools: psalm
en: Denotes the `resource` type that has been closed (e.g. a file handle through `fclose()`).
`fclose()` 等で閉じられたあとのリソースを表す。

## mixed
tools: psalm, phpstan
en: Denotes the `mixed` type, used when you don't know the type of an expression.
型情報が無いことを表す最上位型にあたる。

## non-empty-mixed
tools: phpstan
en: Denotes the `mixed` type, but not empty. Generated for `$x` inside the `if` statement `if ($x) {...}` when `$x` is `mixed` outside.
`mixed` から偽値を除いたものを表す。`if ($x)` の中の `$x` に付く。

## never
tools: psalm, phpstan
en: Denotes the `no-return`/`never-return` type for functions that never return, either throwing an exception or terminating (like the builtin `exit()`).
値を持たない最下位型にあたる。常に例外を投げるか終了する関数の戻り値に使う。

## never-return
tools: psalm
en: An alias of `never`.
`never` の別名として使える。

## never-returns
tools: psalm
en: An alias of `never`.
`never` の別名として使える。

## no-return
tools: psalm
en: An alias of `never`.
`never` の別名として使える。

## noreturn
tools: phpstan
en: An alias of `never` (PHPStan's spelling).
`never` の別名として使える。PHPStan ではこの綴りを使う。

## void
tools: psalm, phpstan
en: Denotes the `void` type, normally just used to annotate a function/method that returns nothing
値を返さない関数の戻り値に使う。

## empty
tools: psalm
en: A type Psalm used to have. It now resolves to `never`.
かつて Psalm が持っていた型で、現在は `never` に解決される。

## self
tools: psalm, phpstan
en: The class the annotation is written in.
そのメソッドが書かれているクラス自身を指す。

## static
tools: psalm, phpstan
en: The class the call was actually made on, following late static binding.
実際に呼び出された側のクラスを指す。遅延静的束縛と同じ意味になる。

## parent
tools: phpstan
en: The parent of the class the annotation is written in.
そのクラスの親クラスを指す。

## key-of
tools: psalm, phpstan
en: Represents an offset of an array.
配列型や定数のキーを取り出す。`key-of<Foo::MAP>` のように書く。

## value-of
tools: psalm, phpstan
en: Represents a value of an array or enum.
配列型や定数の値を取り出す。`value-of<Foo::MAP>` のように書き、backed enum にも使える。

## properties-of
tools: psalm
en: Type that resolves to a keyed-array with properties of a class as keys and their appropriate types as values.
クラスのプロパティ名をキー、その型を値とする配列型に解決される。

## public-properties-of
tools: psalm
en: `properties-of` limited to public properties.
`properties-of` のうち public なものだけを取り出す。

## protected-properties-of
tools: psalm
en: `properties-of` limited to protected properties.
`properties-of` のうち protected なものだけを取り出す。

## private-properties-of
tools: psalm
en: `properties-of` limited to private properties.
`properties-of` のうち private なものだけを取り出す。
