package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * DEV TOOL, registered only in the dev environment. {@code /kegarefx export} writes every effect in
 * {@link FxCatalog} into the project's resources (and the running game's copy, then drops Photon's cache so the new
 * versions play at once). {@code /kegarefx play <name> [self|front|loop]} previews one.
 * Text here is developer-only, so it is not in the lang file.
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class FxDevCommand {
    private FxDevCommand() {}

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("kegarefx")
                .then(Commands.literal("export").executes(c -> export(c.getSource())))
                .then(Commands.literal("play")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
                                .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "name"), "front"))
                                .then(Commands.argument("where", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("front", "self", "loop"), b))
                                        .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "name"),
                                                StringArgumentType.getString(c, "where")))))));
    }

    private static List<String> names() {
        List<String> out = new ArrayList<>();
        FxCatalog.effects().keySet().forEach(id -> out.add(shortName(id)));
        return out;
    }

    private static int export(CommandSourceStack source) {
        Path project = Path.of(System.getProperty("kegare.projectDir", FMLPaths.GAMEDIR.get().resolve("..").toString())).normalize();
        try {
            List<Path> roots = List.of(project.resolve("src/main/resources/assets"), project.resolve("build/resources/main/assets"));
            int files = 0;
            for (Path root : roots) {
                if (!Files.isDirectory(root.getParent())) continue;
                files += FxKit.export(FxCatalog.effects(), root).size();
            }
            FXHelper.invalidate(new ArrayList<>(FxCatalog.effects().keySet()));
            int count = files;
            source.sendSuccess(() -> Component.literal("Exported " + count + " .fx files under " + project), false);
            return 1;
        } catch (Exception e) {
            Kegare.LOGGER.error("FX export failed", e);
            source.sendFailure(Component.literal("Export failed: " + e));
            return 0;
        }
    }

    private static int play(CommandSourceStack source, String name, String where) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0;
        ResourceLocation id = FxCatalog.effects().keySet().stream().filter(e -> shortName(e).equals(name)).findFirst().orElse(null);
        if (id == null) return 0;
        switch (where) {
            case "self" -> ClientVisuals.playOnce(player, Visual.effect(id, Visual.Anchor.BODY));
            case "loop" -> ClientVisuals.startLoop(player, "dev:" + name, Visual.effect(id, Visual.Anchor.EYES), 0);
            default -> {
                Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(3));
                if (Minecraft.getInstance().level != null) ClientVisuals.playAt(Minecraft.getInstance().level, at, Visual.effect(id, Visual.Anchor.BODY));
            }
        }
        return 1;
    }

    private static String shortName(ResourceLocation id) {
        return id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
    }
}
