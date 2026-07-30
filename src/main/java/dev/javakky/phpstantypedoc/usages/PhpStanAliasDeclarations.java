package dev.javakky.phpstantypedoc.usages;

import com.intellij.psi.PsiFile;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanTypeAliasDefinition;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;
import dev.javakky.phpstantypedoc.model.SourceRange;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;

import java.util.Optional;

/**
 * PHPDoc 内で「エイリアスを宣言している名前」を見つける。
 *
 * <p>宣言とみなすのは {@code @phpstan-type} の名前と、{@code @phpstan-import-type ... as X} の
 * {@code X} だけ。import の元名 ({@code UserData from User} の {@code UserData}) は他クラスの
 * 宣言への参照なので、そこでは宣言でなく定義への移動が正しい。
 */
public final class PhpStanAliasDeclarations {
    private PhpStanAliasDeclarations() {
    }

    public record Declaration(SourceRange nameRange, PhpStanAliasSymbol symbol) {
    }

    public static Optional<Declaration> at(PsiFile file, int offset) {
        if (file == null || !file.isValid()) {
            return Optional.empty();
        }
        PhpFileModel fileModel = PhpFileModelCache.of(file);
        Optional<PhpFileModel.ClassLikeDocBlock> block = fileModel.findDocBlockContainingOffset(offset);
        if (block.isEmpty()) {
            return Optional.empty();
        }
        for (PhpStanTypeAliasDefinition alias : block.get().aliases()) {
            if (alias.aliasNameRange().containsExclusiveEnd(offset)) {
                return Optional.of(declaration(file, offset, alias.aliasNameRange(),
                        block.get().fqcn(), alias.aliasName()));
            }
        }
        for (PhpStanTypeImport typeImport : block.get().imports()) {
            if (typeImport.localNameRange().containsExclusiveEnd(offset)) {
                return Optional.of(declaration(file, offset, typeImport.localNameRange(),
                        block.get().fqcn(), typeImport.localName()));
            }
        }
        return Optional.empty();
    }

    /**
     * Symbol の同一性は解決後の定義元に寄せる。import の別名で検索しても、元の定義とその
     * すべての利用箇所が一つの一覧に出るようにするため。解決できないときは書かれたとおりに扱う。
     */
    private static Declaration declaration(
            PsiFile file,
            int offset,
            SourceRange nameRange,
            String fallbackClassFqcn,
            String fallbackName
    ) {
        PhpStanAliasSymbol symbol = new PhpStanTypeResolverFacade().resolveAt(file, offset)
                .map(target -> new PhpStanAliasSymbol(
                        target.resolvedType().definingClassFqcn() == null
                                ? fallbackClassFqcn : target.resolvedType().definingClassFqcn(),
                        target.resolvedType().originalName()))
                .orElseGet(() -> new PhpStanAliasSymbol(fallbackClassFqcn, fallbackName));
        return new Declaration(nameRange, symbol);
    }
}
