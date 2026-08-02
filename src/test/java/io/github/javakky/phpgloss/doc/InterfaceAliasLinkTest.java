package io.github.javakky.phpgloss.doc;

import com.intellij.platform.backend.documentation.LinkResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * インターフェースに書かれた型エイリアスへのリンクが辿れること。
 *
 * <p>照会に `getClassesByFQN` を使っていたためインターフェースが引けず、リンクは描かれるのに
 * クリックしても何も起きなかった (実機のログに `classes=0` として残っていた)。trait も同じ。
 *
 * <p>宣言と利用は別のファイルに置く。同じファイルに書くと索引を引かずに解決できてしまい、
 * この欠陥を再現できない。
 */
public final class InterfaceAliasLinkTest extends BasePlatformTestCase {
    private static final String PORT = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row_shape array{id: positive-int, name: string}
             */
            interface RecordPort
            {
                /**
                 * @return list<row_shape>
                 */
                public function fetchRows(): array;
            }
            """;

    private static final String USE_CASE = """
            <?php
            namespace Probe;

            final class UseCase
            {
                public function __construct(private RecordPort $port) {}

                public function run(): void
                {
                    $rows = $this->port->fetchRows();
                }
            }
            """;

    public void testResolvesALinkToAnAliasDeclaredOnAnInterface() {
        myFixture.addFileToProject("RecordPort.php", PORT);
        PsiFile file = myFixture.addFileToProject("UseCase.php", USE_CASE);
        myFixture.openFileInEditor(file.getVirtualFile());

        var rows = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "rows".equals(v.getName())).findFirst().orElseThrow();
        var target = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, rows.getTextRange().getStartOffset() + 1)
                .getFirst();

        LinkResolveResult resolved = new PhpStanTypeDocumentationLinkHandler().resolveLink(
                target, "psi_element://\\Probe\\RecordPort::row_shape");

        assertNotNull("インターフェースのエイリアスへ遷移できない", resolved);
    }
}
