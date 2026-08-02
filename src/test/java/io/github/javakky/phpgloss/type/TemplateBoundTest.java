package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * `array<K, V>` のキー側の型変数がどう決まるかを確認する。
 *
 * <p>キーは実引数の型引数から取る。取れないときだけ `@template K of array-key` の上界で埋める。
 * 上界が書かれていなければ埋めず、型を出さない。
 */
public final class TemplateBoundTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @template K of array-key
             * @template V
             * @param array<K, V> $input
             * @return list<K>
             */
            function keysOf(array $input): array { return array_keys($input); }

            /**
             * @template K of array-key
             * @template V
             * @param list<V> $input
             * @return list<K>
             */
            function boundedKeysOf(array $input): array { return []; }

            /**
             * @template K
             * @template V
             * @param list<V> $input
             * @return list<K>
             */
            function unboundedKeysOf(array $input): array { return []; }

            function probe(): void
            {
                $stringKeys = keysOf(['a' => 1]);
                $intKeys = keysOf([1, 2, 3]);
                $bounded = boundedKeysOf([1, 2, 3]);
                $unbounded = unboundedKeysOf([1, 2, 3]);
            }
            """;

    /**
     * キーを書いた配列リテラルなら、キーの型は実引数から取れる。`array-key` で埋めると
     * 書かれた情報より粗くなる。
     */
    public void testTakesTheKeyTypeFromTheArgument() {
        var parts = typeOf("stringKeys");

        assertTrue("キー型を実引数から取れていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<") && t.contains("string")));
    }

    public void testTakesAnIntegerKeyType() {
        var parts = typeOf("intKeys");

        assertTrue("キー型を実引数から取れていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<") && t.contains("int")));
        assertFalse("キー型が上界のままになっている: " + parts,
                parts.stream().anyMatch(t -> t.contains("array-key")));
    }

    /**
     * 実引数がキーの型を持たないときは、`of` に書かれた上界で埋める。埋めないとこの型変数を
     * 含む分岐が丸ごと出せない。
     */
    public void testFillsAnUndeterminedTemplateWithItsBound() {
        var parts = typeOf("bounded");

        assertTrue("上界で埋められていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<") && t.contains("array-key")));
    }

    /**
     * 上界が書かれていない型変数は埋めない。何で埋めても根拠がないため、型を出さない。
     */
    public void testAddsNothingForATemplateWithoutABound() {
        var parts = typeOf("unbounded");

        assertFalse("根拠なく型変数を埋めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
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
