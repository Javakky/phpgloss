package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;
import com.jetbrains.php.lang.psi.elements.ArrayIndex;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 添字アクセスの型を、対象の型引数から取る。
 *
 * <p>`list<array{id: int}>` の `[0]` は array shape のはず。PhpStorm は要素を複数形 (`X[]`) を
 * 経由して取るので、shape はその形に載らず `array` まで落ちる (実測: `mixed|array`)。
 * 型引数から直接取れば形が残る。
 *
 * <p>添字が書かれた文字列で、対象が array shape なら、そのキーの型を返す。
 *
 * <p>shape の union は、囲む条件がタグを決めているならその枝だけに絞る ({@link TaggedUnion})。
 */
public final class PhpStanArrayAccessTypeProvider implements PhpTypeProvider4 {
    @Override
    public char getKey() {
        return 'ᨲ';
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof ArrayAccessExpression access)
                || DumbService.isDumb(element.getProject())) {
            // 対象の型を読むので、索引の構築中には答えられない
            return null;
        }
        if (!(access.getValue() instanceof PhpTypedElement target)) {
            return null;
        }
        String key = writtenKeyOf(access.getIndex());
        PhpType result = new PhpType();
        java.util.List<String> members = TaggedUnion.membersFor(access,
                target.getType().global(element.getProject()).getTypesWithParametrisedParts());
        for (String member : members) {
            String type = key == null ? elementTypeOf(member) : shapeValueOf(member, key);
            if (type != null) {
                result.add(type);
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public @Nullable PhpType complete(String expression, Project project) {
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }

    /** `['label']` のように書かれた文字列のキー。書かれていない、または式なら null。 */
    private @Nullable String writtenKeyOf(@Nullable ArrayIndex index) {
        if (index == null || !(index.getValue() instanceof StringLiteralExpression literal)) {
            return null;
        }
        String contents = literal.getContents();
        return contents.isEmpty() ? null : contents;
    }

    /** `list<X>` / `array<K, V>` / `X[]` の要素型。 */
    private @Nullable String elementTypeOf(String type) {
        TypeUnionSimplifier.ArrayShape shape = TypeUnionSimplifier.arrayShapeOf(type);
        if (shape == null) {
            return null;
        }
        String element = shape.element().trim();
        return element.isEmpty() || element.equals("mixed") ? null : element;
    }

    /**
     * `array{label: non-empty-string}` から `label` の型を取る。
     *
     * <p>キーは `?` 付きで書けるので (`label?: string`)、そこも見る。
     */
    private @Nullable String shapeValueOf(String type, String key) {
        String trimmed = type.trim();
        int brace = trimmed.indexOf('{');
        if (!trimmed.startsWith("array{") || brace < 0 || !trimmed.endsWith("}")) {
            return null;
        }
        String body = trimmed.substring(brace + 1, trimmed.length() - 1);
        for (String entry : splitEntries(body)) {
            int colon = entry.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = entry.substring(0, colon).trim();
            if (name.endsWith("?")) {
                name = name.substring(0, name.length() - 1).trim();
            }
            name = name.replaceAll("^['\"]|['\"]$", "");
            if (name.equals(key)) {
                return entry.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** 最上位の `,` で shape の項を分ける。入れ子の中の `,` は区切りにしない。 */
    private java.util.List<String> splitEntries(String body) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                entries.add(body.substring(start, i));
                start = i + 1;
            }
        }
        entries.add(body.substring(start));
        return entries;
    }
}
