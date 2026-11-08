package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.innate_technique.swap.SwapIds;
import com.cnethuka.kegare.network.VisualPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A loop sent to a viewer who missed its start says how long it has been running (DESIGN §3.5). */
class VisualPayloadTest {
    @Test
    void aLoopCarriesItsElapsedTime() {
        VisualPayload late = new VisualPayload(VisualPayload.Action.START_LOOP, 7, SwapIds.SWITCHBACK_TIMER_KEY + 3,
                SwapIds.SWITCHBACK_TIMER, Vec3.ZERO, 60, 0, 25);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        VisualPayload.STREAM_CODEC.encode(buf, late);
        VisualPayload read = VisualPayload.STREAM_CODEC.decode(buf);
        assertEquals(late, read);
        assertEquals(60, read.growTicks(), "the whole delay, not what's left");
        assertEquals(25, read.elapsedTicks());
    }

    @Test
    void aFreshStartHasNoneElapsed() {
        VisualPayload fresh = new VisualPayload(VisualPayload.Action.START_LOOP, 7, "k", SwapIds.SWITCHBACK_TIMER, Vec3.ZERO, 60);
        assertEquals(0, fresh.elapsedTicks());
    }
}
