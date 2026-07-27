# Changelog

## 0.2.0

- Target IntelliJ IDEA Ultimate 2026.2.0.1 (build range 262 through 262.*) and PHP plugin 262.8665.176.
- Raise the build toolchain and bytecode target to Java 25, which build 262 requires.
- Restrict test class detection to the `*Test` naming convention so helper types inside test classes are no longer picked up as test classes.

## 0.1.0

- Initial MVP for Quick Documentation and Go to Declaration on PHPStan type aliases in class-like PHPDoc.

