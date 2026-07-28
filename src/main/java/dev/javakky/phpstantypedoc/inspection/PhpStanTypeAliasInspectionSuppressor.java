package dev.javakky.phpstantypedoc.inspection;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.PhpDocUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocParamTag;
import com.jetbrains.php.lang.inspections.phpdoc.PhpDocSignatureInspection;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * PHPStan の array shape alias と native の {@code array} が一致する場合だけ、
 * {@link PhpDocSignatureInspection} の誤警告を抑止する。
 *
 * <p>組み込み inspection は {@code @param CreateUserData $payload} の生の型名を
 * {@code \Current\Namespace\CreateUserData} というクラス名として比較する。そのため、
 * alias の実体が array shape でも {@code array $payload} と不一致になる。
 *
 * <p>inspection 全体は無効化せず、既存 resolver で alias を解決でき、実体が array shape で、
 * さらに組み込みの型比較へ {@link PhpType#ARRAY} を渡した結果が一致する場合だけ抑止する。
 * nullable や union を含む比較規則は組み込み実装に委譲する。
 */
public final class PhpStanTypeAliasInspectionSuppressor implements InspectionSuppressor {
    private static final String INSPECTION_ID = "PhpDocSignatureInspection";

    private final PhpStanTypeResolverFacade facade = new PhpStanTypeResolverFacade();

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!INSPECTION_ID.equals(toolId)) {
            return false;
        }
        PhpDocParamTag tag = PsiTreeUtil.getParentOfType(element, PhpDocParamTag.class, false);
        if (tag == null) {
            return false;
        }
        PhpDocComment comment = PsiTreeUtil.getParentOfType(tag, PhpDocComment.class, false);
        Function function = comment != null && comment.getOwner() instanceof Function owner ? owner : null;
        Parameter parameter = findParameter(function, tag);
        PhpClass containingClass = PsiTreeUtil.getParentOfType(function, PhpClass.class, false);
        PhpDocType docType = PsiTreeUtil.findChildOfType(tag, PhpDocType.class);
        PsiFile file = tag.getContainingFile();
        if (parameter == null
                || containingClass == null
                || containingClass.getFQN() == null
                || docType == null
                || file == null) {
            return false;
        }

        Optional<PhpStanResolvedType> resolved =
                facade.resolveInClass(file, containingClass.getFQN(), docType.getText());
        return resolved.filter(type -> isArrayShape(type.typeBody()))
                .filter(type -> PhpDocSignatureInspection.typesMatch(parameter, tag, PhpType.ARRAY))
                .isPresent();
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(
            @NotNull PsiElement element,
            @NotNull String toolId
    ) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }

    private Parameter findParameter(Function function, PhpDocParamTag tag) {
        if (function == null) {
            return null;
        }
        String name = PhpDocUtil.getParamName(tag, function);
        if (name == null) {
            return null;
        }
        for (Parameter parameter : function.getParameters()) {
            if (name.equals(parameter.getName())) {
                return parameter;
            }
        }
        return null;
    }

    private boolean isArrayShape(String typeBody) {
        return typeBody != null && typeBody.stripLeading().startsWith("array{");
    }
}
