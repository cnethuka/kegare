package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.TechniqueMode;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.AbilityCategory;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.network.LoadoutEditPayload;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Sorcerer screen's Loadout page. Category tabs down the left (Innate Technique, Shared; locked entries show a
 * padlock), the selected category's abilities as tiles, and the loadout bar below with the fixed ultimate slot.
 * <ul>
 *   <li>drag a tile onto a slot, or click a tile and then a slot, to put it there;</li>
 *   <li>drag a slot onto another to swap them, or off the bar to empty it; right-click a slot to empty it;</li>
 *   <li>"Reset to default" puts the innate technique's abilities back in order.</li>
 * </ul>
 * Everything is a request ({@link LoadoutEditPayload}); the server checks it and the bar updates from the next sync.
 * Refusals (in combat, mid-cast, the ultimate slot...) come back and show here.
 */
final class LoadoutPage {
    private static final int TILE = GuiDraw.ICON_SLOT_SIZE;
    private static final int ULT = GuiDraw.ULTIMATE_SLOT_SIZE;
    private static final int GAP = 4;
    private static final int CATEGORY = 22;
    private static final int TOOLTIP_WIDTH = 220;
    private static final long STATUS_MS = 3500;

    private enum Kind { CATEGORY, TILE, SLOT, ULTIMATE, RESET }

    private record Hit(Kind kind, int x, int y, int w, int h, int index, @Nullable ResourceLocation ability, boolean unlocked) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static AbilityCategory category = AbilityCategory.INNATE_TECHNIQUE;

    private final List<Hit> hits = new ArrayList<>();
    /** Click-to-assign: the tile picked up by a click, waiting for a slot click. */
    @Nullable
    private ResourceLocation picked;
    @Nullable
    private Hit pressed;
    private double pressX, pressY;
    private boolean dragging;
    @Nullable
    private Component status;
    private long statusUntil;
    private int slotCount;

    /** Draws the page from {@code y} down; returns the bottom. */
    int render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        hits.clear();
        SyncPayload data = ClientData.get();
        InnateTechnique technique = TechniqueMode.current();
        if (data == null || technique == null) {
            return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.loadout.severed"), x, y, w, GuiDraw.GRAY);
        }

        // ---- Category tabs (left column)
        AbilityCategory[] categories = AbilityCategory.values();
        for (int i = 0; i < categories.length; i++) {
            int by = y + i * (CATEGORY + 4);
            boolean selected = categories[i] == category;
            GuiDraw.sprite(g, selected ? GuiDraw.SLOT_SELECTED : GuiDraw.SLOT, x, by, CATEGORY, CATEGORY);
            if (categories[i] == AbilityCategory.INNATE_TECHNIQUE) {
                GuiDraw.emblem(g, technique, x + 3, by + 3, 16);
            } else {
                GuiDraw.sprite(g, GuiDraw.CATEGORY_SHARED, x + 3, by + 3, 16, 16);
            }
            hits.add(new Hit(Kind.CATEGORY, x, by, CATEGORY, CATEGORY, i, null, true));
        }
        int columnBottom = y + categories.length * (CATEGORY + 4);

        // ---- The selected category: heading, description, tiles
        int ax = x + CATEGORY + 8;
        int aw = w - CATEGORY - 8;
        int cy = y;
        Component name = Component.translatable(category.translationKey());
        Component heading = category == AbilityCategory.INNATE_TECHNIQUE
                ? Component.translatable("gui.kegare.loadout.category_heading", name, technique.displayName()) : name;
        cy += GuiDraw.heading(g, font, heading, ax, cy, 1f, aw, GuiDraw.GOLD) + 1;
        cy += GuiDraw.wrapped(g, font, Component.translatable(category.descriptionKey()), ax, cy, aw, GuiDraw.GRAY) + 3;

        List<Optional<ResourceLocation>> loadout = data.loadout();
        slotCount = loadout.size();
        List<Ability> tiles = category == AbilityCategory.INNATE_TECHNIQUE ? technique.abilities() : SharedAbilities.all();
        if (tiles.isEmpty()) {
            cy += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.loadout.category_empty"), ax, cy, aw, GuiDraw.DARK_GRAY);
        }
        int perRow = Math.max(1, (aw + GAP) / (TILE + GAP));
        for (int i = 0; i < tiles.size(); i++) {
            Ability ability = tiles.get(i);
            int tx = ax + (i % perRow) * (TILE + GAP);
            int ty = cy + (i / perRow) * (TILE + GAP + 2);
            boolean unlocked = category == AbilityCategory.INNATE_TECHNIQUE || data.sharedUnlocked().contains(ability.id());
            if (unlocked) {
                if (!GuiDraw.abilitySlot(g, ability, tx, ty, ability.id().equals(picked))) {
                    g.drawCenteredString(font, ability.displayName().getString().substring(0, 1), tx + TILE / 2, ty + 6, GuiDraw.WHITE);
                }
                int slot = loadout.indexOf(Optional.of(ability.id()));
                if (slot >= 0) { // on the bar: its slot number, top-left in gold
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.drawString(font, Integer.toString(slot + 1), tx + 2, ty + 1, GuiDraw.GOLD, true);
                    g.pose().popPose();
                }
            } else {
                GuiDraw.lockedSlot(g, font, tx, ty, Component.empty());
            }
            hits.add(new Hit(Kind.TILE, tx, ty, TILE, TILE, i, ability.id(), unlocked));
        }
        if (!tiles.isEmpty()) cy += ((tiles.size() - 1) / perRow + 1) * (TILE + GAP + 2);
        cy = Math.max(cy, columnBottom) + 6;

        // ---- The loadout bar: regular slots, then the fixed ultimate
        cy += GuiDraw.heading(g, font, Component.translatable("gui.kegare.loadout.heading"), x, cy, 1f, w, GuiDraw.MALICE) + 4;
        boolean hasUlt = technique.ultimate().isPresent() || technique.ultimateLocked();
        int rowW = slotCount * TILE + Math.max(0, slotCount - 1) * GAP + (hasUlt ? 10 + ULT : 0);
        int sx0 = x + (w - rowW) / 2;
        int slotY = cy + (ULT - TILE);
        Hit over = hitAt(mouseX, mouseY);
        for (int i = 0; i < slotCount; i++) {
            int sx = sx0 + i * (TILE + GAP);
            Ability ability = loadout.get(i).flatMap(id -> AbilityLookup.find(technique, id)).orElse(null);
            boolean target = (dragging || picked != null) && over != null && over.kind == Kind.SLOT && over.index == i;
            if (ability == null) {
                GuiDraw.emptySlot(g, sx, slotY, TILE, target);
            } else if (!GuiDraw.abilitySlot(g, ability, sx, slotY, target)) {
                g.drawCenteredString(font, ability.displayName().getString().substring(0, 1), sx + TILE / 2, slotY + 6, GuiDraw.WHITE);
            }
            if (dragging && pressed != null && pressed.kind == Kind.SLOT && pressed.index == i) {
                g.fill(sx + 2, slotY + 2, sx + TILE - 2, slotY + TILE - 2, 0x88000000); // the slot being dragged
            }
            GuiDraw.centered(g, font, Component.literal(Integer.toString(i + 1)), sx + TILE / 2, slotY + TILE + 2, 1f,
                    i == data.selectedSlot() ? GuiDraw.GOLD : GuiDraw.GRAY);
            hits.add(new Hit(Kind.SLOT, sx, slotY, TILE, TILE, i, loadout.get(i).orElse(null), true));
        }
        if (hasUlt) {
            int ux = sx0 + slotCount * (TILE + GAP) - GAP + 10;
            Optional<Ability> ult = technique.ultimate();
            if (ult.isPresent()) {
                GuiDraw.abilitySlot(g, ult.get(), ux, cy, ULT, false);
            } else {
                GuiDraw.lockedSlot(g, font, ux, cy, ULT, Component.empty());
            }
            GuiDraw.centered(g, font, KegareKeys.ULTIMATE.getTranslatedKeyMessage(), ux + ULT / 2, slotY + TILE + 2, 1f, GuiDraw.GRAY);
            hits.add(new Hit(Kind.ULTIMATE, ux, cy, ULT, ULT, slotCount, ult.map(Ability::id).orElse(null), true));
        }
        cy += ULT + 14;

        // ---- Status, hint, reset
        if (data.inCombat()) {
            cy += GuiDraw.wrapped(g, font, Component.translatable("message.kegare.loadout.in_combat"), x, cy, w, GuiDraw.RED) + 2;
        }
        if (status != null && Util.getMillis() < statusUntil) {
            cy += GuiDraw.wrapped(g, font, status, x, cy, w, GuiDraw.RED) + 2;
        }
        cy += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.loadout.hint"), x, cy, w, GuiDraw.DARK_GRAY) + 4;
        Component reset = Component.translatable("gui.kegare.loadout.reset");
        int bw = font.width(reset) + 12;
        boolean resetHover = over != null && over.kind == Kind.RESET;
        GuiDraw.sprite(g, resetHover ? GuiDraw.TAB_SELECTED : GuiDraw.TAB, x, cy, bw, 14);
        GuiDraw.centered(g, font, reset, x + bw / 2, cy + 3, 1f, resetHover ? GuiDraw.WHITE : GuiDraw.GRAY);
        hits.add(new Hit(Kind.RESET, x, cy, bw, 14, 0, null, true));
        return cy + 16;
    }

    /** After the page (outside its scissor): the dragged icon under the cursor, else the hover tooltip. */
    void renderOverlay(GuiGraphics g, Font font, int mouseX, int mouseY, boolean mouseInContent) {
        InnateTechnique technique = TechniqueMode.current();
        if (technique == null) return;
        if (dragging && pressed != null && pressed.ability != null) {
            AbilityLookup.find(technique, pressed.ability).ifPresent(ability -> {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                GuiDraw.abilitySlot(g, ability, mouseX - TILE / 2, mouseY - TILE / 2, true);
                g.pose().popPose();
            });
            return;
        }
        if (!mouseInContent) return;
        Hit hit = hitAt(mouseX, mouseY);
        if (hit == null) return;
        List<FormattedCharSequence> lines = tooltip(font, technique, hit);
        if (!lines.isEmpty()) g.renderTooltip(font, lines, mouseX, mouseY);
    }

    private List<FormattedCharSequence> tooltip(Font font, InnateTechnique technique, Hit hit) {
        List<FormattedCharSequence> out = new ArrayList<>();
        SyncPayload data = ClientData.get();
        switch (hit.kind) {
            case CATEGORY -> {
                AbilityCategory c = AbilityCategory.values()[hit.index];
                out.add(Component.translatable(c.translationKey()).withStyle(ChatFormatting.GOLD).getVisualOrderText());
                out.addAll(font.split(Component.translatable(c.descriptionKey()).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            }
            case TILE, SLOT, ULTIMATE -> {
                if (hit.kind == Kind.TILE && !hit.unlocked) {
                    out.add(Component.translatable("gui.kegare.loadout.locked").withStyle(ChatFormatting.GRAY).getVisualOrderText());
                    out.addAll(font.split(Component.translatable("gui.kegare.loadout.locked.desc").withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
                    break;
                }
                Ability ability = hit.ability == null ? null : AbilityLookup.find(technique, hit.ability).orElse(null);
                if (ability == null) {
                    out.add(Component.translatable(hit.kind == Kind.ULTIMATE ? "gui.kegare.sorcerer.ability.ultimate_locked"
                            : "hud.kegare.empty_slot").withStyle(ChatFormatting.GRAY).getVisualOrderText());
                } else {
                    out.add(ability.displayName().copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
                    out.add(Component.translatable("gui.kegare.loadout.kind", Component.translatable(AbilityLookup.category(ability.id()).translationKey()),
                            Component.translatable(ability.castType().translationKey())).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                    out.addAll(font.split(GuiDraw.description(ability.descriptionId()).copy().withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
                    for (Component line : CostTooltip.lines(ability.id())) out.add(line.getVisualOrderText());
                    ability.special().ifPresent(special -> out.add(Component.translatable("gui.kegare.radial.special",
                            KegareKeys.SPECIAL.getTranslatedKeyMessage(), special.displayName()).withStyle(ChatFormatting.GOLD).getVisualOrderText()));
                }
                if (hit.kind == Kind.ULTIMATE) {
                    out.addAll(font.split(Component.translatable("gui.kegare.loadout.ultimate_fixed").withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
                } else if (hit.kind == Kind.SLOT) {
                    out.add(Component.translatable("gui.kegare.loadout.slot_hint").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                } else if (data != null) {
                    int slot = data.loadout().indexOf(Optional.of(hit.ability));
                    out.add((slot >= 0 ? Component.translatable("gui.kegare.loadout.in_slot", slot + 1)
                            : Component.translatable("gui.kegare.loadout.not_slotted")).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
                }
            }
            case RESET -> out.addAll(font.split(Component.translatable("gui.kegare.loadout.reset.desc").withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        }
        return out;
    }

    // ---------------------------------------------------------------- input

    boolean mouseClicked(double mx, double my, int button) {
        Hit hit = hitAt(mx, my);
        if (hit == null) {
            picked = null;
            return false;
        }
        if (button == 1) {
            if (hit.kind == Kind.SLOT) send(LoadoutService.Action.CLEAR, hit.index, 0, null);
            return true;
        }
        if (button != 0) return false;
        switch (hit.kind) {
            case CATEGORY -> {
                category = AbilityCategory.values()[hit.index];
                picked = null;
            }
            case TILE -> {
                if (hit.unlocked) press(hit, mx, my);
            }
            case SLOT -> {
                if (picked != null) {
                    send(LoadoutService.Action.SET, hit.index, 0, picked);
                    picked = null;
                } else {
                    press(hit, mx, my);
                }
            }
            case ULTIMATE -> {
                if (picked != null) {
                    send(LoadoutService.Action.SET, hit.index, 0, picked); // the server refuses: the ultimate is fixed
                    picked = null;
                }
            }
            case RESET -> send(LoadoutService.Action.RESET, 0, 0, null);
        }
        return true;
    }

    boolean mouseDragged(double mx, double my, int button) {
        if (pressed == null || button != 0) return false;
        if (!dragging && Math.hypot(mx - pressX, my - pressY) > 3) dragging = true;
        return true;
    }

    boolean mouseReleased(double mx, double my, int button) {
        if (pressed == null || button != 0) return false;
        Hit source = pressed;
        pressed = null;
        if (!dragging) { // a click on a tile picks it up (click it again to put it down)
            if (source.kind == Kind.TILE) picked = source.ability.equals(picked) ? null : source.ability;
            return true;
        }
        dragging = false;
        Hit target = hitAt(mx, my);
        if (source.kind == Kind.TILE) {
            if (target != null && (target.kind == Kind.SLOT || target.kind == Kind.ULTIMATE)) {
                send(LoadoutService.Action.SET, target.index, 0, source.ability);
            }
        } else if (source.kind == Kind.SLOT) {
            if (target != null && (target.kind == Kind.SLOT || target.kind == Kind.ULTIMATE)) {
                if (target.index != source.index) send(LoadoutService.Action.SWAP, source.index, target.index, null);
            } else if (source.ability != null) {
                send(LoadoutService.Action.CLEAR, source.index, 0, null); // dropped off the bar
            }
        }
        return true;
    }

    void onResult(LoadoutResult result) {
        if (result.translationKey() == null) return;
        status = Component.translatable(result.translationKey());
        statusUntil = Util.getMillis() + STATUS_MS;
    }

    private void press(Hit hit, double mx, double my) {
        pressed = hit;
        pressX = mx;
        pressY = my;
        dragging = false;
    }

    private static void send(LoadoutService.Action action, int slot, int other, @Nullable ResourceLocation ability) {
        PacketDistributor.sendToServer(new LoadoutEditPayload(action, slot, other, Optional.ofNullable(ability)));
    }

    @Nullable
    private Hit hitAt(double mx, double my) {
        for (Hit hit : hits) if (hit.contains(mx, my)) return hit;
        return null;
    }
}
