package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 配列リテラルの型を、書かれたキーと要素から組み立てる。
 *
 * <p>PhpStorm はキーがクラス定数だと整数キーの配列と見る (`[Region::NORTH => …]` が
 * `array<int, string>[]`)。表示だけを直しても、要素アクセスはこの型を辿るので
 * `$table[Region::NORTH]` が誤ったままになる。型そのものに正しい形を足す。
 *
 * <p>足すだけで、PhpStorm 自身の型は消せない。並んだ union は表示のときに整理される。
 */
public final class PhpStanArrayLiteralTypeProvider implements PhpTypeProvider4 {
    @Override
    public char getKey() {
        return 'ᨰ';
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof ArrayCreationExpression literal)) {
            return null;
        }
        // 索引の構築中に他ファイルを解決してはいけない。索引が済んでいれば要素の型まで読める
        boolean indexing = com.intellij.openapi.project.DumbService.isDumb(element.getProject());
        String written = ArgumentText.literalArrayTypeOf(literal, element.getProject(), !indexing);
        if (written == null) {
            return null;
        }
        PhpType type = new PhpType();
        for (String part : PhpTypeText.splitTopLevelUnion(written)) {
            type.add(part);
        }
        return type.isEmpty() ? null : type;
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
