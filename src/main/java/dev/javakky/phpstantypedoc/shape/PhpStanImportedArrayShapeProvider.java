package dev.javakky.phpstantypedoc.shape;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.shapes.PhpArrayShapesProvider;
import com.jetbrains.php.lang.psi.resolve.types.shapes.PhpDocArrayShapeProvider;
import com.jetbrains.php.lang.psi.stubs.indexes.expectedArguments.PhpShapeEntriesIndex.PhpShapeEntry;
import dev.javakky.phpstantypedoc.FileBasedIndexSafe;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;

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

    private static final List<String> PARAM_TAGS = List.of("@param", "@phpstan-param");
    private static final List<String> RETURN_TAGS = List.of("@return", "@phpstan-return");
    private static final List<String> VAR_TAGS = List.of("@var", "@phpstan-var");

    private final PhpStanTypeResolverFacade facade = new PhpStanTypeResolverFacade();

    @Override
    protected List<PhpShapeEntry> getParameterValuesInner(Function function, int index) {
        Parameter[] parameters = function.getParameters();
        if (index < 0 || index >= parameters.length) {
            return List.of();
        }
        return orImported(super.getParameterValuesInner(function, index),
                function, PARAM_TAGS, parameters[index].getName());
    }

    @Override
    protected List<PhpShapeEntry> getValuesInner(Function function) {
        return orImported(super.getValuesInner(function), function, RETURN_TAGS, null);
    }

    @Override
    public List<PhpShapeEntry> getValues(Field field) {
        return orImported(super.getValues(field), field, VAR_TAGS, null);
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
            String typeName = declaredTypeName(owner.getDocComment(), tagNames, parameterName);
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
            PsiFile definitionFile = locateDefinitionFile(currentFile, resolved.get());
            if (definitionFile == null) {
                return List.of();
            }
            PhpDocTag aliasTag = findAliasTag(definitionFile, resolved.get());
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
     * 定義元ファイルを返す。同一ファイル内の定義なら PHP プラグインが解決できるので null を返す。
     */
    private PsiFile locateDefinitionFile(PsiFile currentFile, PhpStanResolvedType resolved) {
        Project project = currentFile.getProject();
        Optional<VirtualFile> found = FileBasedIndexSafe.findFile(
                project, resolved.definingClassFqcn(), resolved.originalName());
        if (found.isEmpty() || found.get().equals(currentFile.getOriginalFile().getVirtualFile())) {
            return null;
        }
        return PsiManager.getInstance(project).findFile(found.get());
    }

    private PhpDocTag findAliasTag(PsiFile definitionFile, PhpStanResolvedType resolved) {
        for (PhpClass phpClass : PsiTreeUtil.findChildrenOfType(definitionFile, PhpClass.class)) {
            if (!sameFqcn(phpClass.getFQN(), resolved.definingClassFqcn())) {
                continue;
            }
            PhpDocComment doc = phpClass.getDocComment();
            if (doc == null) {
                continue;
            }
            for (PhpDocTag tag : doc.getTagElementsByName("@phpstan-type")) {
                if (declaresAlias(tag, resolved.originalName())) {
                    return tag;
                }
            }
        }
        return null;
    }

    /**
     * `@phpstan-type UserData array{...}` の名前部分が {@code aliasName} かを見る。
     * {@code getTagValue()} は空文字列を返すのでタグのテキストを直接分解する。
     */
    private boolean declaresAlias(PhpDocTag tag, String aliasName) {
        String[] parts = tag.getText().trim().split("\\s+", 3);
        return parts.length >= 2 && parts[1].equals(aliasName);
    }

    /**
     * PHPDoc タグに書かれた生の型名を返す。{@code PhpDocTag#getTagValue()} は
     * `@param CreateUserData $payload` に対して空文字列を返すので、PHP のパーサが型と認識した
     * {@link PhpDocType} のテキストを読む。{@code getType()} は解決済みの型を返しエイリアス名が
     * 失われるため使えない。
     */
    private String declaredTypeName(PhpDocComment doc, List<String> tagNames, String parameterName) {
        if (doc == null) {
            return null;
        }
        for (String tagName : tagNames) {
            for (PhpDocTag tag : doc.getTagElementsByName(tagName)) {
                String typeName = typeNameFromTag(tag, parameterName);
                if (typeName != null) {
                    return typeName;
                }
            }
        }
        return null;
    }

    private String typeNameFromTag(PhpDocTag tag, String parameterName) {
        if (parameterName != null && !declaresVariable(tag, parameterName)) {
            return null;
        }
        PhpDocType docType = PsiTreeUtil.findChildOfType(tag, PhpDocType.class);
        if (docType == null) {
            return null;
        }
        String typeName = docType.getText();
        return typeName == null || typeName.isBlank() ? null : typeName;
    }

    private boolean declaresVariable(PhpDocTag tag, String parameterName) {
        String expected = "$" + parameterName;
        for (String part : tag.getText().trim().split("\\s+")) {
            if (part.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameFqcn(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return strip(left).equals(strip(right));
    }

    private String strip(String fqcn) {
        return fqcn.startsWith("\\") ? fqcn.substring(1) : fqcn;
    }
}
