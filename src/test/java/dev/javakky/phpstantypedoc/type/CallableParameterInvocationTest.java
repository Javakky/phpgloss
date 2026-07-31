package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 関数の本体で callable の仮引数を呼んだ結果に、PHPDoc の戻り位置の型を出す。
 *
 * <p>PhpStorm は型なし (`?`) を返す。呼び出し元が決まらないので型変数そのものは埋められないが、
 * `@template G of array-key` の上界までは言える (PHPStan も `G of (int|string)` と表示する)。
 */
public final class CallableParameterInvocationTest extends BasePlatformTestCase {
    private String typeOf(String source, String variable) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .findFirst()
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject()).getTypes())))
                .orElse("");
    }

    public void testTakesTheBoundOfTheReturnTypeVariable() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template K of array-key
                 * @template V
                 * @template G of array-key
                 * @param list<V>|array<K, V> $input
                 * @param callable(V, K): G $classifier
                 * @return array<G, list<V>>
                 */
                function groupBy(array $input, callable $classifier): array
                {
                    $groupKey = $classifier(1, 2);
                    return [];
                }
                """, "groupKey");

        assertTrue("上界から int|string を出せていない: " + type,
                type.contains("int") && type.contains("string"));
    }

    public void testTakesAConcreteReturnTypeAsWritten() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @param callable(int): non-empty-string $format
                 */
                function render(callable $format): void
                {
                    $text = $format(1);
                }
                """, "text");

        assertTrue("書かれた戻り型を出せていない: " + type, type.contains("string"));
    }

    /** 上界の無い型変数では何も言わない。`mixed` を出しても読み手には伝わらない。 */
    public void testSaysNothingForAnUnboundedTypeVariable() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template E
                 * @param callable(int): E $callback
                 */
                function run(callable $callback): void
                {
                    $value = $callback(1);
                }
                """, "value");

        assertFalse("上界の無い型変数から型を出している: " + type,
                type.contains("int") || type.contains("string"));
    }

    /** 名前を持つ通常の関数呼び出しは奪わない。 */
    public void testDoesNotTouchANamedCall() {
        String type = typeOf("""
                <?php
                namespace Probe;

                function helper(): int { return 1; }

                /**
                 * @param callable(int): string $callback
                 */
                function run(callable $callback): void
                {
                    $value = helper();
                }
                """, "value");

        assertFalse("通常の関数呼び出しに介入している: " + type, type.contains("string"));
    }
}
