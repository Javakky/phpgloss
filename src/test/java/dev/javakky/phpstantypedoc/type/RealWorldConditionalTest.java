package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 実際のコード (the sample project の `init`) の PHPDoc をそのまま写して測る。
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
            }
            """;

    public void testResolvesTheListBranch() {
        var parts = typeOf("asList");

        assertTrue("list 側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
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
