package com.cnethuka.kegare.item;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.TalismanPromptPayload;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rerolls the user's innate technique ONLY (never Malice or traits), in two steps:
 * <ol>
 *   <li>Use: the server checks Severed/combat and asks the client to show a confirmation. Nothing is consumed.</li>
 *   <li>{@link #confirm}: the client's confirm request. The server re-checks that the talisman is still in that
 *       hand, combat and Severed status, rolls, consumes one talisman, and sends the reveal.</li>
 * </ol>
 */
public class PurificationTalismanItem extends Item {
    public PurificationTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);
        if (!canPurify(serverPlayer)) return InteractionResultHolder.fail(stack);
        PacketDistributor.sendToPlayer(serverPlayer, new TalismanPromptPayload(hand,
                InnateTechniqueService.current(serverPlayer).map(InnateTechnique::id),
                RollService.get(serverPlayer).rollsSinceLegendary(), RollService.settings().hardPity()));
        return InteractionResultHolder.success(stack);
    }

    /** Server side of the confirm button. Every check is redone here; the client decides nothing. */
    public static void confirm(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(KegareItems.PURIFICATION_TALISMAN.get())) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.no_talisman"));
            return;
        }
        if (!canPurify(player)) return;
        if (RollService.reroll(player, RollReason.TALISMAN) != RollService.RerollResult.SUCCESS) return;
        stack.consume(1, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static boolean canPurify(ServerPlayer player) {
        if (MaliceService.isSevered(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.severed"));
            return false;
        }
        if (CombatHelper.isInCombat(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.in_combat"));
            return false;
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
