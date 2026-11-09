package com.cnethuka.kegare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keybinds (rebindable in Controls; names in the lang file). */
public final class KegareKeys {
    public static final String CATEGORY = "key.categories.kegare";

    /** Hold with a number key for that slot's special; on its own, the selected ability's special. Technique Mode's number keys are vanilla's hotbar keys. */
    public static final KeyMapping SPECIAL = key("key.kegare.special", GLFW.GLFW_KEY_R);
    /** Toggles Technique Mode (the combat panel; number keys cast). */
    public static final KeyMapping TECHNIQUE_MODE = key("key.kegare.technique_mode", GLFW.GLFW_KEY_G);
    public static final KeyMapping ULTIMATE = key("key.kegare.ultimate", GLFW.GLFW_KEY_V);
    public static final KeyMapping SORCERER = key("key.kegare.sorcerer", GLFW.GLFW_KEY_K);
    /** Hold to parry, then guard (DESIGN §3.22). */
    public static final KeyMapping PARRY = key("key.kegare.parry", GLFW.GLFW_KEY_X);
    /** Six Eyes' map (DESIGN §3.32): does nothing without the eyes. */
    public static final KeyMapping SIX_EYES_MAP = key("key.kegare.six_eyes_map", GLFW.GLFW_KEY_M);
    /** Dash on a key of its own. Nothing by default: the default is double-tapping sprint. */
    public static final KeyMapping DASH = new KeyMapping("key.kegare.dash", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);

    private KegareKeys() {}

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SPECIAL);
        event.register(TECHNIQUE_MODE);
        event.register(ULTIMATE);
        event.register(SORCERER);
        event.register(PARRY);
        event.register(DASH);
        event.register(SIX_EYES_MAP);
    }
}
