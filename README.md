# PHPStan Type Documentation

PHPStan Type Documentation is an IntelliJ IDEA plugin that shows Quick Documentation for `@phpstan-type` and `@phpstan-import-type` aliases in PHPDoc, and navigates PHPDoc alias references to the actual type definition.

Supported target:

- IntelliJ IDEA Ultimate 2025.3.6
- PHP plugin 253.32098.37
- IntelliJ build range 253 through 253.*

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

- Java 21

Useful commands:

```shell
./gradlew test
./gradlew runIde
./gradlew verifyPlugin
```

The plugin does not run PHPStan, PHP, external processes, or network requests at runtime. Gradle may download build dependencies.

## Known Limitations

- Reference detection is limited to `@param`, `@return`, `@var`, `@phpstan-param`, `@phpstan-return`, and `@phpstan-var`.
- Alias definitions and imports are limited to class, interface, trait, and enum PHPDoc.
- Method, function, and file-level aliases are not supported.
- Inherited aliases are not resolved implicitly.
- Nested aliases are not fully expanded or linkified.
- PHPStan `typeAliases` from config files are not supported.
- The Documentation Target API used by IntelliJ 2025.3 is marked experimental by the platform.
