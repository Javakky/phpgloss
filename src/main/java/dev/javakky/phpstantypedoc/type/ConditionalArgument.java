package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.function.Supplier;

/**
 * 条件型の判定に使う実引数一つ分。
 *
 * <p>判定はまず書かれたテキストで行い、決まらないときだけ型を見る。型の復元は索引の完成を
 * 伴うので、条件の段ごとに必ず引くと重い。要るときまで遅らせる。
 *
 * @param text 呼び出しに書かれたテキスト。クラス定数は完全修飾済み
 * @param type 実引数の型
 */
record ConditionalArgument(String text, Supplier<PhpType> type) {

    /**
     * 型を持たない実引数。テキストだけで決まる条件に使う。
     */
    static ConditionalArgument ofText(String text) {
        return new ConditionalArgument(text, () -> PhpType.EMPTY);
    }
}
