package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;
import com.jetbrains.php.lang.psi.elements.BinaryExpression;
import com.jetbrains.php.lang.psi.elements.If;
import com.jetbrains.php.lang.psi.elements.PhpMatchArm;
import com.jetbrains.php.lang.psi.elements.PhpMatchExpression;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * タグで分かれた shape の union を、囲む条件が決めている枝に絞る。
 *
 * <p>`array{kind: 'A', value: int}|array{kind: 'B', value: string}` を返す関数の値に対して、
 * `if ($item['kind'] === 'A')` の中や `match ($item['kind']) { 'A' => … }` の腕では、
 * 枝は一つに決まる。決まらなければ触らず、両方の可能性を残す。
 */
final class TaggedUnion {
    private TaggedUnion() {
    }

    /**
     * @param access この添字アクセス (`$item['value']`)
     * @param members 対象の型に並ぶ成分
     * @return 条件が枝を決めているならその成分だけ。決めていなければ元のまま
     */
    static List<String> membersFor(ArrayAccessExpression access, java.util.Collection<String> members) {
        List<String> all = List.copyOf(members);
        if (all.size() < 2) {
            return all;
        }
        String receiver = receiverNameOf(access);
        if (receiver == null) {
            return all;
        }
        Tag tag = tagFromContext(access, receiver);
        if (tag == null) {
            return all;
        }
        List<String> selected = all.stream()
                .filter(member -> holdsTag(member, tag))
                .toList();
        // どれも当てはまらないなら、読み違えている。元のまま返す
        return selected.isEmpty() ? all : selected;
    }

    /** `$item['value']` の `item`。受け手が変数でなければ null。 */
    private static @Nullable String receiverNameOf(ArrayAccessExpression access) {
        return access.getValue() instanceof Variable variable ? variable.getName() : null;
    }

    /** 囲む `if` / `match` が決めているタグ。 */
    private static @Nullable Tag tagFromContext(PsiElement element, String receiver) {
        for (PsiElement context = element; context != null; context = context.getParent()) {
            if (context instanceof If condition) {
                Tag tag = tagFromCondition(condition.getCondition(), receiver);
                if (tag != null && PsiTreeUtil.isAncestor(condition.getStatement(), element, false)) {
                    return tag;
                }
            }
            if (context instanceof PhpMatchArm arm) {
                Tag tag = tagFromArm(arm, receiver);
                if (tag != null) {
                    return tag;
                }
            }
        }
        return null;
    }

    /** `$item['kind'] === 'A'` の形から、キーと値を取る。 */
    private static @Nullable Tag tagFromCondition(@Nullable PsiElement condition, String receiver) {
        if (!(condition instanceof BinaryExpression comparison)
                || comparison.getOperationType() == null) {
            return null;
        }
        // 演算子の綴りは版で変わりうる。等値を表す形を広めに見る
        String operator = comparison.getOperationType().toString().toLowerCase();
        boolean equality = operator.contains("==")
                || operator.contains("identical")
                || operator.contains("identity")
                || operator.contains("equal");
        if (!equality || operator.contains("!")) {
            return null;
        }
        String key = keyOfAccess(comparison.getLeftOperand(), receiver);
        String value = literalOf(comparison.getRightOperand());
        if (key == null || value == null) {
            key = keyOfAccess(comparison.getRightOperand(), receiver);
            value = literalOf(comparison.getLeftOperand());
        }
        return key == null || value == null ? null : new Tag(key, value);
    }

    /** `match ($item['kind']) { 'A' => … }` の腕から、キーと値を取る。 */
    private static @Nullable Tag tagFromArm(PhpMatchArm arm, String receiver) {
        PhpMatchExpression match = PsiTreeUtil.getParentOfType(arm, PhpMatchExpression.class);
        if (match == null) {
            return null;
        }
        String key = keyOfAccess(match.getArgument(), receiver);
        if (key == null) {
            return null;
        }
        for (PsiElement condition : arm.getConditions()) {
            String value = literalOf(condition);
            if (value != null) {
                return new Tag(key, value);
            }
        }
        return null;
    }

    /** `$item['kind']` から `kind`。受け手が違えば null。 */
    private static @Nullable String keyOfAccess(@Nullable PsiElement element, String receiver) {
        if (!(element instanceof ArrayAccessExpression access)
                || !(access.getValue() instanceof Variable variable)
                || !receiver.equals(variable.getName())
                || access.getIndex() == null
                || !(access.getIndex().getValue() instanceof StringLiteralExpression key)) {
            return null;
        }
        String contents = key.getContents();
        return contents.isEmpty() ? null : contents;
    }

    private static @Nullable String literalOf(@Nullable PsiElement element) {
        if (!(element instanceof StringLiteralExpression literal)) {
            return null;
        }
        String contents = literal.getContents();
        return contents.isEmpty() ? null : contents;
    }

    /** この shape が、そのタグの値を持つか。 */
    private static boolean holdsTag(String member, Tag tag) {
        String trimmed = member.trim();
        if (!trimmed.startsWith("array{")) {
            return false;
        }
        String written = valueOfKey(trimmed, tag.key());
        if (written == null) {
            return false;
        }
        String literal = written.trim().replaceAll("^['\"]|['\"]$", "");
        return literal.equals(tag.value());
    }

    /** shape から、そのキーに書かれた型 (`'A'` のような値そのものを含む) を取る。 */
    private static @Nullable String valueOfKey(String shape, String key) {
        int brace = shape.indexOf('{');
        if (brace < 0 || !shape.endsWith("}")) {
            return null;
        }
        String body = shape.substring(brace + 1, shape.length() - 1);
        int depth = 0;
        int start = 0;
        List<String> entries = new java.util.ArrayList<>();
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
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = entry.substring(0, colon).trim()
                    .replaceAll("\\?$", "").replaceAll("^['\"]|['\"]$", "");
            if (name.equals(key)) {
                return entry.substring(colon + 1);
            }
        }
        return null;
    }

    private record Tag(String key, String value) {
    }
}
