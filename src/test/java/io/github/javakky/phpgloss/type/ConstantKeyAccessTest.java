package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/** クラス定数をキーにした配列への要素アクセスと、null 合体との組み合わせ。 */
public final class ConstantKeyAccessTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class Region
            {
                public const NORTH = 'north';
                public const SOUTH = 'south';
            }

            function probe(): void
            {
                $table = [
                    Region::NORTH => ['alpha', 'beta'],
                    Region::SOUTH => ['gamma', 'delta'],
                ];
                $direct = $table[Region::NORTH];
                $coalesced = $table[Region::NORTH] ?? [];
                $missing = $table['unknown'] ?? null;
            }
            """;

    private String typeOf(String variable) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName())).findFirst()
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    /**
     * 定数をキーにした配列への要素アクセスが、値の型に辿り着くこと。
     *
     * <p>PhpStorm はキーを整数と見るので、要素アクセスも誤った型を辿る。表示だけを直しても
     * ここは直らないため、型そのものに正しい形を足している。
     */
    public void testResolvesTheElementBehindAConstantKey() {
        assertTrue("要素の型に辿り着けない: " + typeOf("direct"), typeOf("direct").contains("list<string>"));
    }

    /** `??` を挟んでも要素の型は残る。既定値の型が加わるだけ。 */
    public void testKeepsTheElementTypeThroughNullCoalescing() {
        assertTrue("null 合体で要素の型が落ちる: " + typeOf("coalesced"),
                typeOf("coalesced").contains("list<string>"));
    }

    /** 書かれていないキーへのアクセスでも、既定値の型が出る。 */
    public void testKeepsTheDefaultOfAMissingKey() {
        assertTrue("既定値の型が出ない: " + typeOf("missing"), typeOf("missing").contains("null"));
    }
}
