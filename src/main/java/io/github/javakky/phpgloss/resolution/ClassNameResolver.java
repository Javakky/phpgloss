package io.github.javakky.phpgloss.resolution;

import java.util.Map;

public final class ClassNameResolver {
    public String resolve(String sourceClassText, String currentNamespace, Map<String, String> uses) {
        if (sourceClassText == null || sourceClassText.isBlank()) {
            return null;
        }
        String text = sourceClassText.trim();
        if (text.startsWith("\\")) {
            return text.substring(1);
        }
        int slash = text.indexOf('\\');
        String firstSegment = slash < 0 ? text : text.substring(0, slash);
        String useTarget = uses.get(firstSegment);
        if (useTarget != null) {
            return slash < 0 ? useTarget : useTarget + text.substring(slash);
        }
        if (currentNamespace == null || currentNamespace.isBlank()) {
            return text;
        }
        return currentNamespace + "\\" + text;
    }
}

