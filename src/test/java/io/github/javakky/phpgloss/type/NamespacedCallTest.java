package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 名前が同じで namespace が違うものを混同しないことを確認する。
 *
 * <p>索引後の解決は短い名前しか持ち回らないため、`\A\pick` と `\B\pick`、`\A\Shape` と `\B\Shape` が
 * 区別されずに混ざりうる。
 */
public final class NamespacedCallTest extends BasePlatformTestCase {
    private static final String IN_A = """
            <?php
            namespace A;

            final class User {}

            enum Shape
            {
                case AS_LIST;
                case AS_KEYED;
            }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @return list<V>
             */
            function pick(array $input): array { return $input; }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @phpstan-return ($mode is Shape::AS_LIST ? non-empty-list<V> : list<V>)
             */
            function shaped(array $input, Shape $mode): array { return $input; }

            function probe(): void
            {
                $fromA = pick([new User()]);
                $sameEnum = shaped([new User()], Shape::AS_LIST);
            }
            """;

    private static final String IN_B = """
            <?php
            namespace B;

            enum Shape
            {
                case AS_LIST;
                case AS_KEYED;
            }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @return iterable<V>
             */
            function pick(array $input): iterable { return $input; }

            function probe(): void
            {
                $fromB = pick([new \\A\\User()]);
                $otherEnum = \\A\\shaped([new \\A\\User()], Shape::AS_LIST);
            }
            """;

    /**
     * 呼び出し元の namespace にある関数の戻り値だけを使う。短い名前で引くと、同名の別関数の
     * 戻り値まで union に混ざる。
     */
    public void testUsesTheFunctionInTheCallersNamespace() {
        var parts = typeInB("fromB");

        assertTrue("呼び出し元 namespace の関数が使われていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("iterable<\\A\\User")));
        assertFalse("同名の別 namespace の関数が混ざっている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    public void testUsesTheOtherFunctionFromTheOtherFile() {
        myFixture.configureByText(PhpFileType.INSTANCE, IN_B);
        PsiFile fileA = myFixture.addFileToProject("a.php", IN_A);

        var parts = typeOf(fileA, "fromA");

        assertTrue("宣言と同じ namespace の関数が使われていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\A\\User")));
        assertFalse("同名の別 namespace の関数が混ざっている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("iterable<")));
    }

    /**
     * 条件に書かれた enum と実引数の enum が同じ短い名前でも、namespace が違えば別物。
     * 「同じ enum の別ケース」と断定できないので何も足さない。
     */
    public void testDoesNotConflateEnumsWithTheSameShortName() {
        var parts = typeInB("otherEnum");

        assertFalse("別 namespace の同名 enum を同じものとして扱っている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<") || t.startsWith("list<")));
    }

    /**
     * 同じ namespace の enum なら、これまでどおり判定する。
     */
    public void testStillDecidesWhenTheEnumIsTheSame() {
        myFixture.configureByText(PhpFileType.INSTANCE, IN_B);
        PsiFile fileA = myFixture.addFileToProject("a.php", IN_A);

        var parts = typeOf(fileA, "sameEnum");

        assertTrue("同じ enum で判定できていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<\\A\\User")));
    }

    private Set<String> typeInB(String variableName) {
        myFixture.addFileToProject("a.php", IN_A);
        return typeOf(myFixture.configureByText(PhpFileType.INSTANCE, IN_B), variableName);
    }

    private Set<String> typeOf(PsiFile file, String variableName) {
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
