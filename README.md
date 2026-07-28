# PHPStan Type Documentation

PHPStan Type Documentation is an IntelliJ IDEA plugin that shows Quick Documentation for `@phpstan-type` and `@phpstan-import-type` aliases in PHPDoc, and navigates PHPDoc alias references to the actual type definition.

It also teaches PhpStorm's PHPDoc signature inspection that an alias whose body is an array shape is compatible with a native `array` parameter declaration.

It also fills a gap in PhpStorm's own array shape support. PhpStorm resolves an imported shape only when the `@phpstan-type` is declared in the same file, so key access on a shape imported from another file yields `mixed`. This plugin resolves that case, which restores key completion and key access types.

Supported target:

- IntelliJ IDEA Ultimate 2026.2.0.1
- PHP plugin 262.8665.176
- IntelliJ build range 262 through 262.*

Example:

```php
/**
 * @phpstan-type UserData array{
 *     id: positive-int,
 *     name: non-empty-string
 * }
 */
final class User {}

/**
 * @phpstan-import-type UserData from User as CreateUserData
 *
 * @param CreateUserData $payload
 */
final class CreateUser {}
```

Place the caret on `CreateUserData` in supported type tags and invoke Quick Documentation or Go to Declaration.

## Install From Source

Build the plugin ZIP:

```shell
./gradlew buildPlugin
```

Install `build/distributions/*.zip` using the IDE's local plugin installation action.

## Development

Requirements:

- Java 25. The platform and PHP plugin JARs of build 262 are Java 25 bytecode (class file version 69), so an older JDK cannot read them. If the JDK is not on a path Gradle scans, pass it explicitly:

```shell
JAVA_HOME=/path/to/jdk-25 ./gradlew test
```

Useful commands:

```shell
./gradlew test
./gradlew runIde
./gradlew verifyPlugin
```

The plugin does not run PHPStan, PHP, external processes, or network requests at runtime. Gradle may download build dependencies.

## Known Limitations

- Type references are detected in `@param`, `@return`, `@var`, `@phpstan-param`, `@phpstan-return`, and `@phpstan-var`, and in the declarations themselves: the name of a `@phpstan-type`, and both the original and the aliased name of a `@phpstan-import-type`. Other tags are not scanned.
- On a `@phpstan-type` name, Go to Declaration navigates to that same name rather than listing usages.
- Alias definitions and imports are limited to class, interface, trait, and enum PHPDoc.
- Method, function, and file-level aliases are not supported.
- Inherited aliases are not resolved implicitly.
- Nested aliases are not fully expanded or linkified.
- PHPStan `typeAliases` from config files are not supported.
- `DocumentationTarget` and `DocumentationTargetProvider` are marked `@ApiStatus.OverrideOnly` by the platform. Their signatures are unchanged between build 253 and 262, but the plugin targets a single IDE major version because the platform does not guarantee that.
