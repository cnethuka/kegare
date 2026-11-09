package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.registry.KegareItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Eyewear on the client (DESIGN §3.32): each item's renderer on the face, and Curios' own key moved out of Technique Mode's way. Curios opens
 * its screen on G by default, which is Technique Mode, the game's main control; while it sits on G it moves to J.
 */
public final class EyewearClient {
    private EyewearClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(EyewearClient::setup));
    }

    private static void setup() {
        CuriosRendererRegistry.register(KegareItems.BLINDFOLD.get(), () -> new EyewearRenderer("blindfold"));
        CuriosRendererRegistry.register(KegareItems.TINTED_GLASSES.get(), () -> new EyewearRenderer("tinted_glasses"));
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping key : mc.options.keyMappings) {
            if ("key.curios.open.desc".equals(key.getName()) && key.getKey().getValue() == GLFW.GLFW_KEY_G) {
                key.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
                KeyMapping.resetMapping();
                mc.options.save();
            }
        }
    }
}
