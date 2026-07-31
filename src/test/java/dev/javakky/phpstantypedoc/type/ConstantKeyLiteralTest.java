package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * キーをクラス定数で書いた配列リテラル。
 *
 * <p>`[Region::NORTH => [...], Region::SOUTH => [...]]` を PhpStorm は整数キーの配列と見る (実測)。
 * PHPStan は定数の値まで解決して `array{ja: ..., en: ...}` と読む。定数の値は書かれているので、
 * キーの型は決められる。
 */
public final class ConstantKeyLiteralTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class Region
            {
                public const NORTH = 'ja';
                public const SOUTH = 'en';
            }

            /**
             * @template K of array-key
             * @template V
             * @param array<K, V> $input
             * @return V|null
             */
            function firstOrNull(array $input): mixed { return null; }

            function probe(): void
            {
                $table = [
                    Region::NORTH => ['alpha', 'beta'],
                    Region::SOUTH => ['gamma', 'delta'],
                ];
                $one = firstOrNull($table);
            }
            """;

    public void testReadsTheKeyTypeFromAConstant() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        var table = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "table".equals(v.getName())).findFirst().orElseThrow();
        var call = PsiTreeUtil.findChildrenOfType(file,
                        com.jetbrains.php.lang.psi.elements.FunctionReference.class).stream()
                .filter(r -> "firstOrNull".equals(r.getName())).findFirst().orElseThrow();

        // 呼び出しの束縛でも、変数そのものの表示でも、キーが string として読めること
        String written = ArgumentText.literalArrayTypeOf(call.getParameters()[0], getProject());
        assertTrue("実引数のキーが読めていない: " + written,
                written != null && written.replace("\\", "").startsWith("array<string,"));

        String shown = dev.javakky.phpstantypedoc.doc.VariableHoverProbe.typeAt(
                file, table.getTextRange().getStartOffset() + 1);
        assertTrue("表示のキーが整数のまま: " + shown, shown.replace(" ", "").contains("array<string,"));
    }
}
