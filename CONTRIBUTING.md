# Contributing

## Building

Java 25 is required. The platform and PHP plugin JARs are Java 25 bytecode, so an older JDK cannot
read them — the failure looks like an unsupported class file version.

```shell
./gradlew clean test          # 280 tests
./gradlew verifyPlugin        # every IDE this plugin claims to support
./gradlew buildPlugin         # build/distributions/*.zip
./gradlew runIde              # a sandbox IDE with the plugin loaded
```

If Gradle does not find a JDK 25, pass it: `JAVA_HOME=/path/to/jdk-25 ./gradlew test`.

## What a change needs

**A test that fails without it.** Not a test that passes — one that goes red when the fix is
reverted. This project has twice shipped tests that never exercised the mechanism they were named
after; both were caught only by breaking the code on purpose.

```shell
tools/mutation/mutate.sh result.txt
```

That script disables one mechanism at a time and counts the tests that fail. A mechanism that
nothing catches is not covered, whatever the test names say. Read `tools/mutation/README.md` before
trusting a number from it — a mutation that fails to compile also reports zero.

**A fixture shaped like real code.** Types written by hand tend to be simpler than the ones people
write, and the difference is where the defects live. Copy the shape of a declaration you have seen,
then rename the identifiers — do not carry another project's names into this repository.

**No guessing.** When the arguments do not determine a type, say nothing. A wrong type is worse
than no type: the reader trusts it.

## Measuring against PHPStan

`tools/conformance` collects the type PHPStan infers for every variable assignment in a PHP project
and compares it with what this plugin resolves. It needs a project with `vendor/bin/phpstan`, so it
is not part of CI.

```shell
python3 tools/conformance/extract.py /path/to/php-project src tests -o answers.json
./gradlew test --tests '*ConformanceSweepTest*' \
    -Dconformance.answers=answers.json \
    -Dconformance.project=/path/to/php-project \
    -Dconformance.report=sweep-report.md
```

The report sorts each variable into agreeing, silent, or contradicting. Only contradictions are
defects; silence usually means PHPStan tracks something the IDE has no way to express.

Do not turn the types it collects into fixtures directly — that copies the identifiers of whatever
project you ran it against into a public repository.

## Things that break quietly

- **The index version.** Change what `PhpStanTypeAliasIndex` writes and the version has to go up,
  or existing installs keep reading the old index.
- **Reserved characters.** Provider keys and the separators used to encode a call must not collide;
  `ReservedCharactersTest` walks the source and fails if one is used twice.
- **Resolution during indexing.** A type provider may not resolve other expressions while the index
  is being built. Reading an argument's type does exactly that, and the failure surfaces as
  `TypeProvider contract violation` in the log rather than as a broken feature.
- **Assumptions about PhpStorm.** `PlatformAssumptionsTest` pins the behaviour this plugin relies
  on. If the IDE changes, that test fails instead of the feature silently degrading.

## Commits

Conventional Commits. The subject line says what changed and why, not which files moved.

## Releasing

Tag `v<version>` and push it; the workflow builds, verifies against every supported IDE, and
attaches the ZIP to a GitHub release with the notes from `CHANGELOG.md`. Publishing to the
JetBrains Marketplace is done by hand — the listing text and screenshots deserve a look each time.
