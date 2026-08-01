package dev.javakky.phpstantypedoc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Variable;
import dev.javakky.phpstantypedoc.type.ResolvedCallType;

/**
 * 呼び出しの解決とホバーが、大きなファイルでも待たされない速さで済むこと。
 *
 * <p>数字は環境で動くので、絶対値でなく「桁」で見る。1 呼び出しあたりミリ秒を大きく超えるなら、
 * エディタの入力が引っかかる。
 */
public final class PerformanceTest extends BasePlatformTestCase {
    private static final int CALLS = 300;

    private String bigFile() {
        StringBuilder source = new StringBuilder("""
                <?php
                namespace Probe;

                enum Shape { case AS_LIST; case AS_KEYED; case AUTO; }

                /**
                 * @template K of array-key
                 * @template V
                 * @template E
                 * @param list<V>|array<K, V> $input
                 * @param callable(V, K): E $callback
                 * @phpstan-return ($mode is Shape::AS_LIST ? list<E> :
                 *   ($mode is Shape::AS_KEYED ? array<K, E> :
                 *     ($input is list<V> ? list<E> : array<K, E>)))
                 */
                function convert(array $input, callable $callback, Shape $mode = Shape::AUTO): array
                {
                    return $input;
                }

                function probe(): void
                {
                """);
        for (int i = 0; i < CALLS; i++) {
            source.append("    $result").append(i)
                    .append(" = convert([1, 2, 3], fn (int $v, int $k): string => (string) $v, Shape::AS_LIST);\n");
        }
        return source.append("}\n").toString();
    }

    public void testResolvesManyCallsQuickly() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, bigFile());
        var calls = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "convert".equals(r.getName())).toList();
        assertEquals(CALLS, calls.size());

        long start = System.nanoTime();
        calls.forEach(ResolvedCallType::of);
        long firstPass = System.nanoTime() - start;

        start = System.nanoTime();
        calls.forEach(ResolvedCallType::of);
        long secondPass = System.nanoTime() - start;

        long perCallMicros = firstPass / CALLS / 1000;
        if (Boolean.getBoolean("perf.report")) { fail("1回目 " + firstPass/1000000 + "ms / 2回目 " + secondPass/1000000 + "ms / 1呼び出し " + perCallMicros + "us"); }
        assertTrue("1 呼び出しあたり " + perCallMicros + " マイクロ秒かかっている", perCallMicros < 5000);
        assertTrue("二度目が速くなっていない (キャッシュが効いていない): "
                        + firstPass / 1_000_000 + "ms → " + secondPass / 1_000_000 + "ms",
                secondPass < firstPass);
    }

    public void testHoversManyVariablesQuickly() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, bigFile());
        var variables = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> v.getName() != null && v.getName().startsWith("result")).toList();

        long start = System.nanoTime();
        variables.forEach(v -> dev.javakky.phpstantypedoc.doc.VariableHoverProbe.typeAt(
                file, v.getTextRange().getStartOffset() + 1));
        long elapsed = System.nanoTime() - start;

        long perHoverMicros = elapsed / Math.max(1, variables.size()) / 1000;
        if (Boolean.getBoolean("perf.report")) { fail("ホバー " + elapsed/1000000 + "ms / " + variables.size() + " 変数 / 1変数 " + perHoverMicros + "us"); }
        assertTrue("1 変数あたり " + perHoverMicros + " マイクロ秒かかっている", perHoverMicros < 5000);
    }
}
