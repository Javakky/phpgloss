# Changelog












## 0.63.0

- Do not read an argument's type while the index is being built. Reading it resolves that expression (`PhpReferenceImpl.getType`), which is exactly what a type provider may not ask for during indexing — PhpStorm logged twenty-two `TypeProvider contract violation` exceptions on its first pass over a large project, more than the PHP plugin's own providers put together. During indexing only what is written in the source is used; once indexing is done the argument types are read as before. Fixing the array-literal path alone in 0.62.0 was not enough: the argument's own type was the larger source.

## 0.62.0

- Stop resolving other files while the index is being built. Reading an array literal's element types called `global()`, which asks for a resolve the index is not ready to answer; PhpStorm logged eighteen `TypeProvider contract violation` exceptions during its first indexing pass. The literal is now read with local types alone on that path, and the full read stays where it is allowed — resolving a call outside indexing.

## 0.61.0

- Support PhpStorm 2025.1 through the newest build, instead of 2026.2 alone. Only one thing stood in the way: `PhpType.splitTopLevelUnion` arrived in 2026.1 and accounted for all ten incompatibilities on 2025.2. The PHP plugin's own method is still used wherever it exists, and a local one steps in only where it does not — the split has to agree with the platform, and a permanent copy would drift from it.
- Match the platform's splitting exactly. Writing the fallback surfaced two disagreements the copy would have introduced silently: surrounding spaces are kept (`int | null` splits into `"int "` and `" null"`), and an empty type yields one empty member rather than none. A test compares both implementations across the shapes that occur in practice.
- No upper bound on the IDE version. Nothing in use is version-specific enough to justify locking newer IDEs out.

## 0.60.0

- Look up interfaces and traits, not only classes. Every lookup went through `getClassesByFQN`, which returns classes alone, so an alias declared on an interface could be linked but never opened — the log recorded `classes=0` and the click did nothing. Query ports and other interface-declared aliases are the common case for `@phpstan-type`. The same lookup backs imported shapes, generic type arguments and keyword definitions, all of which had the same blind spot.

## 0.59.0

- Show the alias note when hovering a call, not only its declaration. The note is what carries the alias name — PhpStorm's own `Returns: list<array>` never mentions it — and a call site is where it is usually read.
- Link the alias name in a variable's type and drop the body beside it. A clickable name is enough to reach the declaration; spelling the shape out again only made the line long.

## 0.58.0

- Keep the alias name in a variable's type instead of spelling the body out inside it. `$rows = $port->getUsers(...)` showed the whole shape inline across five lines, which is harder to read than the name it was given (reported on the real machine). The variable now reads `list<row_shape>` with `(row_shape: array{...})` beside it — the same note the method's own hover carries. The expanded form still backs the type itself, so element access keeps working.
- Stop wrapping an expanded alias in parentheses unless its body is a union. They were added for every alias and left stray brackets in the middle of a type.

## 0.57.0

- Find a type alias wherever it is written, not only when it is the whole type. `@return list<row_shape>` left the hover showing `list<array>` with no mention of the alias, because the name was looked up as the entire declared type. Every identifier in the type is now looked up, so an alias inside a type argument, a union, or an array shape is found — and several in one type are all reported.
- Write the alias body beside its name. The name alone does not say what shape it is, and PhpStorm cannot show it (`list<array>` is as far as it goes). The existing `(name)` note becomes `(name: array{...})`, cut off when the body is long. The declared type itself is left alone — it is PhpStorm's, and replacing it would drop the links and colouring it carries.
- Expand an alias when resolving a call, so a variable assigned from such a method carries the shape rather than a bare `array`.

## 0.56.0

- Show the type read from an array literal when PhpStorm's own answer disagrees. Keys written as class constants (`[Region::NORTH => [...], Region::SOUTH => [...]]`) are seen as integer keys, so a lookup table came out as `array<int, string>[]` where its keys are strings. The literal states its keys, and reading them was already done for binding type variables — the same reading now reaches the variable itself. Nothing is claimed when an element or key is unreadable, or when PhpStorm already agrees.
- Point the sweep at any project, and measure the type as shown rather than as held. Running it over a large codebase (830 files, 4,618 assignments) found the defect above, plus three faults in the collector itself: the project's own PHPStan config has to be the base (dropping it loses autoload and the analysis comes back incomplete), the default 128M memory limit is not enough, and the reported identifier was being kept as part of the type.

## 0.55.0

- Build the element type of an array literal from what is written. PhpStorm holds `[null, 1, 2]` as `array<int, mixed>`, and a type variable bound from it collapsed to `mixed`; the written elements each have a readable type, so they are joined into a union instead. Keys are read the same way, and nothing is built when an element or key is itself unreadable — a coarse guess there would overwrite what the arguments already give.
- Type the result of calling a callable parameter inside the function body. `$classifier($value, $key)` had no type at all; the PHPDoc states the return position, and while the caller is unknown the bound of the type variable can still be said (`@template G of array-key` → `int|string`, which is what PHPStan shows as `G of (int|string)`). A type variable with no bound says nothing rather than `mixed`.
- Read the return type of `callable(V, K): G`. The declared-type reader cut at the space after the colon, so the return position was lost.

## 0.54.0

- Follow an array literal assigned to a variable when a condition asks about its shape. `groupBy($input, ...)` decides on `$input is list<V>`, and PhpStorm holds even a sequential literal as `array<int, int>`, so the shape could not be told from the type — the whole call stayed unresolved and PhpStorm's own answer (`array<int, int>[]`: the group key taken from the input's key type, the value from the element type) was shown instead. Only enum cases and array literals are followed; nothing else states its value in the source.
- Sweep every assignment in a real project against PHPStan (`tools/conformance`, `ConformanceSweepTest`). Hand-found gaps leave the ones nobody looked for; the sweep reports each variable as agreeing, silent, or contradicting. It found the `groupBy` defect above independently of the report that prompted it.

## 0.53.0

- Evaluate a negated condition, `($mode is not Shape::AS_LIST ? A : B)`. The two-word `is not` form was read as a condition of its own, and the type it produced was a class named after the word (`\Probe\not` — measured). The decision is the plain one with its truth flipped, so a condition that cannot be decided stays undecided rather than picking the other branch.
- Bind a variadic parameter from every argument written for it. `@param V ...$values` only ever saw the first one, so `collect(1, 'a')` claimed `list<int>`; the arguments from the variadic position onwards are now unioned into the one declared element type. An argument passed as `...$arr` binds nothing at all: how many values of which types it carries cannot be told from the call.
- Unify an intersection, `@param T&Countable $subject`. Neither part is a type variable on its own, so nothing bound and the whole return went unresolved. The parts that are not type variables are matching conditions — subtracted from the argument type when it carries them, and unified in their own right so a type variable nested in one (`iterable<V>&Countable`) still binds. Two type variables in one intersection bind nothing, and each member of an intersection return is qualified separately (resolving `Bag&Boxed` whole looks for a class of that name).
- Qualify a class-name bound. `@template T of Wrapper` filled the variable with the short name, which reads as a class that does not exist; it now goes through the same namespace/use resolution as the rest of the declaration. Keyword bounds (`array-key`) are left alone.
- Fold a conditional whose subject is a type variable, `(R is null ? (?V) : R)` — the real `reduce`. Only `$parameter` subjects were read, so the branch text leaked out as a type (measured: `\Probe\(\int is null ? (?\int) : \int)`). The return is now folded after the arguments have been unified, since that is when `R` is known; a missing argument binds from the declared default (`$initial = null` makes the condition true), `?V` is spelled as a union because `?` alone means "no type" to PhpStorm, and an argument whose type may or may not be null decides nothing.
- Stop adding a plural form for a union element. `list<int|string>` would have added `int|string[]`, which reads as a different type.

## 0.52.0

- Bind the element type of a `map`-style call from the closure passed to it. `@param callable(V, K): E $callback` never bound `E`, so every conditional return mentioning it stayed unresolved. PhpStorm already carries the closure's shape in the argument type — `fn (int $v): string` measures as `\Closure<#ᤓvᤓ\int,\string>` — and puts only the *written* declaration in each parameter slot, the written-or-inferred type in the last. That is exactly the wanted rule: parameters bind from what was written, the return falls back to what PhpStorm inferred. A callable carrying no shape (`strlen(...)`, `'strlen'`) binds nothing rather than guessing.
- Read `callable(V, K): E` whole from the raw tag. The union reader stops at the `:`, so the return position was never seen at all.
- Match named arguments by name. `getParameters()` drops the names and hands back the values in written order, so `init(mode: ..., input: ...)` bound the mode to `$input` and the array to `$mode` and the conditional chose the other branch. Positional arguments still fill from the front, and an argument whose name matches no parameter is dropped instead of guessed at.
- Resolve a static call. `Foo::bar()` has a plain class reference as its receiver, which can carry no type arguments, and the method path gave up on every receiver without them. The type variables come from the method's own `@template` and bind from the arguments alone.
- Read `@template-covariant` and `@template-contravariant`, with the `@phpstan-` and `@psalm-` prefixes too. A bound written after `of` on such a declaration was lost, leaving a type variable that only the bound could fill unresolved. Tags are now read in written order, which is the order the type arguments are matched in.
- Trim a type argument before using it. `Box<int, \App\User>` yields the second one with its leading space, which turned the substituted type into a class that does not exist.

## 0.51.0

- Never trade precision away when fixing the hover. The resolver's own binding travels through the indexed signature, which flattens PhpStorm's constant tracking (`int<20,20>` became `int`), and fixing the hover to it hid the finer type PhpStorm already knew. With the branch decided every union member describes the same value, so a member with the same structure and a strictly finer element now replaces the resolver's coarser one. Structure must match, keeping genuine alternatives (`list<X>` vs `array<int, X>`) unmixed.

## 0.50.0

- Read every declared union whole. The parsed doc type yields only the first member — measured on `@param`, `@return` and `@var` alike — so `array<K, V>` written after `list<V>|` was never seen (which is why the key never bound from the arguments), `@var User|null` lost its null, and an `__invoke` returning `V|null` claimed the value always exists. One raw reader now serves all of them, keeping `V | null` spelled with spaces and cutting before the trailing description.
- Split the `__invoke` return on `|` before adding it, the same fix the call-site path already had.
- Give up on `@var` when the variable is assigned more than once; which annotation governs the call site cannot be told.

## 0.49.0

- Keep the `|null` of an option-style return. `@return V|null` was read through the parsed doc type, which yields only the first member of a union, so the resolved type claimed the value always exists. The whole written union is now read raw, cut off before the trailing description.
- Make keyword links inside a variable hover actually open. The link handler did not recognise the variable-hover target, so its context was unavailable and the link was drawn but dead.

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
