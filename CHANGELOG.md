# Changelog












## 0.48.0

- Keep a refinement written in `@var` — `list<positive-int>` gave `null|int`, because PhpStorm's type flattens `positive-int` to `int` before anything can read it. The written text survives nowhere else, so it is read from the PHPDoc and matched structurally against the declared parameter (`list<V>` × `list<positive-int>` binds `V`), and carried through the indexing pass alongside the argument.
- Resolve a plain template return (`@return V|null`) instead of leaving it to PhpStorm, which resolves `V` but only to the flattened type. Returns that mention no type variable at all are still left alone.
- Split the substituted return on `|` before adding it, and substitute before qualifying so a bare unresolved `V` cannot slip through as a class name.

## 0.47.0

- Keep the standard hover chrome — the namespace link, the coloured variable name, the clickable source — when fixing the type. The patch looked for the literal `$name:`, which never matches because the standard HTML wraps the name in a coloured span, so every hover fell back to the bare minimal page (reported on the real machine). The colon is now found by skipping over tags, and the inserted type text links its class names and keywords.
- Link the type text in the minimal fallback page too.

## 0.46.0

- Fix the hover to the resolved type when this plugin's own resolver has decided the branch of the call the variable was assigned from. A decided branch is a refinement, not another approximation, so the coarser members of the union (`array<int, int>` beside a decided `list<int>`) are known to co-describe the same value and can be dropped. An undecided call keeps the conservative folding, where such a pair may be genuine alternatives.
- Cache the in-place resolution per call expression. The same call is resolved by the type-provider pass, the `__invoke` path and now the hover; the result is computed once and rebuilt on PSI change.

## 0.45.0

- Patch the type text inside PHP's own hover instead of replacing the whole page. The rebuilt hover lost the namespace link and the full source path (reported on the real machine); the standard HTML now stays and only the type is swapped.
- Fold `array<array-key, X>` into `X[]` — an unbounded key says nothing — so the bound-filled key from the resolver is subsumed by PhpStorm's own `array<string, int>` instead of being shown beside it.
- Treat spellings that differ only in spacing (`array<string,int>` vs `array<string, int>`) as one type. Folding `array<int, X>` into `list<X>` was considered and rejected: a union may hold genuine alternatives — an unresolved conditional keeps both branches — and there keeping only the narrower side would claim a list for a value that may not be one. Only types never written as an alternative on their own (`?`, `mixed`, bare `array`, the plural form) are folded.

## 0.44.0

- Drop the abstract types a more precise one in the same union already covers when hovering a variable. A variable's type is the union of several signature parts, so `array` and the plural form stay beside a resolved `list<int>` (measured: `$result: array|int[]|list`). The other parts cannot be removed from the provider side — a type provider can only stop its own key from being read — so they are folded away at display time. Only subsumption is folded: `int|string` and `\Foo[]|\Bar[]` are left alone.
- Drop `mixed` from a key access into an array shape. PhpStorm always adds it to a key access, so a value resolved to `string` was shown as `mixed|string`.
- Leave the standard hover alone when nothing is dropped, so a variable whose type is already precise keeps everything the PHP plugin writes about it.
- Do not add the plural form when it is the same type as the generic form it accompanies. `array<User>` is another spelling of `User[]`, so adding it put the same type in the union twice without making any element reachable.

## 0.43.0

- Fill an argument that is not written at the call site with the parameter's declared default. `init($input)` decides its condition on `Shape::AUTO`, which is neither of the two named cases, so the nested conditional folds all three levels down to the shape of the array literal.
- Decide a condition written on the shape of a value from the argument's type when no literal is written. A `list<...>` is a list; an `array<...>` whose key is a string is not. Anything else stays undecided.
- Decide a condition written on a type (`positive-int`, `string`, …) from a numeric or string literal. Non-literals stay undecided.
- Take the key of `array<K, V>` from the argument's own type argument instead of widening it to `array-key`. Carrying the type through as a string dropped the type arguments, which is why the key was unreachable in the first place.
- Fill a type variable the arguments do not determine with the bound written after `of`, rather than assuming `array-key`. A type variable without a bound is left alone and nothing is inferred, as before.
- Follow one assignment when an enum case is passed through a variable. Several assignments, or an assignment inside a branch, leave the value undecided.
- Compare enum cases by fully qualified name, resolved against the file each side is written in. Two enums with the same short name in different namespaces are no longer treated as the same one; when either side cannot be resolved, nothing is claimed.
- Match a resolved function by fully qualified name so that `\A\filter` and `\B\filter` are not mixed, preferring the caller's namespace and falling back to the global one.
- Substitute the internally encoded type variables through PhpStorm's own mapping API when the return type is only written as `@psalm-return`, which is not read as text.

## 0.42.0

- Fold a conditional `@phpstan-param` against the same arguments before matching it, rather than falling back to the plain `@param`. A condition states which type is accepted, not the shape that determines the type variable, so reading it as-is bound nothing. Folding it first also works when only the conditional parameter is written.

## 0.41.0

- Read the plain `@param` when `@phpstan-param` is itself a conditional type. Real code writes the condition on both the parameter and the return, and reading the conditional as a parameter type bound no type variable at all — so nothing was inferred. Everything had been verified on hand-written fixtures, which did not have this shape.

## 0.40.0

- Fix element access on a resolved call. `$xs = opOf(...)(); $xs[0]` gave the type one step back, because an unresolved type placeholder in the union stops PhpStorm from reading the element. Calls are resolved in place outside indexing, and the plural form is added alongside the generic one so the element type is reachable without losing `list`.

## 0.39.0

- Decide a condition written on the shape of a value (`$input is list<V>`) from the array literal at the call site: keys written means it is not a list. A variable leaves it undecided, and nothing is added.
- Fill the key of `array<K, V>` with `array-key`, since the arguments carry no key type. Without it that whole branch was unusable. PhpStorm normalises `array<K, V>` to `V[]` regardless, so the key does not survive into the shown type.
- Split a union in a declared parameter before unifying. `non-empty-list<V>|array<K, V>` was read as one type and bound nothing.

## 0.38.0

- Resolve type variables on method calls too, taking them from the receiver's type argument as well as from the call's arguments. A conditional return type on a method is evaluated the same way.
- Resolve a method call in place rather than carrying the receiver's signature through to the completion pass, which loses the type argument.

## 0.37.0

- Fold nested conditional return types instead of reading only the outermost one. Real code nests them three deep, and stopping at the first level emitted the remaining `($mode is ... )` text as if it were a type.
- Strip the PHPDoc line decoration before reading a type that spans several lines; the leading `*` made the nested condition unreadable.

## 0.36.0

- Evaluate a conditional return type against the argument at the call site. `($mode is Shape::AS_LIST ? non-empty-list<V> : array<K, V>)` now picks a branch when the argument is an enum case; PhpStorm parses the syntax but never evaluates it, returning the union of both branches whatever is passed (YouTrack WI-85338 and WI-77577, both open).
- Say nothing when the branch cannot be decided — an omitted argument, or a branch whose type variables the arguments do not determine. A wrong type is worse than none.

## 0.35.0

- Resolve a type variable that sits inside a type argument. PhpStorm resolves `@return T` from the call's arguments but leaves `@return Op<V>` as `Op<#V>`, so everything downstream loses the element type.
- Carry that type argument into the class's `__invoke`, so `$op()` and `opOf(...)()` keep both the element type and the `non-empty-list` structure instead of collapsing to `array` (YouTrack WI-65938, open since 2022).
- Substitute the type variable on the PHPDoc text rather than through PhpStorm's own `substituteTypesWithMapping`, which only matches the internally encoded form and left the written `V` untouched.

## 0.34.0

- Carry the upstream English wording for every keyword, taken from the one-line class docblocks in Psalm's `Type\Atomic\T*.php` (matched to keywords through `Atomic::create()`) and from PHPStan's accessory types. Where no upstream sentence exists the English is written here.
- Show the description in the IDE's language: Japanese when the UI is Japanese, the English otherwise.
- Write the Japanese as complete sentences. Trailing-off and noun-stopped endings leave the reader to supply the verb; a test rejects them.

## 0.33.0

- Treat a word as a keyword only where a type is actually written. Being inside a PHPDoc is not enough: prose mentions `list`, and `@phpstan-import-type ... as X` puts `as` there too.
- Decide that position from the `PhpDocType` the PHP parser already builds, rather than counting brackets by hand. The hand-written scan works a line at a time and lost a multi-line array shape; unions, intersections, parentheses and line breaks are all covered by tests now.

## 0.31.0

- Explain `min` and `max`. Neither appears in the reserved-word tables: they are bounds inside `int<1, max>`, held as `TIntRange::BOUND_MIN` in Psalm and compared by name in PHPStan. Building the catalogue from the reserved tables alone missed them by construction.

## 0.30.0

- Land on where a keyword's meaning is decided, not merely where the word is listed. Psalm's `PSALM_RESERVED_WORDS` is a tokeniser word table; `Atomic::create()` is what returns `TIntRange(1, null)` for `positive-int`. The chain is now Psalm `Atomic` → PHPStan `TypeNodeResolver` → Psalm `TPropertiesOf` → `TypeParser` → the word table, and a test names the three keywords that still reach only the table and why.

## 0.29.0

- Show the keyword's own documentation when the caret is on it, not only when its link is followed. PHP answers that position with the resolved type (`\App\User[]`), which never says what the keyword means.
- Go to the analyser's definition when Go to Declaration is pressed on a keyword. PHP sees plain text there and reported that no declaration could be found.

## 0.28.0

- Explain the type keywords — `non-empty-string`, `positive-int`, `non-empty-list` and the rest — in Japanese, and navigate from one to where the analyser defines it. Psalm lists them in `TypeTokenizer::PSALM_RESERVED_WORDS`, PHPStan as `case` labels in `TypeNodeResolver`; both files are bundled (MIT, licences included) so the jump works even without the analyser installed, and the project's own copy under `vendor/` is preferred when present.
- Decide what is a keyword from the enumerated list rather than the shape of the name. An alias may be written in camelCase or snake_case, so a lowercase-leading name says nothing on its own. A test parses the bundled sources and fails when the catalogue falls behind them.
- Point PHP's own keyword links at the keyword instead of the element class. PHP resolves `non-empty-list<User>` to `User[]` and links the keyword text there, which answers a question nobody asked.

## 0.27.0

- Resolve a link to an array type. PHP writes those as the element class with `[]` appended (`non-empty-list<User>` becomes `psi_element://\App\Domain\User[]`), which was rejected as a class name and reported the URL as malformed. Stopgap until type keywords carry their own documentation.

## 0.26.0

- Search for usages when Go to Declaration is pressed on a `@phpstan-type` name, matching what the IDE does for a class or method name. The name in PHPDoc has no PSI that counts as a declaration, so its text range is declared as a symbol instead. An imported alias resolves to the same symbol as the original, so either end lists the whole set.
- Find the alias through the word index rather than a new index of references, which would force a full reindex. A name introduced by `@phpstan-import-type ... as X` is a different word, so `X` is added to the search as soon as the import is seen.

## 0.25.0

- Leave a class's rendered comment alone when the class declares no type aliases. Since 0.20 every class comment was taken over, which now matters because 0.24 made this plugin's rendering win.
- Take the base HTML from the language's registered documentation provider rather than constructing PHP's directly, so anything another plugin adds to PHP's documentation survives.

## 0.24.0

- Show alias names in the rendered comment, which had never worked there. The rendered view collects every provider's items into a map keyed by text range, so the last provider registered wins the displayed HTML, while link clicks come from the first provider that answers. Registering at only one end left the HTML coming from PHP and the links from here.

## 0.23.0

- Log the rendered comment HTML. The rendered view cannot be inspected through the IDE's MCP tools, so its actual output was being guessed at rather than observed.

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
