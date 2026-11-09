package com.cnethuka.kegare.timing.client;

import com.cnethuka.kegare.client.ClientLoadout;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.TechniqueMode;
import com.cnethuka.kegare.fists.FistsIds;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.timing.TimedInput;
import com.cnethuka.kegare.timing.TimingNet;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The client's half of sub-tick timing (DESIGN §3.29): the inputs that need it are stamped with this client's monotonic clock (ms) the
 * moment the key or button goes down, and the stamp is sent there and then, ahead of the packet that does the thing at the next client
 * tick. Sent at the press, its trip to the server is only the network's, which is all the server allows a stamp to claim. How fine it is
 * depends on the frame rate: the game reads keys once a frame.
 */
public final class ClientStamps {
    private ClientStamps() {}

    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void init() {
        TimingNet.Ping.setClientClock(ClientStamps::nowMs);
        NeoForge.EVENT_BUS.addListener(ClientStamps::onKey);
        NeoForge.EVENT_BUS.addListener(ClientStamps::onMouse);
    }

    private static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.getKey(event.getKey(), event.getScanCode()));
    }

    private static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }

    private static void pressed(InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.screen != null) return;
        long now = nowMs();
        // An attack on whatever's under the crosshair now; if the button turns out to do something else, the stamp goes unused.
        if (matches(mc.options.keyAttack, key) && mc.hitResult instanceof EntityHitResult) send(TimedInput.ATTACK, now);
        if (matches(KegareKeys.PARRY, key)) send(TimedInput.PARRY, now);
        if (TechniqueMode.isActive()) {
            KeyMapping[] hotbar = mc.options.keyHotbarSlots;
            List<Ability> slotted = ClientLoadout.abilities();
            for (int i = 0; i < hotbar.length && i < slotted.size(); i++) {
                Ability ability = slotted.get(i);
                if (ability == null || !matches(hotbar[i], key)) continue;
                if (FistsIds.ABILITY.equals(ability.id())) send(TimedInput.FISTS_ON, now);
                send(TimedInput.CAST, now); // every cast key: the moment the intent is on its way
            }
            if (matches(KegareKeys.ULTIMATE, key)) send(TimedInput.CAST, now);
        }
    }

    private static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return !mapping.isUnbound() && mapping.getKey().equals(key);
    }

    private static void send(TimedInput input, long at) {
        PacketDistributor.sendToServer(new TimingNet.Stamp(input, at));
    }
}
