package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 上界がクラス名の `@template T of Wrapper`。
 *
 * <p>実引数から決められなかった型変数は上界で埋めるが、書かれているのは短い名前なので、
 * そのまま型に入れると `\Wrapper` という存在しないクラスになる。宣言が書かれたファイルの
 * namespace / use で完全修飾してから埋める。型キーワードの上界 (`array-key`) は修飾しない。
 */
public final class TemplateClassBoundTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            use Other\\Handle;

            final class Wrapper {}

            /**
             * @template T of Wrapper
             * @template V
             * @param list<V> $input
             * @return list<T>
             */
            function wrappersOf(array $input): array { return []; }

            /**
             * @template T of Handle
             * @template V
             * @param list<V> $input
             * @return list<T>
             */
            function handlesOf(array $input): array { return []; }

            /**
             * @template T of \\Other\\Handle
             * @template V
             * @param list<V> $input
             * @return list<T>
             */
            function qualifiedHandlesOf(array $input): array { return []; }

            /**
             * @template K of array-key
             * @template V
             * @param list<V> $input
             * @return list<K>
             */
            function keysOf(array $input): array { return []; }

            function probe(): void
            {
                $wrappers = wrappersOf([1, 2]);
                $handles = handlesOf([1, 2]);
                $qualified = qualifiedHandlesOf([1, 2]);
                $keys = keysOf([1, 2]);
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("Handle.php", """
                <?php
                namespace Other;

                final class Handle {}
                """);
    }

    /**
     * 同じ namespace のクラスは、その namespace の下の名前として埋まる。
     */
    public void testQualifiesABoundWrittenAsAShortName() {
        var parts = typeOf("wrappers");

        assertTrue("上界のクラス名が完全修飾されていない: " + parts,
                parts.contains("list<\\Probe\\Wrapper>"));
        assertFalse("解けたのに粗い型が混ざっている: " + parts,
                parts.contains("mixed") || parts.equals("?"));
    }

    /**
     * `use` で取り込んだクラスは、取り込み元の namespace で埋まる。呼び出し側ではなく
     * 宣言が書かれたファイルの `use` で解決する。
     */
    public void testQualifiesABoundThroughAUseStatement() {
        var parts = typeOf("handles");

        assertTrue("use で取り込んだ上界が解決されていない: " + parts,
                parts.contains("list<\\Other\\Handle>"));
    }

    /**
     * 既に完全修飾されている上界はそのまま。
     */
    public void testKeepsAnAlreadyQualifiedBound() {
        var parts = typeOf("qualified");

        assertTrue("完全修飾の上界が壊れている: " + parts,
                parts.contains("list<\\Other\\Handle>"));
    }

    /**
     * 型キーワードの上界は修飾しない。修飾すると `\Probe\array-key` という存在しない名前になる。
     */
    public void testDoesNotQualifyAKeywordBound() {
        var parts = typeOf("keys");

        assertTrue("型キーワードの上界が壊れている: " + parts,
                parts.stream().anyMatch(t -> t.contains("array-key")));
        assertFalse("型キーワードを namespace で修飾している: " + parts,
                parts.stream().anyMatch(t -> t.contains("Probe\\array-key")));
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
