package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;

import java.util.ArrayList;
import java.util.List;

/**
 * 索引を跨ぐ符号化が、型を壊さずに往復すること。
 *
 * <p>呼び出しの情報は区切り文字を使って 1 本の文字列に畳まれ、索引の先で復元される。区切りに
 * 使っている文字が型そのものに現れると、そこで切れる。PHP の型には文字列リテラルが書けるので、
 * 何が入ってくるかは書き手次第になる。
 */
public final class EncodingRoundTripTest extends BasePlatformTestCase {
    /** 符号化を通したときに壊れやすい形。 */
    private static final List<String> ARGUMENTS = List.of(
            "'ふつうの日本語'",
            "'キー: 値'",
            "'a|b'",
            "'a<b>c'",
            "'{}'",
            "'ᨣᨤᨦ'",
            "'😀'",
            "'\\\\App\\\\User'",
            "['日本語' => 1]",
            "['a' => ['b' => 'c']]");

    public void testKeepsTheTypeThroughEncoding() {
        List<String> broken = new ArrayList<>();
        for (String argument : ARGUMENTS) {
            String source = """
                    <?php
                    namespace Probe;

                    /**
                     * @template V
                     * @param V $value
                     * @return list<V>
                     */
                    function wrap($value): array { return [$value]; }

                    function probe(): void
                    {
                        $result = wrap(%s);
                    }
                    """.formatted(argument);
            var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
            var call = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                    .filter(r -> "wrap".equals(r.getName())).findFirst().orElseThrow();

            // 索引を跨ぐ経路: getType で畳み、complete で戻す
            var provider = new PhpStanCallSiteGenericTypeProvider();
            var encoded = provider.getType(call);
            if (encoded == null) {
                continue;
            }
            for (String part : encoded.getTypes()) {
                try {
                    provider.complete(part, getProject());
                } catch (Exception failure) {
                    broken.add(argument + " -> " + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
                }
            }
        }
        assertTrue("符号化の往復で落ちる型がある:\n  " + String.join("\n  ", broken), broken.isEmpty());
    }

    /** 区切りに使う文字が実引数に現れても、解決結果が壊れないこと。 */
    public void testSeparatorCharactersInTheArgumentDoNotLeak() {
        String source = """
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param V $value
                 * @return list<V>
                 */
                function wrap($value): array { return [$value]; }

                function probe(): void
                {
                    $result = wrap('ᨣᨤᨦᨧᨨᨩᨪᨬ');
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        var call = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "wrap".equals(r.getName())).findFirst().orElseThrow();

        String type = ResolvedCallType.of(call)
                .map(t -> String.join("|", t.getTypesWithParametrisedParts())).orElse("");

        assertFalse("区切り文字が型に漏れている: " + type,
                type.contains("ᨤ") || type.contains("ᨦ") || type.contains("ᨧ"));
    }
}
