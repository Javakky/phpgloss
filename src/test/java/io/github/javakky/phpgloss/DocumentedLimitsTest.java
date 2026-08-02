package io.github.javakky.phpgloss;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * README の「できないこと」が本当にできないかを確かめる。
 *
 * <p>制限を書いておきながら実は動く、あるいは書いていない制限がある、のどちらも読み手を裏切る。
 * ここが落ちたら README を直す。
 */
public final class DocumentedLimitsTest extends BasePlatformTestCase {
    private String hoverOf(String source, String variable) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName())).findFirst()
                .map(v -> io.github.javakky.phpgloss.doc.VariableHoverProbe.typeAt(
                        file, v.getTextRange().getStartOffset() + 1))
                .orElse("");
    }

    /** 「メソッドに書いたエイリアスは読まない」。 */
    public void testDoesNotReadAnAliasDeclaredOnAMethod() {
        String shown = hoverOf("""
                <?php
                namespace Probe;

                final class Port
                {
                    /**
                     * @phpstan-type inline_record array{id: positive-int}
                     * @return list<inline_record>
                     */
                    public function all(): array { return []; }
                }

                function probe(Port $port): void
                {
                    $rows = $port->all();
                }
                """, "rows");

        assertFalse("メソッドのエイリアスを読んでいる (README と食い違う): " + shown,
                shown.contains("inline_record"));
    }

    /** 「継承したエイリアスは暗黙には解決しない」。 */
    public void testDoesNotInheritAnAliasImplicitly() {
        String shown = hoverOf("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type base_record array{id: positive-int}
                 */
                interface BasePort {}

                interface ChildPort extends BasePort
                {
                    /** @return list<base_record> */
                    public function all(): array;
                }

                function probe(ChildPort $port): void
                {
                    $rows = $port->all();
                }
                """, "rows");

        assertFalse("継承したエイリアスを解決している (README と食い違う): " + shown,
                shown.contains("base_record"));
    }
}
