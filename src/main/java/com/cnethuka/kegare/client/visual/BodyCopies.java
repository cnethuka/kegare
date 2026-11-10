package com.cnethuka.kegare.client.visual;

import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Hides name tags while a body is drawn again where it isn't (an afterimage, a Severed player's sensed outline): a copy
 * has no name, and a name drawn into one shows as a grey box. Wrap the drawing in {@link #draw}.
 */
public final class BodyCopies {
    private static int drawing;

    private BodyCopies() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderNameTagEvent e) -> {
            if (drawing > 0) e.setCanRender(TriState.FALSE);
        });
    }

    public static void draw(Runnable draw) {
        drawing++;
        try {
            draw.run();
        } finally {
            drawing--;
        }
    }
}
