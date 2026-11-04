package com.cnethuka.kegare.item;

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

/**
 * Rerolls the user's technique ONLY (never ce or traits). Blocked for Severed players.
 * Consumed on success (not in creative).
 */
public class PurificationTalismanItem extends Item {
    public PurificationTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The server decides; the client just plays the swing.
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);

        RollService.RerollResult result = RollService.reroll(serverPlayer, RollReason.TALISMAN);
        if (result != RollService.RerollResult.SUCCESS) return InteractionResultHolder.fail(stack);

        stack.consume(1, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
