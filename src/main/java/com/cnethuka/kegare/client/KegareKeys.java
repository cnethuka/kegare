package com.cnethuka.kegare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keybinds (rebindable in Controls; names in the lang file). */
public final class KegareKeys {
    public static final String CATEGORY = "key.categories.kegare";

    public static final KeyMapping CAST = key("key.kegare.cast", GLFW.GLFW_KEY_R);
    public static final KeyMapping NEXT_ABILITY = key("key.kegare.next_ability", GLFW.GLFW_KEY_Z);
    public static final KeyMapping PREVIOUS_ABILITY = key("key.kegare.previous_ability", GLFW.GLFW_KEY_X);
    public static final KeyMapping ULTIMATE = key("key.kegare.ultimate", GLFW.GLFW_KEY_V);

    private KegareKeys() {}

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(CAST);
        event.register(NEXT_ABILITY);
        event.register(PREVIOUS_ABILITY);
        event.register(ULTIMATE);
    }
}
