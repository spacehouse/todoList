package com.todolist.mixin;

import com.todolist.fabric.AdvancementAwardGate;
import com.todolist.trigger.TaskTriggerService;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进度授奖注入。
 * Fabric API 未提供公开的进度事件，这里在 {@link PlayerAdvancements#award} 返回后判定
 * 该进度是否已真正完成，完成时把进度 ID 转发给通用触发引擎。
 *
 * 注意：本类只能包含 private 成员（含 private static 方法），
 * 去重状态与清理入口放在 {@link AdvancementAwardGate}，
 * 否则 Mixin 会在应用阶段抛出 {@code InvalidMixinException} 导致目标类加载失败。
 */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    /** 该进度集合所属的玩家。 */
    @Shadow
    private ServerPlayer player;

    /**
     * 进度达成后转发 ADVANCEMENT 触发事件。
     *
     * @param advancement  本次授奖的进度
     * @param criterionKey 本次达成的条件键
     * @param callbackInfo 原方法返回值回调
     */
    @Inject(method = "award", at = @At("RETURN"))
    private void todolist$onAdvancementAwarded(Advancement advancement, String criterionKey,
                                               CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!Boolean.TRUE.equals(callbackInfo.getReturnValue()) || advancement == null || player == null) {
            return;
        }
        // 隐藏进度没有展示信息，不作为可选目标
        if (advancement.getDisplay() == null) {
            return;
        }
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        if (!AdvancementAwardGate.shouldReport(player.getUUID(), advancement.getId().toString())) {
            return;
        }
        TaskTriggerService.handleAdvancementAwarded(player, advancement.getId().toString());
    }
}
