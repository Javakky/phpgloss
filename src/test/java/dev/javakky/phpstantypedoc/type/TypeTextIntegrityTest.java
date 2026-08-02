package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;

import java.util.ArrayList;
import java.util.List;

/**
 * 型を組み立てる経路が、型を壊さないこと。
 *
 * <p>置換・修飾・展開はどれも文字列を組み立てる。括弧の釣り合いを崩したり、型キーワードに
 * 名前空間を付けたりすると、存在しない型が出る。実際に `array{...}` が `\Probe\array{...}` に
 * なっていた (0.70.0 で修正)。同じ誤りが他の経路にも潜みうるので、形の違う型をまとめて通す。
 */
public final class TypeTextIntegrityTest extends BasePlatformTestCase {
    /** 実際の PHPDoc に現れる、構造の違う型。 */
    private static final List<String> SHAPES = List.of(
            "list<V>",
            "array{a: int, b: string}",
            "array{a: int, b: array{c: string}}",
            "list<array{id: positive-int}>",
            "callable(V, int): string",
            "non-empty-list<class-string<\\App\\User>>",
            "int<1, max>",
            "array<array-key, V>",
            "V|null",
            "list<V>|array<string, V>",
            "\\App\\Holder<V>",
            "array{optional?: int}",
            // 修飾の分岐に関わる形を、一度に通す
            "?V",
            "V[]",
            "self",
            "static",
            "\\App\\User::class",
            "int<1, max>",
            "V&\\Countable",
            "list<V>&\\Countable",
            "(V|null)",
            "iterable<int, V>",
            "class-string<V>",
            "array<int, array{a: V}>",
            "callable(): void",
            "\\Closure(V): bool",
            "key-of<array{a: int}>",
            "value-of<array{a: int}>");

    /** 型として読めない形になっていないか。 */
    private void assertWellFormed(String label, String type, List<String> broken) {
        int angle = 0;
        int brace = 0;
        int paren = 0;
        for (char c : type.toCharArray()) {
            switch (c) {
                case '<' -> angle++;
                case '>' -> angle--;
                case '{' -> brace++;
                case '}' -> brace--;
                case '(' -> paren++;
                case ')' -> paren--;
                default -> { }
            }
            if (angle < 0 || brace < 0 || paren < 0) {
                broken.add(label + ": 括弧の対応が壊れている -> " + type);
                return;
            }
        }
        if (angle != 0 || brace != 0 || paren != 0) {
            broken.add(label + ": 括弧が閉じていない -> " + type);
        }
        // 型キーワードに名前空間を付けると存在しない型になる。`\int` のように `\` が 1 つ付くのは
        // PhpStorm が組み込み型を持つときの表記で、こちらは正常
        for (String keyword : List.of("array", "list", "int", "string", "callable", "iterable",
                "non-empty-list", "class-string", "array-key", "mixed", "null", "bool", "float")) {
            if (type.matches(".*\\\\\\w+\\\\" + keyword + "\\b.*")) {
                broken.add(label + ": 型キーワードに名前空間が付いている (" + keyword + ") -> " + type);
                return;
            }
        }
    }

    /** 呼び出しを解いた結果が、どの形でも壊れていないこと。 */
    public void testResolvedTypesStayWellFormed() {
        List<String> broken = new ArrayList<>();
        for (String shape : SHAPES) {
            String source = """
                    <?php
                    namespace Probe;

                    final class Holder {}
                    final class User {}

                    /**
                     * @template V
                     * @param list<V> $items
                     * @return %s
                     */
                    function build(array $items): mixed { return null; }

                    function probe(): void
                    {
                        $result = build([1, 2]);
                    }
                    """.formatted(shape);
            var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
            PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                    .filter(r -> "build".equals(r.getName())).findFirst()
                    .flatMap(ResolvedCallType::of)
                    .ifPresent(type -> type.getTypesWithParametrisedParts()
                            .forEach(part -> assertWellFormed(shape, part, broken)));
        }
        assertTrue("組み立てた型が壊れている:\n  " + String.join("\n  ", broken), broken.isEmpty());
    }

    /** callable 型を戻り値に書いたとき、戻り型まで運ばれること。 */
    public void testKeepsTheReturnTypeOfACallable() {
        String source = """
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $items
                 * @return callable(V): non-empty-string
                 */
                function formatter(array $items): callable { return fn ($v) => (string) $v; }

                function probe(): void
                {
                    $format = formatter([1, 2]);
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        String type = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "formatter".equals(r.getName())).findFirst()
                .flatMap(ResolvedCallType::of)
                .map(t -> String.join("|", t.getTypesWithParametrisedParts()))
                .orElse("");

        assertTrue("callable の戻り型が落ちている: " + type,
                type.isEmpty() || type.contains("non-empty-string"));
    }

    /** 型変数の置換が、周りの構造を保つこと。 */
    public void testSubstitutionKeepsTheStructure() {
        List<String> broken = new ArrayList<>();
        for (String shape : SHAPES) {
            String substituted = TemplateSubstitution.apply(shape, java.util.Map.of("V", "\\App\\User"));
            assertWellFormed(shape, substituted, broken);
        }
        assertTrue("置換で型が壊れている:\n  " + String.join("\n  ", broken), broken.isEmpty());
    }
}
