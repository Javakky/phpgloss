package io.github.javakky.phpgloss;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Variable;
import io.github.javakky.phpgloss.type.ResolvedCallType;

/**
 * 壊れた入力でも落ちないこと。公開すると、書きかけの PHPDoc の上でも走る。
 */
public final class RobustnessTest extends BasePlatformTestCase {
    private void resolveEverything(String source) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        // 例外が出れば落ちる。型が出るかどうかはここでは問わない
        PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).forEach(ResolvedCallType::of);
        PsiTreeUtil.findChildrenOfType(file, Variable.class).forEach(variable ->
                io.github.javakky.phpgloss.doc.VariableHoverProbe.typeAt(
                        file, variable.getTextRange().getStartOffset() + 1));
    }

    /** 自分自身を指すエイリアス。開き続けると止まらない。 */
    public void testSurvivesACircularAlias() {
        resolveEverything("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type a array{next: b}
                 * @phpstan-type b array{next: a}
                 */
                interface Loop
                {
                    /** @return list<a> */
                    public function all(): array;
                }

                function probe(Loop $loop): void
                {
                    $rows = $loop->all();
                }
                """);
    }

    /** 閉じていない型。書いている途中の PHPDoc はこうなる。 */
    public void testSurvivesAnUnclosedType() {
        resolveEverything("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V $items
                 * @return array{a: int
                 */
                function broken(array $items): array { return $items; }

                function probe(): void
                {
                    $result = broken([1, 2]);
                }
                """);
    }

    /** 条件型が閉じていない。分岐の途中まで書いた状態。 */
    public void testSurvivesAnUnclosedConditional() {
        resolveEverything("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $items
                 * @phpstan-return ($items is list<V> ? list<V>
                 */
                function half(array $items): array { return $items; }

                function probe(): void
                {
                    $result = half([1]);
                }
                """);
    }

    /** 深く入れ子になった型。折り返しの上限を超える形。 */
    public void testSurvivesDeeplyNestedTypes() {
        StringBuilder nested = new StringBuilder("int");
        for (int depth = 0; depth < 60; depth++) {
            nested.insert(0, "list<").append(">");
        }
        resolveEverything("""
                <?php
                namespace Probe;

                /**
                 * @param %s $deep
                 * @return %s
                 */
                function deep(array $deep): array { return $deep; }

                function probe(): void
                {
                    $result = deep([]);
                }
                """.formatted(nested, nested));
    }

    /** 引数が自分自身を呼ぶ形。再帰の入口。 */
    public void testSurvivesSelfReferencingCall() {
        resolveEverything("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $items
                 * @return list<V>
                 */
                function wrap(array $items): array { return $items; }

                function probe(): void
                {
                    $result = wrap(wrap(wrap([1])));
                }
                """);
    }
}
