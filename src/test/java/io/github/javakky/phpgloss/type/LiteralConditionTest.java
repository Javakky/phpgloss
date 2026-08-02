package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 型を条件に書いた条件型 (`$size is positive-int`) を、書かれたリテラルで判定する。
 *
 * <p>enum ケースと違い、条件側は型キーワードで実引数側は値になる。値がリテラルで書かれていれば
 * どちらの分岐かは決まる。
 */
public final class LiteralConditionTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @phpstan-return ($size is positive-int ? non-empty-list<V> : list<V>)
             */
            function sized(array $input, mixed $size): array { return $input; }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @phpstan-return ($key is string ? array<string, V> : list<V>)
             */
            function keyed(array $input, mixed $key): array { return $input; }

            function probe(): void
            {
                $positive = sized([new User()], 5);
                $zero = sized([new User()], 0);
                $negative = sized([new User()], -3);
                $text = sized([new User()], 'x');
                $unknownSize = sized([new User()], $given);
                $byString = keyed([new User()], 'a');
                $byInt = keyed([new User()], 7);
            }
            """;

    public void testPicksThePositiveBranchForAPositiveLiteral() {
        assertHas("positive", "non-empty-list<\\Probe\\User");
    }

    public void testPicksTheOtherBranchForZero() {
        var parts = typeOf("zero");

        assertTrue("0 が positive-int として扱われている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\Probe\\User")));
    }

    public void testPicksTheOtherBranchForANegativeLiteral() {
        var parts = typeOf("negative");

        assertTrue("負の数が positive-int として扱われている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\Probe\\User")));
    }

    /**
     * 文字列リテラルは整数の型キーワードに当たらない。
     */
    public void testPicksTheOtherBranchForAStringLiteral() {
        var parts = typeOf("text");

        assertTrue("文字列が positive-int として扱われている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\Probe\\User")));
    }

    /**
     * リテラルでなければ値が分からない。誤った分岐を出すより何も足さない。
     */
    public void testAddsNothingForANonLiteral() {
        var parts = typeOf("unknownSize");

        assertFalse("値が分からないのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<") || t.startsWith("list<")));
    }

    public void testPicksTheStringBranchForAStringLiteral() {
        var parts = typeOf("byString");

        assertFalse("文字列なのに list 側が選ばれている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    public void testPicksTheOtherBranchForAnIntegerLiteral() {
        assertHas("byInt", "list<\\Probe\\User");
    }

    private void assertHas(String variableName, String expectedPrefix) {
        var parts = typeOf(variableName);

        assertTrue("期待した分岐が出ていない (" + expectedPrefix + "): " + parts,
                parts.stream().anyMatch(t -> t.startsWith(expectedPrefix)));
    }

    private Set<String> typeOf(String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
