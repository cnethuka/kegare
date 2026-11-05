package com.cnethuka.kegare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keybinds (rebindable in Controls; names in the lang file). */
public final class KegareKeys {
    public static final String CATEGORY = "key.categories.kegare";

    /** Casts the selected ability's special (hold for CHARGED/CHANNELED specials). */
    public static final KeyMapping SPECIAL = key("key.kegare.special", GLFW.GLFW_KEY_R);
    /** Toggles Technique Mode (inputScheme = TOGGLE_MODE). */
    public static final KeyMapping TECHNIQUE_MODE = key("key.kegare.technique_mode", GLFW.GLFW_KEY_G);
    /** Casts the selected ability's base version (inputScheme = SPLIT only; in TOGGLE_MODE that is right-click). */
    public static final KeyMapping CAST = key("key.kegare.cast", GLFW.GLFW_KEY_C);
    /** Hold to open the radial ability menu. */
    public static final KeyMapping RADIAL = key("key.kegare.radial", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ULTIMATE = key("key.kegare.ultimate", GLFW.GLFW_KEY_V);
    public static final KeyMapping SORCERER = key("key.kegare.sorcerer", GLFW.GLFW_KEY_K);

    private KegareKeys() {}

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SPECIAL);
        event.register(TECHNIQUE_MODE);
        event.register(CAST);
        event.register(RADIAL);
        event.register(ULTIMATE);
        event.register(SORCERER);
    }
}
