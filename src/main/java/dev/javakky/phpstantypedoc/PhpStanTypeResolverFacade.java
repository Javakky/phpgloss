package dev.javakky.phpstantypedoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.model.PhpStanTypeAliasDefinition;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;
import dev.javakky.phpstantypedoc.model.SourceRange;
import dev.javakky.phpstantypedoc.parser.DetectedTypeReference;
import dev.javakky.phpstantypedoc.parser.PhpDocTypeReferenceDetector;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelParser;
import dev.javakky.phpstantypedoc.resolution.ClassNameResolver;
import dev.javakky.phpstantypedoc.resolution.PhpStanTypeResolver;

import java.util.Optional;

public final class PhpStanTypeResolverFacade {
    private static final Logger LOG = Logger.getInstance(PhpStanTypeResolverFacade.class);

    private final PhpFileModelParser fileParser = new PhpFileModelParser();
    private final PhpDocTypeReferenceDetector referenceDetector = new PhpDocTypeReferenceDetector();
    private final PhpStanTypeResolver resolver = new PhpStanTypeResolver(new ClassNameResolver());

    public Optional<ResolvedPhpStanTypeTarget> resolveAt(PsiFile psiFile, int offset) {
        try {
            ProgressManager.checkCanceled();
            if (psiFile == null || !psiFile.isValid()) {
                return Optional.empty();
            }
            Project project = psiFile.getProject();
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null) {
                return Optional.empty();
            }
            PhpFileModel fileModel = fileParser.parse(psiFile.getText());
            PsiElement offsetElement = psiFile.findElementAt(offset);
            PhpDocComment referenceDoc = PsiTreeUtil.getParentOfType(offsetElement, PhpDocComment.class, false);
            PhpClass containingClass = PsiTreeUtil.getParentOfType(referenceDoc, PhpClass.class, false);
            // クラス自身の PHPDoc は PhpClass の子ではなく前の兄弟なので、PSI から所属クラスを
            // 辿れない。その場合はパース済みモデルの doc 範囲から所属クラスを引く。
            Optional<PhpFileModel.ClassLikeDocBlock> block = findContainingClass(fileModel, containingClass)
                    .or(() -> fileModel.findDocBlockContainingOffset(offset));
            if (block.isEmpty()) {
                return Optional.empty();
            }
            if (referenceDoc == null) {
                return Optional.empty();
            }
            Optional<TypeNameLookup> lookup = referenceDetector.detect(
                    referenceDoc.getText(),
                    referenceDoc.getTextRange().getStartOffset(),
                    offset
            ).map(TypeNameLookup::ofReference);
            if (lookup.isEmpty()) {
                lookup = detectDeclarationName(block.get(), offset);
            }
            if (lookup.isEmpty()) {
                return Optional.empty();
            }
            ProjectPhpStanTypeSourceProvider sourceProvider = new ProjectPhpStanTypeSourceProvider(project, fileModel, virtualFile);
            DetectedTypeReference reference = lookup.get().reference();
            PhpStanResolvedType resolved = resolver.resolve(reference.name(), lookup.get().lookupName(), fileModel, block.get(), sourceProvider);
            if (resolved.unresolved()) {
                return Optional.empty();
            }
            VirtualFile definitionFile = locateDefinitionFile(project, virtualFile, fileModel, resolved);
            return Optional.of(new ResolvedPhpStanTypeTarget(resolved, reference, definitionFile));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (IndexNotReadyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warn("Unexpected PHPStan type documentation failure", e);
            return Optional.empty();
        }
    }

    /**
     * 型名と所属クラスが判明している場合の解決。キャレット位置を持たない呼び出し元
     * (array shape の提供) から使う。
     */
    public Optional<PhpStanResolvedType> resolveInClass(PsiFile psiFile, String classFqcn, String typeName) {
        try {
            ProgressManager.checkCanceled();
            if (psiFile == null || !psiFile.isValid() || classFqcn == null || typeName == null) {
                return Optional.empty();
            }
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null) {
                return Optional.empty();
            }
            PhpFileModel fileModel = fileParser.parse(psiFile.getText());
            Optional<PhpFileModel.ClassLikeDocBlock> block = fileModel.findClass(stripLeadingSeparator(classFqcn));
            if (block.isEmpty()) {
                return Optional.empty();
            }
            ProjectPhpStanTypeSourceProvider sourceProvider =
                    new ProjectPhpStanTypeSourceProvider(psiFile.getProject(), fileModel, virtualFile);
            PhpStanResolvedType resolved = resolver.resolve(typeName, fileModel, block.get(), sourceProvider);
            return resolved.unresolved() ? Optional.empty() : Optional.of(resolved);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (IndexNotReadyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warn("Unexpected PHPStan type resolution failure", e);
            return Optional.empty();
        }
    }

    /**
     * エイリアスの宣言行そのものにキャレットがある場合を拾う。
     * 型参照の検出 ({@link PhpDocTypeReferenceDetector}) は {@code @param} 等のタグを見るので、
     * 宣言行はパース済みモデルが持つ名前の範囲と突き合わせる。
     */
    private Optional<TypeNameLookup> detectDeclarationName(PhpFileModel.ClassLikeDocBlock block, int offset) {
        for (PhpStanTypeAliasDefinition alias : block.aliases()) {
            if (alias.aliasNameRange().containsExclusiveEnd(offset)) {
                return Optional.of(declarationLookup(alias.aliasName(), alias.aliasNameRange(), alias.aliasName()));
            }
        }
        for (PhpStanTypeImport typeImport : block.imports()) {
            if (typeImport.localNameRange().containsExclusiveEnd(offset)) {
                return Optional.of(declarationLookup(typeImport.localName(), typeImport.localNameRange(), typeImport.localName()));
            }
            if (typeImport.originalNameRange().containsExclusiveEnd(offset)) {
                return Optional.of(declarationLookup(typeImport.originalName(), typeImport.originalNameRange(), typeImport.localName()));
            }
        }
        return Optional.empty();
    }

    private TypeNameLookup declarationLookup(String displayName, SourceRange nameRange, String lookupName) {
        return new TypeNameLookup(new DetectedTypeReference(displayName, nameRange), lookupName);
    }

    private Optional<PhpFileModel.ClassLikeDocBlock> findContainingClass(
            PhpFileModel fileModel,
            PhpClass containingClass
    ) {
        if (containingClass == null || containingClass.getFQN() == null) {
            return Optional.empty();
        }
        return fileModel.findClass(stripLeadingSeparator(containingClass.getFQN()));
    }

    private static String stripLeadingSeparator(String fqcn) {
        return fqcn.startsWith("\\") ? fqcn.substring(1) : fqcn;
    }

    private VirtualFile locateDefinitionFile(Project project, VirtualFile currentFile, PhpFileModel currentFileModel, PhpStanResolvedType resolved) {
        if (resolved.definingClassFqcn() == null) {
            return currentFile;
        }
        if (currentFileModel.findClass(resolved.definingClassFqcn()).isPresent()) {
            return currentFile;
        }
        return FileBasedIndexSafe.findFile(project, resolved.definingClassFqcn(), resolved.originalName()).orElse(currentFile);
    }

    private record TypeNameLookup(DetectedTypeReference reference, String lookupName) {
        static TypeNameLookup ofReference(DetectedTypeReference reference) {
            return new TypeNameLookup(reference, reference.name());
        }
    }
}
