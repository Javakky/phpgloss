# Changelog











## 0.22.0

- Fix "malformed URL" when clicking a link in the rendered comment. Links there are resolved starting from the target returned by `getOwnerTarget()`, which was null for a class, so clicks fell through to the external browser. Returning the class target routes them through the link handler, and alias names in the rendered comment now navigate to the alias itself.

## 0.21.0

- Point alias names in the rendered comment at the declaring class, since the `Owner::Alias` form appeared to be rejected there. Superseded by 0.22.0, which found the real cause.

## 0.20.0

- Link the alias names written in `@phpstan-type` and `@phpstan-import-type` comments themselves. PHP renders only the class after `from` as a link, leaving both the original and the aliased name as plain text. The original name points at the alias in its declaring class, the aliased name at the alias in the using class. Applies to the rendered comment in the editor and to the class's Quick Documentation.
- Resolve an alias whose owning class lives in another file, which the original name of an import requires.

## 0.19.0

- Fix alias links opening nothing. The link handler resolved correctly, but the returned target was held through a pointer that restores itself by re-resolving a saved text range. For a link-created target that range points at the referring element (a whole method), where no alias resolves, so the pointer produced null and the documentation was discarded. Link-created targets are now held directly.

## 0.18.0

- Carry the alias inside a `psi_element://` URL as `Owner::AliasName`. A custom scheme is rendered as a link but the documentation UI never dispatches the click, so the handler was never reached. A `#` fragment breaks the platform's PSI resolution. The `::` form keeps the known scheme while leaving the alias name recoverable.
- Log every link the handler receives, so a failing click can be traced from the IDE log.

## 0.17.0

- Resolve namespace links inside this plugin's documentation. PHP emits links such as `psi_element://\App\Domain\` in its generated documentation, and while this plugin's target is shown the platform's own PSI handler stays out of the way, so those clicks ended in "documentation not found".

## 0.16.0

- Navigate from an alias name to documentation for that alias itself. The platform's link dispatch calls every `DocumentationLinkHandler` without inspecting the URL, so a custom scheme does reach the handler — the earlier attempt failed for a different reason. A `psi_element://` URL cannot carry the alias, since the platform resolves that scheme as a PSI element and a fragment makes resolution fail.

## 0.15.0

- Carry a class's PHPStan type aliases into that class's Quick Documentation, and point alias names at the class that uses them. There is no way to navigate to documentation for the alias itself: the platform resolves `psi_element://` links on its own without consulting a link handler, so only real PSI elements can be reached. Linking to the declaring class loses the name — an imported `CreateUserData` is `UserData` there — so the link goes to the using class, where the name matches and the body is listed.

## 0.14.0

- Make the alias name navigate to the alias itself rather than to its declaring class. The link handler now logs where resolution fails and falls back to the owner class instead of returning nothing, so a failure degrades to the previous behaviour rather than showing "documentation not found".
- Find the rendered PHPDoc by walking up from the range start rather than requiring an exact range match, and take the owner from `PsiDocCommentBase#getOwner` instead of walking siblings.

## 0.13.0

- Show alias names in the rendered PHPDoc shown inside the editor, not just in Quick Documentation. Rendered comments come from `inlineDocumentationProvider`, a different extension point that the PHP plugin does not implement, so the alias names were missing there. Documents without a resolvable alias are left to the platform's own rendering.

## 0.12.0

- Point alias links at the declaring class instead of encoding the alias in a URL fragment. A `psi_element://` link with a fragment made the platform attempt PSI resolution before reaching the link handler, and the click failed with "documentation not found". A plain class FQN resolves normally, and the alias definition is visible in that class's PHPDoc.

## 0.11.0

- Preserve PHP's function and method Quick Documentation while adding linked PHPStan type alias names to the existing `Parameters:` and `Returns:` sections.

## 0.10.0

- Make links inside this plugin's Quick Documentation resolve to documentation targets. The links were rendered as `psi_element://` anchors, but the platform's built-in handler only accepts PSI-backed source targets and ignored this plugin's custom target.

## 0.9.0

- Suppress the false "Argument type does not match the declared" warning when a PHPStan type alias resolves to an array shape and the native parameter type is `array`. Real mismatches, such as an array-shape alias on a `string` parameter, remain reported.

## 0.8.0

- Link class names inside the type body. In `array{accounts: non-empty-list<Account>}` the `Account` now navigates to its class. Only capitalised identifiers are considered, so PHPStan's type keywords (all lowercase) are left alone; ambiguous names fall back to the one in the declaring class's namespace, and stay unlinked if still undecided.
- Link the alias name and the declaring class to that class.

## 0.7.0

- Present a variable as `$payload: CreateUserData`, matching how the PHP plugin renders a variable (`$user_id: int`), followed by the type body.

## 0.6.0

- Show the alias on a variable. Placing the caret on a variable declared by `@param SomeAlias $var` — either the usage or the parameter itself — now resolves the alias. The PHP plugin only reports the resolved type (`array`), so the alias name was not reachable from the variable side.

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
