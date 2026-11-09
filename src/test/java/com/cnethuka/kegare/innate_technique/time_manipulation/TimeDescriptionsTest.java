package com.cnethuka.kegare.innate_technique.time_manipulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Time Manipulation's descriptions (DESIGN §3.26) state the numbers the server is set to, never fixed ones: every number in their text
 * is a placeholder filled from {@code [time_manipulation]}, and changing the config changes what they read.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TimeDescriptionsTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    private static JsonObject lang() throws Exception {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/generated/resources/assets/kegare/lang/en_us.json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void everyNumberIsTheConfigs(MinecraftServer server) throws Exception {
        JsonObject lang = lang();
        List<Ability> abilities = List.of(TimeManipulation.POINT_MAKER, TimeManipulation.REWIND, TimeManipulation.ACCELERATE,
                TimeManipulation.FAST_FORWARD, TimeManipulation.DECELERATE, TimeManipulation.STASIS_FIELD, TimeManipulation.STANDSTILL);
        for (Ability a : abilities) {
            String text = lang.get(a.descriptionId() + ".desc").getAsString();
            Matcher m = PLACEHOLDER.matcher(text);
            int highest = 0, plain = 0;
            while (m.find()) {
                if (m.group(1) == null) plain++;
                else highest = Math.max(highest, Integer.parseInt(m.group(1).substring(0, m.group(1).length() - 1)));
            }
            assertEquals(a.descriptionArgs().length, Math.max(highest, plain), a.id() + ": one placeholder per configured number");
            assertFalse(PLACEHOLDER.matcher(text).replaceAll("").matches(".*\\d.*"), a.id() + ": no number of its own: " + text);
        }
    }

    @Test
    void theyReadWhatTheServerIsSetTo(MinecraftServer server) {
        double rate = TimeConfig.ACCELERATE_RATE.get(), heal = TimeConfig.RETURN_HEAL.get();
        try {
            assertArrayEquals(new Object[] {"2"}, TimeManipulation.ACCELERATE.descriptionArgs(), "the default: 2 times as fast");
            TimeConfig.ACCELERATE_RATE.set(3.0);
            assertArrayEquals(new Object[] {"3"}, TimeManipulation.ACCELERATE.descriptionArgs(), "3 times as fast once it's set to 3");
            TimeConfig.ACCELERATE_RATE.set(1.5);
            assertArrayEquals(new Object[] {"1.5"}, TimeManipulation.ACCELERATE.descriptionArgs());
            TimeConfig.RETURN_HEAL.set(0.25);
            assertEquals("25%", TimeManipulation.POINT_MAKER.descriptionArgs()[2], "a share reads as a percentage");
            assertEquals("5%", TimeManipulation.STASIS_FIELD.descriptionArgs()[2], "a twentieth of their speed");
        } finally {
            TimeConfig.ACCELERATE_RATE.set(rate);
            TimeConfig.RETURN_HEAL.set(heal);
        }
    }
}
