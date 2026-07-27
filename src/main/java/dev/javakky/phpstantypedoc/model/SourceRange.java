package dev.javakky.phpstantypedoc.model;

public record SourceRange(int startOffset, int endOffset) {
    public boolean contains(int offset) {
        return startOffset <= offset && offset <= endOffset;
    }

    public boolean containsExclusiveEnd(int offset) {
        return startOffset <= offset && offset < endOffset;
    }

    public int length() {
        return Math.max(0, endOffset - startOffset);
    }
}

