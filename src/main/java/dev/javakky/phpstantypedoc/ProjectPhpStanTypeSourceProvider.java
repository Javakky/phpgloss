package dev.javakky.phpstantypedoc;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import dev.javakky.phpstantypedoc.index.PhpStanTypeAliasIndex;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;
import dev.javakky.phpstantypedoc.resolution.PhpStanTypeSourceProvider;

import java.util.Collection;
import java.util.Optional;

final class ProjectPhpStanTypeSourceProvider implements PhpStanTypeSourceProvider {
    private final Project project;
    private final PhpFileModel currentFileModel;
    private final VirtualFile currentVirtualFile;

    ProjectPhpStanTypeSourceProvider(Project project, PhpFileModel currentFileModel, VirtualFile currentVirtualFile) {
        this.project = project;
        this.currentFileModel = currentFileModel;
        this.currentVirtualFile = currentVirtualFile;
    }

    @Override
    public Optional<PhpFileModel.ClassLikeDocBlock> findClass(String fqcn, String aliasName) {
        Optional<PhpFileModel.ClassLikeDocBlock> local = currentFileModel.findClass(fqcn);
        if (local.isPresent()) {
            return local;
        }
        if (DumbService.isDumb(project)) {
            return Optional.empty();
        }
        String key = PhpStanTypeAliasIndex.key(fqcn, aliasName);
        Collection<VirtualFile> files = FileBasedIndex.getInstance()
                .getContainingFiles(PhpStanTypeAliasIndex.NAME, key, GlobalSearchScope.projectScope(project));
        for (VirtualFile file : files) {
            if (file.equals(currentVirtualFile)) {
                continue;
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null || !psiFile.isValid()) {
                continue;
            }
            Optional<PhpFileModel.ClassLikeDocBlock> block = PhpFileModelCache.of(psiFile).findClass(fqcn);
            if (block.isPresent()) {
                return block;
            }
        }
        return Optional.empty();
    }
}

