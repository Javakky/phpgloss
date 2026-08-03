package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * プロパティを読んだ型に、`@var` に書かれたエイリアスを開いて入れる。
 *
 * <p>`@var list&lt;row&gt;` と書いたプロパティは、PhpStorm には `array` としか見えない。
 * メソッドの戻り値では開いていたが、プロパティは見ていなかった。
 *
 * <p>参照の解決は {@code complete} で行う。{@code getType} は索引の構築中にも呼ばれ、そこで
 * 参照を引くと stub が組み立てられなくなる (実測: `Failed to build stub tree`)。書かれた
 * シグネチャだけを持ち回り、索引の外で解く。
 */
public final class PhpStanFieldAliasTypeProvider implements PhpTypeProvider4 {
    private static final char KEY = 'ᨳ';

    @Override
    public char getKey() {
        return KEY;
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof FieldReference reference)) {
            return null;
        }
        String signature = reference.getSignature();
        if (signature.isEmpty()) {
            return null;
        }
        // シグネチャ自体が union のことがある (`#P#C…rows|#P#…().rows`)。まとめてキーを
        // 付けると、分解されたときに一方が読めない形になる
        PhpType keyed = new PhpType();
        for (String part : PhpTypeText.splitTopLevelUnion(signature)) {
            keyed.add("#" + KEY + part);
        }
        return keyed.isEmpty() ? null : keyed;
    }

    @Override
    public @Nullable PhpType complete(String expression, Project project) {
        if (expression == null || expression.length() < 2 || expression.charAt(1) != KEY) {
            return null;
        }
        String signature = expression.substring(2);
        for (PhpNamedElement element : PhpIndex.getInstance(project).getBySignature(signature)) {
            if (element instanceof Field field) {
                PhpType expanded = AliasExpansion.fieldTypeOf(field);
                if (expanded != null) {
                    return expanded;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }
}
