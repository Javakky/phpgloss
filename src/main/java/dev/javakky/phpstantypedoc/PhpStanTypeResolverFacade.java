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
            Optional<PhpFileModel.ClassLikeDocBlock> block = findContainingClass(fileModel, containingClass);
            if (block.isEmpty()) {
                return Optional.empty();
            }
            if (referenceDoc == null) {
                return Optional.empty();
            }
            Optional<DetectedTypeReference> reference = referenceDetector.detect(
                    referenceDoc.getText(),
                    referenceDoc.getTextRange().getStartOffset(),
                    offset
            );
            if (reference.isEmpty()) {
                return Optional.empty();
            }
            ProjectPhpStanTypeSourceProvider sourceProvider = new ProjectPhpStanTypeSourceProvider(project, fileModel, virtualFile);
            PhpStanResolvedType resolved = resolver.resolve(reference.get().name(), fileModel, block.get(), sourceProvider);
            if (resolved.unresolved()) {
                return Optional.empty();
            }
            VirtualFile definitionFile = locateDefinitionFile(project, virtualFile, fileModel, resolved);
            return Optional.of(new ResolvedPhpStanTypeTarget(resolved, reference.get(), definitionFile));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (IndexNotReadyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warn("Unexpected PHPStan type documentation failure", e);
            return Optional.empty();
        }
    }

    private Optional<PhpFileModel.ClassLikeDocBlock> findContainingClass(
            PhpFileModel fileModel,
            PhpClass containingClass
    ) {
        if (containingClass == null || containingClass.getFQN() == null) {
            return Optional.empty();
        }
        String fqcn = containingClass.getFQN();
        if (fqcn.startsWith("\\")) {
            fqcn = fqcn.substring(1);
        }
        return fileModel.findClass(fqcn);
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
}
