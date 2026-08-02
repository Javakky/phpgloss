package io.github.javakky.phpgloss;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Variable;
import io.github.javakky.phpgloss.type.ResolvedCallType;

/**
 * 呼び出しの解決とホバーが、大きなファイルでも待たされない速さで済むこと。
 *
 * <p>絶対値は走らせる機械で変わる。開発機では 1 呼び出し 2.4 ms、ホバー 2.5 ms だが、CI の
 * 共有ランナーはその数倍かかる。そこで閾値は「明らかに壊れている」水準に置き、細かい退行は
 * `-Dperf.report=true` で数字を見て判断する。
 *
 * <p>環境に依らないのは**二度目が安いこと**で、そちらは常に確かめる。キャッシュが効かなくなれば
 * 編集のたびに全部が計算し直され、機械の速さでは埋められない。
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
        assertTrue("1 呼び出しあたり " + perCallMicros + " マイクロ秒かかっている", perCallMicros < 50_000);
        assertTrue("二度目が速くなっていない (キャッシュが効いていない): "
                        + firstPass / 1_000_000 + "ms → " + secondPass / 1_000_000 + "ms",
                secondPass < firstPass);
    }

    public void testHoversManyVariablesQuickly() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, bigFile());
        var variables = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> v.getName() != null && v.getName().startsWith("result")).toList();

        long start = System.nanoTime();
        variables.forEach(v -> io.github.javakky.phpgloss.doc.VariableHoverProbe.typeAt(
                file, v.getTextRange().getStartOffset() + 1));
        long elapsed = System.nanoTime() - start;

        long perHoverMicros = elapsed / Math.max(1, variables.size()) / 1000;
        if (Boolean.getBoolean("perf.report")) { fail("ホバー " + elapsed/1000000 + "ms / " + variables.size() + " 変数 / 1変数 " + perHoverMicros + "us"); }
        assertTrue("1 変数あたり " + perHoverMicros + " マイクロ秒かかっている", perHoverMicros < 50_000);
    }
}
