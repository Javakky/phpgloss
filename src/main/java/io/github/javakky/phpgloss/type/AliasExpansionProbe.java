package io.github.javakky.phpgloss.type;

import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

/** テストから、プロパティの型の組み立てだけを測るための入口。 */
public final class AliasExpansionProbe {
    private AliasExpansionProbe() {
    }

    public static @Nullable PhpType fieldType(Field field) {
        return AliasExpansion.fieldTypeOf(field);
    }
}
