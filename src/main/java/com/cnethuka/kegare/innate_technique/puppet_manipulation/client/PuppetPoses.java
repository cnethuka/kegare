package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.google.gson.JsonArray;
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
 * itself: per bone, degrees. Both keyframe styles PAL reads are understood: Blockbench's Bedrock export
 * ({@code {"post": [x, y, z], "lerp_mode": "linear" | "catmullrom" | "step"}}, what {@code tools/anims} writes) and
 * GeckoLib's ({@code {"vector": [x, y, z], "easing": "easeOutQuad"}}). A malformed animation is skipped, not the file.
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
            if (t <= keys.get(0).time) return new Vector3f(keys.get(0).value);
            for (int i = 1; i < keys.size(); i++) {
                Key prev = keys.get(i - 1), next = keys.get(i);
                if (next.time < t) continue;
                float span = next.time - prev.time;
                float f = span <= 0 ? 1f : (t - prev.time) / span;
                // Bedrock lerp modes belong to the key a segment starts from, GeckoLib easings to the key it ends on.
                String mode = prev.easing.equals("catmullrom") || prev.easing.equals("step") ? prev.easing : next.easing;
                if (mode.equals("step")) return new Vector3f(prev.value);
                if (mode.equals("catmullrom")) {
                    Vector3f before = keys.get(Math.max(0, i - 2)).value, after = keys.get(Math.min(keys.size() - 1, i + 1)).value;
                    return new Vector3f(catmullRom(before.x, prev.value.x, next.value.x, after.x, f),
                            catmullRom(before.y, prev.value.y, next.value.y, after.y, f),
                            catmullRom(before.z, prev.value.z, next.value.z, after.z, f));
                }
                return new Vector3f(prev.value).lerp(next.value, ease(mode, f));
            }
            return new Vector3f(keys.get(keys.size() - 1).value);
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
        JsonObject animations;
        try (Reader reader = resource.get().openAsReader()) {
            animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        } catch (Exception e) {
            Kegare.LOGGER.warn("Couldn't read the puppet animations in {}", FILE, e);
            return;
        }
        for (Map.Entry<String, JsonElement> a : animations.entrySet()) {
            try {
                CLIPS.put(Kegare.id(a.getKey()), parse(a.getValue().getAsJsonObject()));
            } catch (Exception e) {
                Kegare.LOGGER.warn("Skipping puppet animation {} in {}: {}", a.getKey(), FILE, e.toString());
            }
        }
    }

    static Clip parse(JsonObject anim) {
        float length = anim.has("animation_length") ? anim.get("animation_length").getAsFloat() : 0f;
        Map<String, List<Key>> rotations = new HashMap<>();
        if (anim.has("bones")) {
            for (Map.Entry<String, JsonElement> b : anim.getAsJsonObject("bones").entrySet()) {
                JsonObject bone = b.getValue().getAsJsonObject();
                if (!bone.has("rotation") || !bone.get("rotation").isJsonObject()) continue;
                List<Key> keys = new ArrayList<>();
                for (Map.Entry<String, JsonElement> k : bone.getAsJsonObject("rotation").entrySet()) {
                    JsonObject frame = k.getValue().getAsJsonObject();
                    JsonArray v = frame.has("post") ? frame.getAsJsonArray("post") : frame.getAsJsonArray("vector");
                    String easing = frame.has("lerp_mode") ? frame.get("lerp_mode").getAsString()
                            : frame.has("easing") ? frame.get("easing").getAsString() : "linear";
                    keys.add(new Key(Float.parseFloat(k.getKey()),
                            new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()), easing));
                }
                keys.sort((x, y) -> Float.compare(x.time, y.time));
                rotations.put(b.getKey(), keys);
            }
        }
        return new Clip(length, rotations);
    }

    /** Catmull-Rom between p1 and p2 (p0 and p3 their neighbours), as Blockbench and PAL draw it. */
    static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
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
