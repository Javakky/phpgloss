package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * プロパティに置いた型と、コンストラクタに渡した型が、読み出す側まで届くか。
 *
 * <p>どちらも実務でよく通る経路だが、こちらは一度も試していない。
 */
public final class PropertyAndConstructorTest extends BasePlatformTestCase {
    private String raw(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    private PsiFile load(String declaration, String body) {
        myFixture.addFileToProject("Declared%d.php".formatted(Math.abs(declaration.hashCode())), declaration);
        PsiFile file = myFixture.addFileToProject("probe%d.php".formatted(Math.abs(body.hashCode())), """
                <?php
                namespace Probe;

                function probe(): void
                {
                %s
                }
                """.formatted(body));
        myFixture.openFileInEditor(file.getVirtualFile());
        return file;
    }

    /** `@var` でエイリアスを書いたプロパティを読む。 */
    public void testReadsAnAliasFromAProperty() {
        var file = load("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int, label: non-empty-string}
                 */
                final class Store
                {
                    /** @var list<row> */
                    public array $rows = [];
                }
                """, """
                    $store = new Store();
                    $first = $store->rows[0];
                """);

        assertTrue("プロパティから shape が読めない: " + raw(file, "first"),
                raw(file, "first").contains("array{"));
    }

    /** プロパティ経由の shape に、キーでアクセスする。 */
    public void testReadsAKeyFromAPropertyShape() {
        var file = load("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int, label: non-empty-string}
                 */
                final class Holder
                {
                    /** @var row */
                    public array $current = [];
                }
                """, """
                    $holder = new Holder();
                    $label = $holder->current['label'];
                """);

        assertTrue("プロパティの shape でキーの型が取れない: " + raw(file, "label"),
                raw(file, "label").contains("non-empty-string"));
    }

    /** コンストラクタの引数から、クラスの型引数が決まる。 */
    public void testInfersTheTypeArgumentFromTheConstructor() {
        var file = load("""
                <?php
                namespace Probe;

                final class User {}

                /**
                 * @template T
                 */
                final class Box
                {
                    /**
                     * @param T $value
                     */
                    public function __construct(private mixed $value) {}

                    /** @return T */
                    public function get(): mixed { return $this->value; }
                }
                """, """
                    $box = new Box(new User());
                    $value = $box->get();
                """);

        assertTrue("コンストラクタから型引数が決まらない: " + raw(file, "value"),
                raw(file, "value").contains("User"));
    }

    /** 昇格プロパティ (constructor property promotion) 経由。 */
    public void testReadsAPromotedProperty() {
        var file = load("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                final class Promoted
                {
                    /**
                     * @param list<row> $rows
                     */
                    public function __construct(public array $rows) {}
                }
                """, """
                    $promoted = new Promoted([]);
                    $first = $promoted->rows[0];
                """);

        assertTrue("昇格プロパティから shape が読めない: " + raw(file, "first"),
                raw(file, "first").contains("array{"));
    }

    /** ゲッター経由で読む。 */
    public void testReadsThroughAGetter() {
        var file = load("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                final class Repository
                {
                    /** @var list<row> */
                    private array $rows = [];

                    /** @return list<row> */
                    public function rows(): array { return $this->rows; }
                }
                """, """
                    $repository = new Repository();
                    $first = $repository->rows()[0];
                """);

        assertTrue("ゲッター経由で shape が読めない: " + raw(file, "first"),
                raw(file, "first").contains("array{"));
    }
}
