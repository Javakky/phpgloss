# PHPGloss

PhpStorm reads PHPDoc, but not all of it. Type aliases collapse to `array`, conditional return
types come back as both branches at once, and a type variable that sits inside a type argument is
never filled in. This plugin resolves those in the IDE, so the type you see matches the type
PHPStan infers.

## What it does

### Type aliases

`@phpstan-type` and `@phpstan-import-type` declare a name for a shape. PhpStorm parses them but
does not carry the shape through.

| | PhpStorm alone | With this plugin |
|---|---|---|
| Hover an alias | nothing | the shape it stands for |
| <kbd>Ctrl</kbd>+<kbd>B</kbd> on a reference | no declaration found | jumps to the declaration |
| <kbd>Ctrl</kbd>+<kbd>B</kbd> on a declaration | no declaration found | lists the usages |
| Key access on an imported shape | `mixed` | the key's type, with completion |
| `@return list<row_shape>` | `list<array>` | the alias name, linked to its declaration |

An alias declared on an interface or trait works the same as one on a class — a query port is
where these are usually written.

```php
/**
 * @phpstan-type row_shape array{user_id: positive-int, user_name: non-empty-string}
 */
interface RecordPort
{
    /** @return list<row_shape> */
    public function getUsers(): array;
}
```

### Type keywords

The 84 keywords PHPStan and Psalm reserve — `non-empty-string`, `positive-int`, `class-string`,
`int-mask-of`, and the rest — carry no meaning in the IDE. Hovering one now explains it, and
<kbd>Ctrl</kbd>+<kbd>B</kbd> opens the code that defines it: the copy in the project's `vendor/`
when there is one, otherwise the bundled source.

Descriptions come from the upstream classes themselves (Psalm's `Type\Atomic\T*` docblocks,
PHPStan's `Type\Accessory\*`), and are shown in Japanese when the IDE is in Japanese.

Only where a type is actually written. Prose that mentions `list`, and the `as` of an
`@phpstan-import-type`, are left alone.

### Type inference

PhpStorm resolves a type variable from a call's arguments, but stops short in several places.
Each of these is resolved here.

| Written | PhpStorm alone | With this plugin |
|---|---|---|
| `($mode is Shape::LIST ? list<V> : array<K, V>)` | both branches | the branch the argument selects |
| `@return Op<V>` | `Op<#V>` | `Op<\App\User>` |
| `@param callable(V, K): E` | `E` unresolved | bound from the closure's return type |
| `@return V\|null` | `V` alone | the null is kept |
| `@var list<positive-int>` | flattened to `int` | `positive-int` survives |
| `$callback($value)` inside the function | no type | the bound of the return type variable |
| `[Region::NORTH => …]` | integer keys | the constants' own type |

Conditional return types nest, and nesting is followed — real code writes them three deep. A
branch that the arguments cannot decide is left alone: no type is better than a wrong one.

## Install

Download the ZIP from the releases page and use **Settings → Plugins → ⚙ → Install Plugin from
Disk**, or build it yourself:

```shell
./gradlew buildPlugin        # build/distributions/*.zip
```

## Supported IDEs

PhpStorm 2025.1 and later, and IntelliJ IDEA Ultimate with the PHP plugin. Verified against
PhpStorm 2025.1, 2025.2, 2026.1, 2026.2 and IntelliJ IDEA Ultimate 2026.2.

The plugin runs no external process and makes no network request. It does not run PHPStan.

## Known limitations

- Type references are read in `@param`, `@return`, `@var` and their `@phpstan-` forms. Other tags
  are not scanned.
- Aliases are read from class, interface, trait and enum PHPDoc. Method, function and file-level
  aliases are not supported, and inherited aliases are not resolved implicitly.
- `typeAliases` from a PHPStan config file are not read.
- A type variable with no `of` bound cannot be shown inside the function that declares it. PHPStan
  prints `E (function …)`; the IDE has no way to say that.
- A shape reached through `foreach` shows as a shape, but a key access on it still resolves to
  `mixed`. PhpStorm holds shapes in a representation of its own and does not read one back from a
  type written as text.
- The rendered-doc note and Find Usages rest on experimental platform APIs. They may break on a
  future IDE; the rest of the plugin does not depend on them.

## Contributing

Bug reports and pull requests are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) covers building,
what a change needs, and the ways this plugin breaks quietly.

## Development

Java 25 is required — the platform and PHP plugin JARs are Java 25 bytecode, and an older JDK
cannot read them.

```shell
JAVA_HOME=/path/to/jdk-25 ./gradlew test
./gradlew verifyPlugin      # every supported IDE build
./gradlew runIde
```

`tools/conformance` measures the inference against PHPStan itself: it collects the type PHPStan
infers for every variable assignment in a real project, then compares. See its README for how to
run it; it needs a PHP project with `vendor/bin/phpstan`, so it is not part of CI.

## License

MIT. The bundled Psalm and PHPStan sources are MIT as well; see `NOTICE`.
