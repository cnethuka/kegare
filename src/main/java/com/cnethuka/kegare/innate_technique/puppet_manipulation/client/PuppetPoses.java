package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Plays Player Animation Library animations on the puppet model. PAL itself only animates players, so this reads the
 * same file ({@code player_animations/puppet_manipulation.json}, Bedrock/PAL format) and samples rotation keyframes
 * itself: per bone, degrees, with linear, easeInOutSine, easeInQuad and easeOutQuad easing. Enough for the idle spin.
 */
public final class PuppetPoses implements ResourceManagerReloadListener {
    private static final ResourceLocation FILE = Kegare.id("player_animations/puppet_manipulation.json");

    /** One animation: rotation keyframes per bone, and its length in seconds. */
    public record Clip(float length, Map<String, List<Key>> rotations) {
        /** The bone's rotation (degrees) at {@code seconds} into the clip (looping), or null if it has no keyframes. */
        @Nullable
        public Vector3f rotation(String bone, float seconds) {
            List<Key> keys = rotations.get(bone);
            if (keys == null || keys.isEmpty()) return null;
            float t = length > 0 ? seconds % length : 0;
            Key prev = keys.get(0);
            if (t <= prev.time) return new Vector3f(prev.value);
            for (Key next : keys) {
                if (next.time >= t) {
                    float span = next.time - prev.time;
                    float f = span <= 0 ? 1f : ease(next.easing, (t - prev.time) / span);
                    return new Vector3f(prev.value).lerp(next.value, f);
                }
                prev = next;
            }
            return new Vector3f(prev.value);
        }
    }

    public record Key(float time, Vector3f value, String easing) {}

    private static final Map<ResourceLocation, Clip> CLIPS = new HashMap<>();

    public static Optional<Clip> clip(ResourceLocation id) {
        return Optional.ofNullable(CLIPS.get(id));
    }

    public static Optional<Clip> idleSpin() {
        return clip(PuppetIds.ANIM_PUPPET_IDLE_SPIN);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        CLIPS.clear();
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) return;
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> a : animations.entrySet()) {
                JsonObject anim = a.getValue().getAsJsonObject();
                float length = anim.has("animation_length") ? anim.get("animation_length").getAsFloat() : 0f;
                Map<String, List<Key>> rotations = new HashMap<>();
                if (anim.has("bones")) {
                    for (Map.Entry<String, JsonElement> b : anim.getAsJsonObject("bones").entrySet()) {
                        JsonObject bone = b.getValue().getAsJsonObject();
                        if (!bone.has("rotation") || !bone.get("rotation").isJsonObject()) continue;
                        List<Key> keys = new ArrayList<>();
                        for (Map.Entry<String, JsonElement> k : bone.getAsJsonObject("rotation").entrySet()) {
                            JsonObject frame = k.getValue().getAsJsonObject();
                            var v = frame.getAsJsonArray("vector");
                            keys.add(new Key(Float.parseFloat(k.getKey()),
                                    new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()),
                                    frame.has("easing") ? frame.get("easing").getAsString() : "linear"));
                        }
                        keys.sort((x, y) -> Float.compare(x.time, y.time));
                        rotations.put(b.getKey(), keys);
                    }
                }
                CLIPS.put(Kegare.id(a.getKey()), new Clip(length, rotations));
            }
        } catch (Exception e) {
            Kegare.LOGGER.warn("Couldn't read the puppet animations in {}", FILE, e);
        }
    }

    private static float ease(String easing, float t) {
        return switch (easing) {
            case "easeInOutSine" -> -(Mth.cos(Mth.PI * t) - 1f) / 2f;
            case "easeInQuad" -> t * t;
            case "easeOutQuad" -> 1f - (1f - t) * (1f - t);
            default -> t;
        };
    }
}
