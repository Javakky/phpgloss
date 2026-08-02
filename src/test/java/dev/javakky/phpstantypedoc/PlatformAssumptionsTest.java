package dev.javakky.phpstantypedoc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.List;

/**
 * PhpStorm の振る舞いについて、実装が置いている前提。
 *
 * <p>コード中の「実測」と書かれた判断は、書いた時点の PhpStorm を見て決めたもの。IDE が変われば
 * 前提の方が古くなり、実装は正しいまま動かなくなる。前提が崩れたらここが落ちる。
 */
public final class PlatformAssumptionsTest extends BasePlatformTestCase {
    /** `PhpType#add(String)` は `|` を union として分割しない。 */
    public void testAddDoesNotSplitAUnion() {
        PhpType type = new PhpType().add("int|string");
        assertEquals("add が union を分割するようになった: " + type.getTypes(),
                1, type.getTypes().size());
    }

    /** `getTypes()` は型引数を落とし、`getTypesWithParametrisedParts()` は残す。 */
    public void testOnlyOneAccessorKeepsTypeArguments() {
        PhpType type = new PhpType().add("list<\\App\\User>");
        assertFalse("getTypes が型引数を残すようになった: " + type.getTypes(),
                String.join("", type.getTypes()).contains("<"));
        assertTrue("getTypesWithParametrisedParts が型引数を落とすようになった: "
                        + type.getTypesWithParametrisedParts(),
                String.join("", type.getTypesWithParametrisedParts()).contains("<"));
    }

    /** 配列リテラルの要素は `getChildren()` に現れる。 */
    public void testArrayLiteralElementsAreChildren() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                function probe(): void { $x = [1, 2, 3]; }
                """);
        var literal = PsiTreeUtil.findChildrenOfType(file, ArrayCreationExpression.class)
                .stream().findFirst().orElseThrow();

        var children = literal.getChildren();
        assertEquals("要素の数が変わった: "
                        + java.util.Arrays.stream(children).map(c -> c.getText()).toList(),
                3, children.length);
        assertEquals("要素が式として取れない", "1", children[0].getText());
        assertTrue("要素が型を持たない",
                children[0] instanceof PhpTypedElement
                        || PsiTreeUtil.findChildOfType(children[0], PhpTypedElement.class) != null);
    }

    /** 名前付き引数は `getParameters()` から名前が落ち、値だけが書かれた順に返る。 */
    public void testNamedArgumentsLoseTheirNames() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                function target(int $first, string $second): void {}
                function probe(): void { target(second: 'x', first: 1); }
                """);
        var call = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "target".equals(r.getName())).findFirst().orElseThrow();

        var written = List.of(call.getParameters()).stream().map(p -> p.getText()).toList();
        assertEquals("名前付き引数に名前が残るようになった: " + written,
                List.of("'x'", "1"), written);
    }

    /** クロージャの型引数は global 解決して初めて現れる。local 型は `\Closure` だけ。 */
    public void testClosureShapeNeedsGlobalResolution() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                function target(callable $c): void {}
                function probe(): void { target(fn (int $v): string => (string) $v); }
                """);
        var call = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "target".equals(r.getName())).findFirst().orElseThrow();
        var closure = (PhpTypedElement) call.getParameters()[0];

        assertEquals("local 型にクロージャの形が乗るようになった: " + closure.getType(),
                "\\Closure", closure.getType().toString());
        assertTrue("global 解決でもクロージャの形が取れない: "
                        + closure.getType().global(getProject()).getTypesWithParametrisedParts(),
                closure.getType().global(getProject()).getTypesWithParametrisedParts()
                        .stream().anyMatch(t -> t.contains("<")));
    }

    /** 連番の配列リテラルも `array<int, …>` として持たれ、list とは区別されない。 */
    public void testSequentialLiteralIsNotSeenAsAList() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                function probe(): void { $x = [1, 2, 3]; }
                """);
        var variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "x".equals(v.getName())).findFirst().orElseThrow();
        String type = String.join("|", ((PhpTypedElement) variable).getType()
                .global(getProject()).getTypesWithParametrisedParts());

        assertFalse("連番リテラルが list として持たれるようになった: " + type, type.contains("list<"));
    }
}
