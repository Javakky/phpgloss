package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * `@param callable(V, K): E $callback` の型変数を、渡されたクロージャから決める。
 *
 * <p>PhpStorm はクロージャの型を `\Closure` の型引数として持っている。実測した形:
 *
 * <ul>
 *   <li>`fn (int $v): string => ...` → `\Closure&lt;#ᤓvᤓ\int,\string&gt;`</li>
 *   <li>`fn (int $v, string $k): Holder => ...` → `\Closure&lt;#ᤓvᤓ\int,#ᤓkᤓ\string,\Probe\Holder&gt;`</li>
 *   <li>`fn ($v) =&gt; $v * 2` → `\Closure&lt;#ᤓvᤓ,\floatᤓ\int&gt;`
 *       (型宣言の無い引数は空。戻り値は PhpStorm が推論した union)</li>
 *   <li>`fn () =&gt; 1` → `\Closure&lt;\intᤓ\int&lt;1,1&gt;&gt;` (引数が無ければ戻り値だけが並ぶ)</li>
 *   <li>`strlen(...)` → `\Closure` / `'strlen'` → `\string` (どちらも型引数を持たない)</li>
 * </ul>
 *
 * <p>つまり `#ᤓ` で始まる型引数が仮引数一つ分 (`#ᤓ` + 名前 + `ᤓ` + **書かれた**型宣言) で、
 * 残りの一つが戻り値。`ᤓ` は型の中では union の区切りにもなる。引数の型は書かれた宣言しか
 * 載らないため、そのまま使えば「書かれた宣言だけを使う」という条件を満たす。戻り値は
 * 書かれていれば宣言がそのまま、無ければ推論された型が載る。
 *
 * <p>型引数を持たない `\Closure` (第一級 callable 構文) や文字列の callable からは何も決めない。
 */
final class CallableUnification {
    /** PhpStorm がクロージャの型引数で使う区切り。引数の印にも union の区切りにも使われる (実測)。 */
    private static final char SEPARATOR = 'ᤓ';
    /** 型引数が仮引数一つ分であることを表す印。 */
    private static final String PARAMETER_MARKER = "#" + SEPARATOR;
    private static final String CLOSURE = "\\Closure";
    /** PHPDoc 側で callable として書かれうる名前。 */
    private static final Set<String> DECLARED_BASES = Set.of("callable", "closure", "\\closure");

    private CallableUnification() {
    }

    /**
     * @param declared  PHPDoc に書かれた仮引数の型。`callable(V, K): E` の形でなければ何もしない
     * @param signature 実引数の型 (`\Closure<#ᤓvᤓ\int,\string>`)
     * @param templates 解きたい型変数の名前
     * @param bindings  結果の書き込み先。既に決まっている型変数は上書きしない
     */
    static void unify(
            Project project,
            @Nullable String declared,
            String signature,
            Collection<String> templates,
            Map<String, String> bindings
    ) {
        Shape written = declaredShapeOf(declared);
        Shape actual = closureShapeOf(signature);
        if (written == null || actual == null) {
            return;
        }
        for (int i = 0; i < written.parameters().size() && i < actual.parameters().size(); i++) {
            String actualParameter = actual.parameters().get(i);
            if (!actualParameter.isBlank()) {
                // 書かれた型どうしの照合。PhpStorm の型を経由すると精密化が潰れる
                TemplateUnification.unifyText(
                        written.parameters().get(i), actualParameter, templates, bindings);
            }
        }
        if (written.returnType() == null || actual.returnType() == null) {
            return;
        }
        // 戻り値は推論された型のこともあり、未解決のシグネチャを含みうる。型として復元してから照合する
        TemplateUnification.unify(
                written.returnType(),
                CallSiteGenericResolver.actualTypeOf(project, actual.returnType()),
                templates,
                bindings);
    }

    /**
     * callable の形。
     *
     * @param returnType 戻り位置に書かれた型。書かれていなければ null
     */
    private record Shape(List<String> parameters, @Nullable String returnType) {
    }

    /**
     * `callable(V, K): E` を読む。callable 以外の型、括弧を持たない素の `callable` からは何も作らない。
     */
    private static @Nullable Shape declaredShapeOf(@Nullable String declared) {
        if (declared == null) {
            return null;
        }
        String trimmed = declared.trim();
        int open = trimmed.indexOf('(');
        if (open <= 0
                || !DECLARED_BASES.contains(trimmed.substring(0, open).trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        int close = closingOf(trimmed, open);
        if (close < 0) {
            return null;
        }
        String afterParameters = trimmed.substring(close + 1).trim();
        String returnType = afterParameters.startsWith(":")
                ? afterParameters.substring(1).trim()
                : "";
        return new Shape(
                topLevelParts(trimmed.substring(open + 1, close)),
                returnType.isEmpty() ? null : returnType);
    }

    /**
     * 実引数の型から `\Closure<...>` を探して読む。型引数を持たない `\Closure` は形が無いので null。
     */
    private static @Nullable Shape closureShapeOf(String signature) {
        for (String member : PhpType.splitTopLevelUnion(signature)) {
            int angle = member.indexOf('<');
            if (angle < 0 || !member.endsWith(">") || !CLOSURE.equals(member.substring(0, angle))) {
                continue;
            }
            List<String> parameters = new ArrayList<>();
            String returnType = null;
            for (String part : topLevelParts(member.substring(angle + 1, member.length() - 1))) {
                if (part.startsWith(PARAMETER_MARKER)) {
                    parameters.add(declaredTypeOf(part));
                } else {
                    returnType = part.replace(SEPARATOR, '|');
                }
            }
            return new Shape(parameters, returnType);
        }
        return null;
    }

    /**
     * `#ᤓvᤓ\int` から `\int` を取り出す。型宣言が書かれていなければ空文字列。
     */
    private static String declaredTypeOf(String parameterPart) {
        int type = parameterPart.indexOf(SEPARATOR, PARAMETER_MARKER.length());
        return type < 0 ? "" : parameterPart.substring(type + 1).replace(SEPARATOR, '|');
    }

    /**
     * 入れ子の中の `,` を区切りにせずに分ける。
     *
     * <p>{@code PhpType#getParametrizedParts} は使えない。クロージャの型引数に埋まった未解決
     * シグネチャの中の `;` を `,` に書き換えてしまう (実測)。
     */
    private static List<String> topLevelParts(String arguments) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= arguments.length(); i++) {
            if (i == arguments.length() || (depth == 0 && arguments.charAt(i) == ',')) {
                String part = arguments.substring(start, i).trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                start = i + 1;
                continue;
            }
            char c = arguments.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            }
        }
        return parts;
    }

    /**
     * @return {@code open} の位置の `(` に対応する `)` の位置。閉じていなければ -1
     */
    private static int closingOf(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
