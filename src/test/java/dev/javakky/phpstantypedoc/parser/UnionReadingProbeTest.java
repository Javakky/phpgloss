package dev.javakky.phpstantypedoc.parser;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;

/**
 * union を含む型の読み取り。{@code typeNameFor} は PhpDocType 経由で union の先頭しか返さない
 * (実測で @param / @return / @var すべて)。{@code unionTypeFor} が丸ごと読むことを固定する。
 */
public final class UnionReadingProbeTest extends BasePlatformTestCase {
    public void testWhatTypeNameForReturnsForUnions() {
        String source = """
                <?php
                namespace Probe;

                final class User {}

                /**
                 * @param \\Probe\\User|null $a
                 * @param list<int>|array<string, int> $b
                 * @return \\Probe\\User | null 最後の要素、または空なら null
                 */
                function f(?User $a, array $b): mixed
                {
                    /** @var \\Probe\\User|null $x */
                    $x = null;
                    return $x;
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        var docs = PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class).iterator();
        PhpDocComment fn = docs.next();
        PhpDocComment var = docs.next();

        assertEquals("\\Probe\\User|null",
                PhpDocDeclaredTypeReader.unionTypeFor(fn, PhpDocDeclaredTypeReader.PARAM_TAGS, "a"));
        assertEquals("list<int>|array<string, int>",
                PhpDocDeclaredTypeReader.unionTypeFor(fn, PhpDocDeclaredTypeReader.PARAM_TAGS, "b"));
        assertEquals("\\Probe\\User|null",
                PhpDocDeclaredTypeReader.unionTypeFor(fn, PhpDocDeclaredTypeReader.RETURN_TAGS, null));
        assertEquals("\\Probe\\User|null",
                PhpDocDeclaredTypeReader.unionTypeFor(var, PhpDocDeclaredTypeReader.VAR_TAGS, "x"));
    }
}
