package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * `@phpstan-type` で宣言した型エイリアスを、戻り値の型引数の中でも展開する。
 *
 * <p>宣言と同じクラスの中で使うエイリアスは import が要らない。呼び出し側から見ると
 * `list<row_shape>` の `row_shape` は宣言元のクラスのものなので、そこで解決できる。
 */
public final class AliasInReturnTypeTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row_shape array{
             *     user_id: positive-int,
             *     user_name: string
             * }
             */
            interface RecordPort
            {
                /**
                 * @param list<positive-int> $ids
                 * @return list<row_shape>
                 */
                public function getUsers(array $ids): array;

                /**
                 * @return row_shape|null
                 */
                public function getUser(int $id): ?array;
            }

            final class UseCase
            {
                public function __construct(private RecordPort $port) {}

                public function run(): void
                {
                    $rows = $this->port->getUsers([1, 2]);
                    $one = $this->port->getUser(1);
                }
            }
            """;

    private String typeOf(String method) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        return PsiTreeUtil.findChildrenOfType(file,
                        com.jetbrains.php.lang.psi.elements.MethodReference.class).stream()
                .filter(r -> method.equals(r.getName())).findFirst()
                .flatMap(ResolvedCallType::of)
                .map(t -> String.join("|", t.getTypesWithParametrisedParts()))
                .orElse("");
    }

    public void testExpandsAnAliasInsideATypeArgument() {
        String type = typeOf("getUsers");
        assertTrue("エイリアスが展開されていない: " + type, type.contains("user_id"));
    }

    public void testExpandsAnAliasUsedDirectly() {
        String type = typeOf("getUser");
        assertTrue("エイリアスが展開されていない: " + type, type.contains("user_id"));
    }

    /** 変数の表示にも反映される。 */
    public void testShowsTheExpandedAliasOnTheVariable() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        var rows = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "rows".equals(v.getName())).findFirst().orElseThrow();

        String shown = dev.javakky.phpstantypedoc.doc.VariableHoverProbe.typeAt(
                file, rows.getTextRange().getStartOffset() + 1);

        assertTrue("変数の表示にエイリアスが開かれていない: " + shown, shown.contains("user_id"));
    }

    /**
     * メソッド自身のホバー (Ctrl+Q) には、エイリアス名に続けて本体が添えられる。
     *
     * <p>PhpStorm は `list<row_shape>` を `list<array>` としか出せない。型を差し替えるのでなく、
     * 既存の `(エイリアス名)` の追記に本体を足す形に揃える。
     */
    public void testShowsTheExpandedAliasOnTheMethod() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf("public function getUsers") + "public function get".length();

        var targets = new dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset);
        String html = targets.isEmpty() ? ""
                : String.valueOf(targets.getFirst().computeDocumentation());

        String visible = html.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">");
        assertTrue("エイリアス名が出ていない: " + visible, visible.contains("row_shape"));
        assertTrue("本体が添えられていない: " + visible, visible.contains("user_id"));
    }
}
