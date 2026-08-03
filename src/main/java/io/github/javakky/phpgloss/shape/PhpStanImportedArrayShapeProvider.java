package io.github.javakky.phpgloss.shape;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.resolve.types.shapes.PhpArrayShapesProvider;
import com.jetbrains.php.lang.psi.resolve.types.shapes.PhpDocArrayShapeProvider;
import com.jetbrains.php.lang.psi.stubs.indexes.expectedArguments.PhpShapeEntriesIndex.PhpShapeEntry;
import io.github.javakky.phpgloss.PhpStanTypeResolverFacade;
import io.github.javakky.phpgloss.model.PhpStanResolvedType;
import io.github.javakky.phpgloss.parser.PhpDocDeclaredTypeReader;

import java.util.List;
import java.util.Optional;

/**
 * 別ファイルから `@phpstan-import-type` した array shape を PHP の shape 機構へ渡す。
 *
 * <p>PHP プラグインの {@link PhpDocArrayShapeProvider} は import を解決するが同一ファイル内に
 * 限られる。別ファイルの定義は解決できず shape が 0 件になるため、そこだけを補う。
 *
 * <p>{@link PhpDocArrayShapeProvider} を継承するのは、変数から宣言元を辿る経路
 * ({@code collectValuesFromAssignments} / {@code getValuesFromAssignedValue}) がこのクラスで
 * 実装されているため。キー補完はその経路を通るので、{@link PhpArrayShapesProvider} を直接
 * 継承すると型解決だけが効いて補完が出ない。
 *
 * <p>各経路で先に {@code super} を呼び、PHP が解決できた場合は空を返して譲る。
 * PHP 側の {@code PhpStanDocArrayShapeProvider} も同じ結果を返すため、二重に返すと重複する。
 *
 * <p>shape のパースは {@link PhpDocArrayShapeProvider#getArrayShapesFromDoc} に委譲する。
 * 定義元の `@phpstan-type` タグを見つけて渡すだけで、型表現は PHP の実装に揃う。
 */
public final class PhpStanImportedArrayShapeProvider extends PhpDocArrayShapeProvider {
    private static final Logger LOG = Logger.getInstance(PhpStanImportedArrayShapeProvider.class);

    private final PhpStanTypeResolverFacade facade = new PhpStanTypeResolverFacade();

    @Override
    protected List<PhpShapeEntry> getParameterValuesInner(Function function, int index) {
        Parameter[] parameters = function.getParameters();
        if (index < 0 || index >= parameters.length) {
            return List.of();
        }
        return orImported(super.getParameterValuesInner(function, index),
                function, PhpDocDeclaredTypeReader.PARAM_TAGS, parameters[index].getName());
    }

    @Override
    protected List<PhpShapeEntry> getValuesInner(Function function) {
        return orImported(super.getValuesInner(function), function, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
    }

    @Override
    public List<PhpShapeEntry> getValues(Field field) {
        return orImported(super.getValues(field), field, PhpDocDeclaredTypeReader.VAR_TAGS, null);
    }

    /**
     * PHP が解決できた場合は空を返して譲る。PHP 側の provider が同じ結果を返すため。
     */
    private List<PhpShapeEntry> orImported(
            List<PhpShapeEntry> fromPhp,
            PhpNamedElement owner,
            List<String> tagNames,
            String parameterName
    ) {
        if (fromPhp != null && !fromPhp.isEmpty()) {
            return List.of();
        }
        return importedShapes(owner, tagNames, parameterName);
    }

    private List<PhpShapeEntry> importedShapes(PhpNamedElement owner, List<String> tagNames, String parameterName) {
        try {
            String typeName = PhpDocDeclaredTypeReader.typeNameFor(owner.getDocComment(), tagNames, parameterName);
            if (typeName == null) {
                return List.of();
            }
            PhpClass containingClass = PsiTreeUtil.getParentOfType(owner, PhpClass.class, false);
            if (containingClass == null || containingClass.getFQN() == null) {
                return List.of();
            }
            PsiFile currentFile = owner.getContainingFile();
            Optional<PhpStanResolvedType> resolved =
                    facade.resolveInClass(currentFile, containingClass.getFQN(), typeName);
            if (resolved.isEmpty() || resolved.get().definingClassFqcn() == null) {
                return List.of();
            }
            PhpDocTag aliasTag = findAliasTagInAnotherFile(currentFile, resolved.get());
            return aliasTag == null ? List.of() : PhpDocArrayShapeProvider.getArrayShapesFromDoc(aliasTag);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (IndexNotReadyException e) {
            return List.of();
        } catch (RuntimeException e) {
            LOG.warn("Unexpected PHPStan imported array shape failure", e);
            return List.of();
        }
    }

    /**
     * 定義元クラスの `@phpstan-type` タグを返す。同一ファイル内の定義は PHP プラグインが
     * 解決できるので対象から外す。
     *
     * <p>クラスの取得は {@link PhpIndex#getAnyByFQN} を使う。定義元ファイルを引いてから
     * PSI を全走査すると、1 打鍵ごとに呼ばれるキー補完では間に合わない。
     */
    private PhpDocTag findAliasTagInAnotherFile(PsiFile currentFile, PhpStanResolvedType resolved) {
        VirtualFile currentVirtualFile = currentFile.getOriginalFile().getVirtualFile();
        String fqn = "\\" + stripLeadingSeparator(resolved.definingClassFqcn());
        for (PhpClass phpClass : PhpIndex.getInstance(currentFile.getProject()).getAnyByFQN(fqn)) {
            if (isInFile(phpClass, currentVirtualFile)) {
                continue;
            }
            PhpDocComment doc = phpClass.getDocComment();
            if (doc == null) {
                continue;
            }
            // 同じ宣言を `@psalm-type` と書く人もいる。索引はどちらでも拾うので、ここで
            // 片方しか見ないと、索引に載っているのに shape が出ないという食い違いになる
            for (String tagName : List.of("@phpstan-type", "@psalm-type")) {
                for (PhpDocTag tag : doc.getTagElementsByName(tagName)) {
                    if (declaresAlias(tag, resolved.originalName())) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInFile(PhpClass phpClass, VirtualFile file) {
        PsiFile containingFile = phpClass.getContainingFile();
        return containingFile != null && containingFile.getVirtualFile() != null
                && containingFile.getVirtualFile().equals(file);
    }

    /**
     * `@phpstan-type UserData array{...}` の名前部分が {@code aliasName} かを見る。
     * {@code getTagValue()} は空文字列を返すのでタグのテキストを直接分解する。
     */
    private boolean declaresAlias(PhpDocTag tag, String aliasName) {
        String[] parts = tag.getText().trim().split("\\s+", 3);
        return parts.length >= 2 && parts[1].equals(aliasName);
    }




    private String stripLeadingSeparator(String fqcn) {
        return fqcn.startsWith("\\") ? fqcn.substring(1) : fqcn;
    }
}
