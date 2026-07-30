package dev.javakky.phpstantypedoc.usages;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.UsageHandler;
import com.intellij.find.usages.symbol.SearchTargetSymbol;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * PHPStan 型エイリアスを Symbol として表したもの。使用箇所検索の対象になる。
 *
 * <p>同一性は「定義元クラス + 元の名前」で決める。{@code @phpstan-import-type} の別名は
 * 元の定義と同じ Symbol になり、宣言のどこで検索しても同じ一覧が出る。別名ごとに Symbol を
 * 分けると、元の定義から検索したときに import 経由の使用箇所が落ちる。
 *
 * <p>{@link SearchTargetSymbol} を実装しているので {@code lang.symbolSearchTarget} への
 * 登録は要らない (platform が Symbol 自身から SearchTarget を取る)。
 */
public record PhpStanAliasSymbol(String definingClassFqcn, String aliasName)
        implements SearchTargetSymbol, SearchTarget {

    @Override
    public @NotNull Pointer<PhpStanAliasSymbol> createPointer() {
        // 名前だけを持つ値オブジェクトなので PSI の生存に依存しない
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull SearchTarget getSearchTarget() {
        return this;
    }

    @Override
    public @NotNull TargetPresentation presentation() {
        return TargetPresentation.builder(aliasName)
                .locationText(definingClassFqcn)
                .presentation();
    }

    @Override
    public @NotNull UsageHandler getUsageHandler() {
        return UsageHandler.createEmptyUsageHandler(aliasName);
    }
}
