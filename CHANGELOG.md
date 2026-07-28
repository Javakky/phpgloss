# Changelog

## 0.5.0

- Make imported shape resolution fast enough for key completion. The file model was re-parsed on every call, and the declaring class was found by scanning the whole definition file after an index lookup. Key completion calls this on every keystroke, so it was too slow to finish. The file model is now cached per file (invalidated on PSI change) and the declaring class comes from `PhpIndex.getClassesByFQN`.

## 0.4.0

- Resolve array shapes for `@phpstan-import-type` when the type is declared in another file. PhpStorm resolves imported shapes only within a single file, so key access on a cross-file imported shape produced `mixed`. Key completion and key access types now behave the same as for a locally declared `@phpstan-type`.
- Same-file imports are left to the PHP plugin, which already resolves them.

## 0.3.0

- Resolve type names in the alias declarations themselves: the name of a `@phpstan-type`, and both the original and the aliased name of a `@phpstan-import-type`. Previously the PHP plugin answered these positions with the class that owns the PHPDoc.
- Fix resolution inside class-level PHPDoc. The owning class was looked up through the PSI tree, but a class PHPDoc is a preceding sibling of the class rather than its child, so resolution was abandoned before it started.

## 0.2.0

- Target IntelliJ IDEA Ultimate 2026.2.0.1 (build range 262 through 262.*) and PHP plugin 262.8665.176.
- Raise the build toolchain and bytecode target to Java 25, which build 262 requires.
- Restrict test class detection to the `*Test` naming convention so helper types inside test classes are no longer picked up as test classes.

## 0.1.0

- Initial MVP for Quick Documentation and Go to Declaration on PHPStan type aliases in class-like PHPDoc.

