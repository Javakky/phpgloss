package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;

/**
 * 条件型の主語は enum だけではない。PHPStan の記法では、型・値・引数の有無でも分岐できる。
 *
 * <p>これまでの fixture は enum で分岐する形に偏っていた (条件型 15 個のうち 11 個)。
 * 一つのライブラリの書き方に寄ると、そこで通ることしか確かめられない。
 */
public final class ConditionalShapesTest extends BasePlatformTestCase {
    private String resolve(String source, String function) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        return PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> function.equals(r.getName())).findFirst()
                .flatMap(ResolvedCallType::of)
                .map(t -> String.join("|", TypeUnionSimplifier.normalize(
                        t.getTypesWithParametrisedParts())))
                .orElse("");
    }

    /** 主語が型そのもの。`$value is int` はリテラルの型で決まる。 */
    public void testDecidesOnTheTypeOfTheArgument() {
        String type = resolve("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-return ($value is int ? non-empty-string : int)
                 */
                function render(int|string $value): mixed { return $value; }

                function probe(): void
                {
                    $shown = render(42);
                }
                """, "render");

        assertTrue("整数の側の分岐を選べていない: " + type, type.contains("string"));
    }

    /** 主語が引数そのもの (`$strict is true`)。真偽リテラルで決まる。 */
    public void testDecidesOnABooleanLiteral() {
        String type = resolve("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $items
                 * @phpstan-return ($strict is true ? non-empty-list<V> : list<V>)
                 */
                function ensure(array $items, bool $strict): array { return $items; }

                function probe(): void
                {
                    $checked = ensure([1, 2], true);
                }
                """, "ensure");

        assertTrue("真の側の分岐を選べていない: " + type, type.contains("non-empty-list"));
    }

    /** どちらとも決められない主語では何も足さない。 */
    public void testSaysNothingWhenTheSubjectIsUnknown() {
        String type = resolve("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $items
                 * @phpstan-return ($strict is true ? non-empty-list<V> : list<V>)
                 */
                function ensure(array $items, bool $strict): array { return $items; }

                function probe(bool $unknown): void
                {
                    $checked = ensure([1, 2], $unknown);
                }
                """, "ensure");

        assertFalse("決められない条件で分岐を選んでいる: " + type, type.contains("non-empty-list"));
    }
}
