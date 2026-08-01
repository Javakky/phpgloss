package dev.javakky.phpstantypedoc;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * trait に書かれた型エイリアス。README が「クラスと同じように働く」と言っている形。
 *
 * <p>宣言と利用は別のファイルに置く。同じファイルでは索引を経由せず解決できてしまう。
 */
public final class TraitAliasTest extends BasePlatformTestCase {
    private static final String TRAIT = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type page_record array{page_id: positive-int, title: non-empty-string}
             */
            trait PageRecords
            {
                /** @return list<page_record> */
                public function pages(): array
                {
                    return [];
                }
            }
            """;

    private static final String USER = """
            <?php
            namespace Probe;

            final class Catalog
            {
                use PageRecords;

                public function run(): void
                {
                    $rows = $this->pages();
                }
            }
            """;

    public void testResolvesAnAliasDeclaredOnATrait() {
        myFixture.addFileToProject("PageRecords.php", TRAIT);
        PsiFile file = myFixture.addFileToProject("Catalog.php", USER);
        myFixture.openFileInEditor(file.getVirtualFile());

        var rows = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "rows".equals(v.getName())).findFirst().orElseThrow();
        String shown = dev.javakky.phpstantypedoc.doc.VariableHoverProbe.typeAt(
                file, rows.getTextRange().getStartOffset() + 1);

        assertTrue("trait のエイリアスが解決されない: " + shown, shown.contains("page_record"));
    }

    /** trait のエイリアスへのリンクが辿れること。 */
    public void testResolvesALinkToATraitAlias() {
        myFixture.addFileToProject("PageRecords.php", TRAIT);
        PsiFile file = myFixture.addFileToProject("Catalog.php", USER);
        myFixture.openFileInEditor(file.getVirtualFile());

        var rows = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "rows".equals(v.getName())).findFirst().orElseThrow();
        var target = new dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, rows.getTextRange().getStartOffset() + 1);

        assertFalse("target が返らない", target.isEmpty());
        var resolved = new dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationLinkHandler()
                .resolveLink(target.getFirst(), "psi_element://\\Probe\\PageRecords::page_record");

        assertNotNull("trait のエイリアスへ遷移できない", resolved);
    }
}
