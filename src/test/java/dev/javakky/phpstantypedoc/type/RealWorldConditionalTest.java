package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 実際のコードにある入れ子の条件型 の PHPDoc をそのまま写して測る。
 *
 * <p>自分で書いた fixture では通っていたが実物では効いていなかった。差は
 * `@phpstan-param` にも条件型が書かれていること、`@template K of array-key` の境界、
 * 3 段の入れ子。
 */
public final class RealWorldConditionalTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php

            namespace Example\\Collection;

            enum Shape
            {
                public static function resolveShape(Shape $mode, array $input): Shape { return $mode; }

                case AS_KEYED;
                case AS_LIST;
                case AUTO;
            }

            /**
             * 配列の最後の要素を除いたすべての要素を返します。
             *
             * @template K of array-key
             * @template V
             *
             * @param list<V>|array<K, V> $input 対象の配列
             * @phpstan-param ($mode is Shape::AS_LIST ? list<V> :
             *   ($mode is Shape::AS_KEYED ? array<K, V> :
             *     list<V>|array<K, V>
             * )) $input
             * @return list<V>|array<K, V>
             * @phpstan-return ($mode is Shape::AS_LIST ? list<V> :
             *     ($mode is Shape::AS_KEYED ? array<K, V> :
             *       ($input is list<V> ? list<V> :
             *         array<K, V>
             *  )))
             */
            function init(array $input, Shape $mode = Shape::AUTO): array
            {
                return $input;
            }

            function probe(): void
            {
                $asList = init([1, 2, 3], Shape::AS_LIST);
                $asAssoc = init(['a' => 1], Shape::AS_KEYED);
                $auto = init([1, 2, 3]);
            }
            """;

    public void testResolvesTheListBranch() {
        var parts = typeOf("asList");

        assertTrue("list 側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 素の `@param` が無く `@phpstan-param` の条件型しか無い場合も、同じ実引数で畳めば
     * 型変数を決められる。`@param` へ落とすだけの実装ではここが解けない。
     */
    public void testResolvesWhenOnlyTheConditionalParamIsWritten() {
        String source = SOURCE.replace("             * @param list<V>|array<K, V> $input 対象の配列\n", "");
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "asList".equals(v.getName())).findFirst().orElseThrow();
        var parts = ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();

        assertTrue("条件型の @phpstan-param から型変数を決められていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * `$mode` を書かずに呼ぶと、宣言の既定値 `Shape::AUTO` で条件が決まる。AUTO は LIST とも
     * ASSOC とも別のケースなので外側 2 段は偽側へ抜け、3 段目の `$input is list<V>` が
     * 配列リテラルで決まる。実物の `init` で一番よく書かれる呼び方がこれになる。
     */
    public void testFoldsThreeLevelsWhenTheModeIsOmitted() {
        var parts = typeOf("auto");

        assertTrue("3 段目まで畳めていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<") && t.contains("int")));
    }

    public void testDistinguishesTheTwoModes() {
        var asList = typeOf("asList");
        var asAssoc = typeOf("asAssoc");

        assertNotSame("モードを変えても結果が同じ: " + asList, asList, asAssoc);
        assertFalse("assoc 側で list が選ばれている: " + asAssoc,
                asAssoc.stream().anyMatch(t -> t.startsWith("list<")));
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
