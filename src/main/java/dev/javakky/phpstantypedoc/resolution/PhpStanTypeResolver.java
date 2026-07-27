package dev.javakky.phpstantypedoc.resolution;

import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.model.PhpStanTypeAliasDefinition;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PhpStanTypeResolver {
    static final int MAX_DEPTH = 32;

    private final ClassNameResolver classNameResolver;

    public PhpStanTypeResolver(ClassNameResolver classNameResolver) {
        this.classNameResolver = classNameResolver;
    }

    public PhpStanResolvedType resolve(
            String requestedName,
            PhpFileModel fileModel,
            PhpFileModel.ClassLikeDocBlock currentClass,
            PhpStanTypeSourceProvider sourceProvider
    ) {
        return resolve(requestedName, requestedName, fileModel.namespace(), fileModel.uses(), currentClass, sourceProvider, new HashSet<>(), 0, false);
    }

    private PhpStanResolvedType resolve(
            String requestedName,
            String lookupName,
            String namespace,
            Map<String, String> uses,
            PhpFileModel.ClassLikeDocBlock currentClass,
            PhpStanTypeSourceProvider sourceProvider,
            Set<String> visited,
            int depth,
            boolean imported
    ) {
        if (currentClass == null || depth > MAX_DEPTH) {
            return PhpStanResolvedType.circular(requestedName, lookupName, currentClass == null ? null : currentClass.fqcn());
        }
        String visitKey = currentClass.fqcn() + "#" + lookupName;
        if (!visited.add(visitKey)) {
            return PhpStanResolvedType.circular(requestedName, lookupName, currentClass.fqcn());
        }

        Optional<PhpStanTypeAliasDefinition> local = currentClass.aliases().stream()
                .filter(alias -> alias.aliasName().equals(lookupName))
                .findFirst();
        if (local.isPresent()) {
            if (hasDirectLocalCycle(currentClass, lookupName, new HashSet<>())) {
                return PhpStanResolvedType.circular(requestedName, lookupName, currentClass.fqcn());
            }
            PhpStanTypeAliasDefinition alias = local.get();
            return new PhpStanResolvedType(
                    requestedName,
                    alias.aliasName(),
                    alias.typeExpression(),
                    currentClass.fqcn(),
                    alias.aliasNameRange(),
                    imported,
                    false,
                    false
            );
        }

        Optional<PhpStanTypeImport> matchedImport = currentClass.imports().stream()
                .filter(typeImport -> typeImport.localName().equals(lookupName))
                .findFirst();
        if (matchedImport.isEmpty()) {
            return PhpStanResolvedType.unresolved(requestedName);
        }

        PhpStanTypeImport typeImport = matchedImport.get();
        String sourceFqcn = classNameResolver.resolve(typeImport.sourceClassText(), currentClass.namespace(), currentClass.uses());
        if (sourceFqcn == null) {
            return PhpStanResolvedType.unresolved(requestedName);
        }
        Optional<PhpFileModel.ClassLikeDocBlock> sourceClass = sourceProvider.findClass(sourceFqcn, typeImport.originalName());
        if (sourceClass.isEmpty()) {
            return PhpStanResolvedType.unresolved(requestedName);
        }
        return resolve(requestedName, typeImport.originalName(), namespace, uses, sourceClass.get(), sourceProvider, visited, depth + 1, true);
    }

    private boolean hasDirectLocalCycle(
            PhpFileModel.ClassLikeDocBlock currentClass,
            String aliasName,
            Set<String> visitedAliases
    ) {
        if (!visitedAliases.add(aliasName)) {
            return true;
        }
        Optional<PhpStanTypeAliasDefinition> alias = currentClass.aliases().stream()
                .filter(candidate -> candidate.aliasName().equals(aliasName))
                .findFirst();
        if (alias.isEmpty()) {
            return false;
        }
        String referencedAlias = alias.get().typeExpression().trim();
        if (!referencedAlias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return false;
        }
        boolean isLocalAlias = currentClass.aliases().stream()
                .anyMatch(candidate -> candidate.aliasName().equals(referencedAlias));
        return isLocalAlias && hasDirectLocalCycle(currentClass, referencedAlias, visitedAliases);
    }
}
