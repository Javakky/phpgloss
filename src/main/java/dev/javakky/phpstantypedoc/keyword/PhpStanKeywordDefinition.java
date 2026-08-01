package dev.javakky.phpstantypedoc.keyword;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;

import java.util.List;
import java.util.Optional;

/**
 * 型キーワードが実際に定義されている場所を探す。
 *
 * <p>キーワードは PHP の言語機能ではなく静的解析ツール側の語彙なので、定義は解析ツールの
 * ソースにある。両ツールとも一箇所に列挙しているため、そこを指せば「定義へ飛ぶ」が成立する。
 *
 * <ul>
 *   <li>Psalm — {@code TypeTokenizer::PSALM_RESERVED_WORDS} の該当行
 *   <li>PHPStan — {@code TypeNodeResolver} の該当 {@code case} 行
 * </ul>
 *
 * <p>まずプロジェクトの {@code vendor/} を見る。実際に使っているバージョンの定義が読めるため。
 * 入っていない場合 (PHPStan は phar 配布でソースが読めないこともある) は同梱したコピーへ飛ぶ。
 */
public final class PhpStanKeywordDefinition {
    /**
     * 上から順に試す。並びは「その語の意味を決めている場所」が先。
     *
     * <p>Psalm の {@code TypeTokenizer::PSALM_RESERVED_WORDS} はトークナイザ用の単語表でしかなく、
     * 飛んでも「その語が存在する」ことしか分からない。意味は {@code Atomic::create()} が
     * 型オブジェクトを返す形で持っている。総称型 ({@code key-of} 等) は
     * {@code TypeParser} 側で扱われるので、そこも見る。
     */
    private static final List<Source> SOURCES = List.of(
            new Source("\\Psalm\\Type\\Atomic", "case '%s':", "psalm-Atomic.php"),
            new Source("\\PHPStan\\PhpDoc\\TypeNodeResolver", "case '%s':", "phpstan-TypeNodeResolver.php"),
            new Source("\\Psalm\\Type\\Atomic\\TPropertiesOf", "'%s'", "psalm-TPropertiesOf.php"),
            new Source("\\Psalm\\Type\\Atomic\\TIntRange", "'%s'", "psalm-TIntRange.php"),
            new Source("\\Psalm\\Internal\\Type\\TypeParser", "'%s'", "psalm-TypeParser.php"),
            new Source("\\Psalm\\Internal\\Type\\TypeTokenizer", "'%s' => true", "psalm-TypeTokenizer.php")
    );

    private PhpStanKeywordDefinition() {
    }

    /**
     * @param bundled プロジェクトの vendor でなく同梱のコピーを指しているか
     */
    public record Location(VirtualFile file, int offset, boolean bundled) {
    }

    public static Optional<Location> find(Project project, String keyword) {
        return inProject(project, keyword).or(() -> bundled(keyword));
    }

    private static Optional<Location> inProject(Project project, String keyword) {
        if (DumbService.isDumb(project)) {
            return Optional.empty();
        }
        PhpIndex index = PhpIndex.getInstance(project);
        for (Source source : SOURCES) {
            for (PhpClass phpClass : index.getAnyByFQN(source.classFqn())) {
                PsiFile file = phpClass.getContainingFile();
                VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
                if (virtualFile == null) {
                    continue;
                }
                int offset = file.getText().indexOf(source.pattern().formatted(keyword));
                if (offset >= 0) {
                    return Optional.of(new Location(virtualFile, offset, false));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Location> bundled(String keyword) {
        for (Source source : SOURCES) {
            Optional<String> text = PhpStanBundledSource.text(source.bundledFileName());
            if (text.isEmpty()) {
                continue;
            }
            int offset = text.get().indexOf(source.pattern().formatted(keyword));
            if (offset < 0) {
                continue;
            }
            Optional<Location> location = PhpStanBundledSource.extract(source.bundledFileName())
                    .map(file -> new Location(file, offset, true));
            if (location.isPresent()) {
                return location;
            }
        }
        return Optional.empty();
    }

    private record Source(String classFqn, String pattern, String bundledFileName) {
    }
}
