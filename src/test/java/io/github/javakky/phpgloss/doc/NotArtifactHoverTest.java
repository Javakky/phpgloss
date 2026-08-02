package io.github.javakky.phpgloss.doc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * `is not` 条件型があると PhpStorm 自身が `\Probe\not` という存在しないクラスを union に混ぜる
 * (条件式の `not` をクラス名と読むため)。解決器の出力には無く、分岐が確定していれば確定した型に
 * 固めるので表示にも出ない。この残骸が表に出ないことを固定する。
 */
public final class NotArtifactHoverTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            enum Shape { case AS_LIST; case AS_KEYED; case AUTO; }

            /**
             * @template V
             * @param list<V> $input
             * @phpstan-return ($mode is not Shape::AS_LIST ? array<string, V> : list<V>)
             */
            function shaped(array $input, Shape $mode = Shape::AUTO): array { return $input; }

            function probe(): void
            {
                $result = shaped([1, 2, 3], Shape::AS_LIST);
            }
            """;

    public void testDoesNotShowTheNotArtifact() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "result".equals(v.getName())).findFirst().orElseThrow();
        var raw = ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();

        var target = SimplifiedVariableTypeTarget.at(file, PsiTreeUtil.findChildrenOfType(file, Variable.class)
                .stream().filter(v -> "result".equals(v.getName())).findFirst().orElseThrow()
                .getTextRange().getStartOffset() + 2);

        assertTrue("target が返らない (raw=" + raw + ")", target.isPresent());
        String html = String.valueOf(target.get().computeDocumentation());
        assertFalse("条件型の残骸 (\\Probe\\not) が表示に出ている: " + html + " / raw=" + raw,
                html.contains("\\Probe\\not") || html.contains(">not<"));
    }
}
