package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/** 戻り値の宣言が無いとき、実装の return から何が読めているか。 */
public final class InferredReturnTest extends BasePlatformTestCase {
    private String typeOf(String source, String variable) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> {
                    // 表示に出る型。整理が効くのはこちら
                    String shown = io.github.javakky.phpgloss.doc.VariableHoverProbe.typeAt(
                            file, v.getTextRange().getStartOffset() + 1);
                    return shown.isEmpty()
                            ? "(素) " + String.join("|", TypeUnionSimplifier.normalize(
                                    ((PhpTypedElement) v).getType().global(getProject())
                                            .getTypesWithParametrisedParts()))
                            : shown;
                })
                .orElse("");
    }

    /**
     * 戻り値の宣言が無いとき、実装の `return` から推論した型が並ぶ。同じ式を複数の経路が
     * 読むので、同じ構造で粗いだけの成分が混ざる。精密な方があるなら、粗い方は要らない。
     */
    public void testDropsCoarserSiblingsOfAnInferredReturn() {
        String shapeOrNull = typeOf("""
                <?php
                namespace Probe;

                function find(bool $flag)
                {
                    if ($flag) {
                        return ['id' => 1, 'name' => 'x'];
                    }
                    return null;
                }

                function probe(): void { $result = find(true); }
                """, "result");

        String scalars = typeOf("""
                <?php
                namespace Probe;

                function pick(int $n)
                {
                    if ($n > 0) {
                        return 'positive';
                    }
                    if ($n < 0) {
                        return -1;
                    }
                    return false;
                }

                function probe(): void { $result = pick(1); }
                """, "result");

        String sameShapeTwice = typeOf("""
                <?php
                namespace Probe;

                function build(bool $flag)
                {
                    if ($flag) {
                        return ['id' => 1];
                    }
                    return ['id' => 2];
                }

                function probe(): void { $result = build(true); }
                """, "result");

        // 同じ構造で粗いだけの成分は、精密な方が同じ union にいるなら読み手に何も足さない
        assertFalse("mixed 版が具体版と並んでいる: " + shapeOrNull, shapeOrNull.contains("mixed"));
        assertTrue("具体的な要素型が落ちた: " + shapeOrNull, shapeOrNull.contains("int|string"));
        assertTrue("null の枝が落ちた: " + shapeOrNull, shapeOrNull.contains("null"));

        // `int<-1,-1>` は `int` に含まれる。同じ `return` を二通りに読んだ結果
        assertFalse("包まれる側と包む側が並んでいる: " + scalars,
                scalars.replace("int<-1,-1>", "").contains("int"));
        assertTrue("枝ごとの型が落ちた: " + scalars,
                scalars.contains("false") && scalars.contains("string"));

        // 枝ごとに違う shape は選択肢。両方残り、両方を包む粗い版だけが落ちる
        assertTrue("枝の型が落ちた: " + sameShapeTwice,
                sameShapeTwice.contains("int<1,1>") && sameShapeTwice.contains("int<2,2>"));
        assertFalse("両方を包む粗い版が残っている: " + sameShapeTwice,
                sameShapeTwice.replace("int<1,1>", "").replace("int<2,2>", "").contains("int"));
    }
}
