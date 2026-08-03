package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * プロパティを読んだ型に、`@var` に書かれたエイリアスや shape を開いて入れる。
 *
 * <p>`@var list&lt;row&gt;` と書いたプロパティは、PhpStorm には `array` としか見えない。
 * メソッドの戻り値では開いていたが、プロパティは見ていなかった。昇格プロパティでは型が
 * コンストラクタの `@param` 側にあるので、そちらも読む。
 *
 * <p>その場で参照を引き、解けたときだけ型を返す。シグネチャを持ち回って {@code complete} で
 * 解こうとすると、絞り込みのあとに元のクラスを指してしまい解決できず、**PhpStorm 自身の型まで
 * 消える** (実測: `$node->data` が型なしになった)。索引の構築中は何も言わない。
 */
public final class PhpStanFieldAliasTypeProvider implements PhpTypeProvider4 {
    @Override
    public char getKey() {
        return 'ᨳ';
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof FieldReference reference)
                || DumbService.isDumb(element.getProject())) {
            return null;
        }
        try {
            PhpType found = fromNarrowedReceiver(reference);
            if (found != null) {
                return found;
            }
            for (var resolved : reference.multiResolve(false)) {
                if (resolved.getElement() instanceof Field field) {
                    PhpType expanded = AliasExpansion.fieldTypeOf(field);
                    if (expanded != null) {
                        return expanded;
                    }
                }
            }
        } catch (Throwable unavailable) {
            // 索引の構築中にも呼ばれ、そこで参照を引くと stub が組み立てられない。
            // `DumbService` では捕まえられない状態があるので、投げられたものは何であれ
            // 受けて型を出さずに諦める。索引が済めば通常どおり解ける
            return null;
        }
        return null;
    }

    /**
     * 受け手の型からフィールドを探す。
     *
     * <p>`instanceof` で絞ったあと、参照の解決は宣言時のクラスを指す (`Node.data`)。絞った先の
     * クラスにしかないプロパティは、そこからは見つからない。受け手の**いまの型**を見れば届く。
     */
    private @Nullable PhpType fromNarrowedReceiver(FieldReference reference) {
        String name = reference.getName();
        if (name == null || !(reference.getClassReference() instanceof PhpTypedElement receiver)) {
            return null;
        }
        PhpIndex index = PhpIndex.getInstance(reference.getProject());
        for (String type : receiver.getType().global(reference.getProject()).getTypes()) {
            String fqn = type.startsWith("#") ? null : type;
            if (fqn == null || fqn.isBlank()) {
                continue;
            }
            for (PhpClass owner : index.getAnyByFQN(fqn)) {
                for (Field field : owner.getFields()) {
                    if (!name.equals(field.getName())) {
                        continue;
                    }
                    PhpType expanded = AliasExpansion.fieldTypeOf(field);
                    if (expanded != null) {
                        return expanded;
                    }
                }
            }
        }
        return null;
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
}
