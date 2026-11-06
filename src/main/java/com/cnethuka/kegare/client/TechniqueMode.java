package com.cnethuka.kegare.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.TechniqueSelect;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/**
 * Client-only Technique Mode state (key G). While it is on the combat panel takes the hotbar's place
 * ({@link CombatPanelLayer}; the hotbar becomes a column, {@link CombatHotbarLayer}), the control chosen by
 * {@link TechniqueSelect} picks abilities while the other stays on the hotbar, and R and V work (C works either way).
 * Right-click stays vanilla. Screens don't touch it: chat, the inventory or a command leave it as it was. Purely input and
 * display, so the server doesn't know about it: the mode costs nothing to turn on, so a client ignoring it gains nothing,
 * and the server still validates every request.
 */
public final class TechniqueMode {
    private static boolean active;
    private static long shownAtMs;

    private TechniqueMode() {}

    /** Which control picks abilities while the mode is on (client setting). */
    public static TechniqueSelect select() {
        return KegareClientConfig.TECHNIQUE_SELECT.get();
    }

    /** The one answer to "is it on?": input and the HUD both read this, so they can't disagree. */
    public static boolean isActive() {
        return active && abilitiesAvailable();
    }

    /** The combat panel is up. */
    public static boolean panelShown() {
        return isActive();
    }

    /** Ends the mode once abilities have gone away (death, Severed, no innate technique) rather than pausing it. */
    static void tick() {
        if (active && !abilitiesAvailable()) exit();
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
