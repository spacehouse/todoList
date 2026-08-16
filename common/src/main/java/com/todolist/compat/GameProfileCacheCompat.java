package com.todolist.compat;

import com.mojang.authlib.GameProfile;
import com.todolist.TodoConstants;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * GameProfileCache 兼容工具，统一处理不同 Minecraft 小版本的异步查找签名差异。
 */
public final class GameProfileCacheCompat {
    private GameProfileCacheCompat() {
    }

    /**
     * 兼容调用玩家档案缓存的异步名称查询。
     *
     * @param server 当前服务端实例
     * @param playerName 目标玩家名称
     * @param callback 查询完成后的回调
     */
    public static void getAsync(MinecraftServer server, String playerName, Consumer<Optional<GameProfile>> callback) {
        Object profileCache = server.getProfileCache();
        if (profileCache == null) {
            callback.accept(Optional.empty());
            return;
        }

        try {
            Method callbackMethod = profileCache.getClass().getMethod("getAsync", String.class, Consumer.class);
            callbackMethod.invoke(profileCache, playerName, callback);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the future-based signature.
        } catch (ReflectiveOperationException e) {
            TodoConstants.LOGGER.warn("Failed to invoke callback-based GameProfileCache#getAsync", e);
            callback.accept(Optional.empty());
            return;
        }

        try {
            Method futureMethod = profileCache.getClass().getMethod("getAsync", String.class);
            Object futureResult = futureMethod.invoke(profileCache, playerName);
            if (futureResult instanceof CompletionStage<?> completionStage) {
                completionStage.whenComplete((result, error) -> {
                    if (error != null) {
                        TodoConstants.LOGGER.warn("Failed to resolve player profile asynchronously for {}", playerName, error);
                        callback.accept(Optional.empty());
                        return;
                    }
                    if (result instanceof Optional<?> optional && (optional.isEmpty() || optional.get() instanceof GameProfile)) {
                        @SuppressWarnings("unchecked")
                        Optional<GameProfile> gameProfileOptional = (Optional<GameProfile>) optional;
                        callback.accept(gameProfileOptional);
                        return;
                    }
                    callback.accept(Optional.empty());
                });
                return;
            }
        } catch (ReflectiveOperationException e) {
            TodoConstants.LOGGER.warn("Failed to invoke future-based GameProfileCache#getAsync", e);
        }

        callback.accept(Optional.empty());
    }
}
