package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Time Manipulation (LEGENDARY, DESIGN §3.26): Point Maker, Accelerate and Decelerate, with Rewind, Fast Forward and Stasis Field on R;
 * Standstill as the ultimate. Rewind and Decelerate's cooldown slow fire {@link TimeTargetEvent} first. All numbers:
 * [time_manipulation] in kegare-server.toml.
 */
public final class TimeManipulation {
    public static final DeferredRegister<InnateTechnique> INNATE_TECHNIQUES = DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, Kegare.MODID);

    /** Clockwork bronze. */
    public static final int MALICE_COLOR = 0xB08D57;

    public static final PointMakerAbility POINT_MAKER = new PointMakerAbility();
    public static final AccelerateAbility ACCELERATE = new AccelerateAbility();
    public static final DecelerateAbility DECELERATE = new DecelerateAbility();

    // Specials (R), each sharing its base ability's cooldown.
    public static final RewindAbility REWIND = new RewindAbility();
    public static final FastForwardAbility FAST_FORWARD = new FastForwardAbility();
    public static final StasisFieldAbility STASIS_FIELD = new StasisFieldAbility();

    /** The ultimate (V). */
    public static final StandstillAbility STANDSTILL = new StandstillAbility();

    public static final DeferredHolder<InnateTechnique, InnateTechnique> TIME_MANIPULATION = INNATE_TECHNIQUES.register("time_manipulation",
            () -> new InnateTechnique(InnateTechnique.builder(InnateTechniqueRarity.LEGENDARY)
                    .ability(POINT_MAKER)
                    .ability(ACCELERATE)
                    .ability(DECELERATE)
                    .defaultUnlockLevels()
                    .ultimate(STANDSTILL)
                    .maliceColor(MALICE_COLOR)
                    .signatureAnimation(TimeIds.ANIM_STANDSTILL_CAST)
                    .tabIcon(Items.CLOCK)) {
                /** Rerolled (or reborn, or set by an admin) away from it: a mark still out is gone, with no cooldown. */
                @Override
                public void onRemoved(ServerPlayer player) {
                    if (!holds(player)) PointMarks.clear(player);
                }
            });

    private TimeManipulation() {}

    public static void register(IEventBus modBus) {
        INNATE_TECHNIQUES.register(modBus);
        TimeRegistry.register(modBus);
        TimeEvents.init();
    }

    /** Whether the player's CURRENT technique is Time Manipulation (what Standstill's immunity and the tab go by). */
    public static boolean holds(Player player) {
        return InnateTechniqueService.current(player).map(t -> t == TIME_MANIPULATION.get()).orElse(false);
    }

    /** A sound at an entity, heard by everyone near. */
    static void sound(Entity at, Holder<SoundEvent> sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void sound(ServerPlayer near, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        near.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** One of the moments the clients draw, to everyone tracking {@code about} (and them, if a player). */
    static void fx(Entity about, TimeNet.Fx fx) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(about, fx);
    }

    static TimeNet.Fx fx(TimeNet.Fx.Kind kind, int entity, List<Vec3> path, float value, int ticks) {
        return new TimeNet.Fx(kind, entity, path, value, ticks);
    }
}
