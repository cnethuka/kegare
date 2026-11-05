package com.cnethuka.kegare.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.InputScheme;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/**
 * Client-only Technique Mode state (key G). While it is on the combat panel replaces the hotbar
 * ({@link CombatPanelLayer}); in {@link InputScheme#TOGGLE_MODE} scroll and number keys also pick abilities (the hotbar
 * never changes) and right-click base-casts instead of using the held item. In {@link InputScheme#SPLIT} G only shows
 * or hides the panel. Purely input and display: the server still validates every request.
 */
public final class TechniqueMode {
    private static boolean active;
    private static long shownAtMs;

    private TechniqueMode() {}

    public static InputScheme scheme() {
        return KegareClientConfig.INPUT_SCHEME.get();
    }

    /** On, and the scheme uses a mode at all. */
    public static boolean isActive() {
        return active && scheme() == InputScheme.TOGGLE_MODE;
    }

    /** The combat panel is up (either scheme). */
    public static boolean panelShown() {
        return active && abilitiesAvailable();
    }

    /** When the panel last came up (it slides in). */
    public static long shownAtMs() {
        return shownAtMs;
    }

    /** G: on when allowed, off when on. Does nothing for Severed players or without abilities. */
    static void toggle() {
        if (active) {
            exit();
        } else if (abilitiesAvailable()) {
            active = true;
            shownAtMs = net.minecraft.Util.getMillis();
        }
    }

    public static void exit() {
        active = false;
    }

    /** The local player has an innate technique with abilities, is alive and is not Severed. */
    public static boolean abilitiesAvailable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying() || mc.player.isSpectator()) return false;
        return current() != null && ClientLoadout.size() > 0;
    }

    @Nullable
    public static InnateTechnique current() {
        SyncPayload data = ClientData.get();
        if (data == null || !data.born() || data.severed()) return null;
        return data.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
    }
}
