package dev.javakky.phpstantypedoc.usages;

import com.intellij.find.usages.api.PsiUsage;
import com.intellij.find.usages.api.Usage;
import com.intellij.find.usages.api.UsageSearchParameters;
import com.intellij.find.usages.api.UsageSearcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PHPStan 型エイリアスの使用箇所を探す。
 *
 * <p>参照用の索引は持たない。IntelliJ の Find Usages が標準でやっているのと同じく、
 * 単語索引で名前を含むファイルへ絞ってから、各候補を実際に解決して同じエイリアスかを確かめる。
 * 自前の索引を足すと索引バージョンが上がり、全再インデックスを招く。
 *
 * <p>検索語は一つでは足りない。{@code @phpstan-import-type UserData from User as CreateUserData}
 * で導入された {@code CreateUserData} は単語として別物なので、{@code UserData} の索引では引けない。
 * import を見つけるたびにその別名を検索語に足し、打ち止めになるまで繰り返す。
 */
public final class PhpStanAliasUsageSearcher implements UsageSearcher {
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @NotNull Collection<? extends Usage> collectImmediateResults(
            @NotNull UsageSearchParameters parameters
    ) {
        if (!(parameters.getTarget() instanceof PhpStanAliasSymbol symbol)) {
            return List.of();
        }
        Collector collector = new Collector(symbol, parameters.getSearchScope(),
                PsiSearchHelper.getInstance(parameters.getProject()));
        return collector.collect();
    }

    private final class Collector {
        private final PhpStanAliasSymbol symbol;
        private final SearchScope scope;
        private final PsiSearchHelper searchHelper;

        private final List<Usage> usages = new ArrayList<>();
        private final Set<String> visitedOccurrences = new HashSet<>();
        private final Set<String> searchedNames = new LinkedHashSet<>();
        private final Deque<String> pending = new ArrayDeque<>();

        private Collector(PhpStanAliasSymbol symbol, SearchScope scope, PsiSearchHelper searchHelper) {
            this.symbol = symbol;
            this.scope = scope;
            this.searchHelper = searchHelper;
        }

        private List<Usage> collect() {
            enqueue(symbol.aliasName());
            while (!pending.isEmpty()) {
                searchWord(pending.removeFirst());
            }
            return usages;
        }

        private void enqueue(String name) {
            if (name != null && !name.isBlank() && searchedNames.add(name)) {
                pending.addLast(name);
            }
        }

        private void searchWord(String name) {
            searchHelper.processElementsWithWord(
                    (element, offsetInElement) -> {
                        visit(name, element, offsetInElement);
                        return true;
                    },
                    scope,
                    name,
                    UsageSearchContext.ANY,
                    true
            );
        }

        /**
         * 同じ位置が要素の階層ごとに何度も渡されるため、ファイルと開始位置で一度きりに絞る。
         */
        private void visit(String name, PsiElement element, int offsetInElement) {
            TextRange elementRange = element.getTextRange();
            PsiFile file = element.getContainingFile();
            if (elementRange == null || file == null) {
                return;
            }
            if (PsiTreeUtil.getParentOfType(element, PhpDocComment.class, false) == null) {
                return;
            }
            int offset = elementRange.getStartOffset() + offsetInElement;
            if (!visitedOccurrences.add(occurrenceKey(file, offset))) {
                return;
            }
            importedNameAt(file, offset).ifPresent(this::enqueue);
            if (PhpStanAliasDeclarations.at(file, offset).isPresent()) {
                // 宣言そのものは使用箇所に出さない。Find Usages の一覧では別枠になるため
                return;
            }
            if (!resolvesToSymbol(file, offset)) {
                return;
            }
            usages.add(PsiUsage.textUsage(file, new TextRange(offset, offset + name.length())));
        }

        private boolean resolvesToSymbol(PsiFile file, int offset) {
            return resolver.resolveAt(file, offset)
                    .filter(target -> symbol.equals(new PhpStanAliasSymbol(
                            target.resolvedType().definingClassFqcn(),
                            target.resolvedType().originalName())))
                    .isPresent();
        }

        /**
         * この位置が {@code @phpstan-import-type} の元名なら、そこで導入される別名を返す。
         * 別名は単語として別物なので、検索語に足さないとその利用箇所が丸ごと落ちる。
         */
        private Optional<String> importedNameAt(PsiFile file, int offset) {
            PhpFileModel fileModel = PhpFileModelCache.of(file);
            return fileModel.findDocBlockContainingOffset(offset)
                    .flatMap(block -> block.imports().stream()
                            .filter(typeImport -> typeImport.originalNameRange().containsExclusiveEnd(offset))
                            .findFirst())
                    .map(PhpStanTypeImport::localName);
        }

        private String occurrenceKey(PsiFile file, int offset) {
            var virtualFile = file.getVirtualFile();
            return (virtualFile == null ? file.getName() : virtualFile.getPath()) + ":" + offset;
        }
    }
}
