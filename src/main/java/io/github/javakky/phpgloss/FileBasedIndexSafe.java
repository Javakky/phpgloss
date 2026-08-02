package io.github.javakky.phpgloss;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import io.github.javakky.phpgloss.index.PhpStanTypeAliasIndex;

import java.util.Collection;
import java.util.Optional;

public final class FileBasedIndexSafe {
    private FileBasedIndexSafe() {
    }

    public static Optional<VirtualFile> findFile(Project project, String fqcn, String aliasName) {
        if (project == null || DumbService.isDumb(project) || fqcn == null || aliasName == null) {
            return Optional.empty();
        }
        String key = PhpStanTypeAliasIndex.key(fqcn, aliasName);
        Collection<VirtualFile> files = FileBasedIndex.getInstance()
                .getContainingFiles(PhpStanTypeAliasIndex.NAME, key, GlobalSearchScope.projectScope(project));
        return files.stream().findFirst();
    }
}

