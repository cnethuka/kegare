package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.Requirement;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.network.SorcererInfoPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Sorcerer screen (key K): Malice, Innate Technique (or the Severed page), Traits and Pity. All numbers come from
 * a {@link SorcererInfoPayload} computed by the server; this screen only displays them.
 * Keys: 1-4 or Tab switch pages, arrows / mouse wheel scroll, K or Esc close.
 */
public final class SorcererScreen extends Screen {
    private enum Page { MALICE, INNATE_TECHNIQUE, TRAITS, PITY }

    private static Page lastPage = Page.MALICE;
    private static final int TOOLTIP_WIDTH = 220;

    private SorcererInfoPayload info;
    private Page page = lastPage;
    private double scroll;
    private int contentHeight;

    private int panelX, panelY, panelW, panelH;
    /** Ability rows drawn this frame (screen coordinates), for hover tooltips. */
    private final java.util.List<HoverRow> hoverRows = new java.util.ArrayList<>();

    private record HoverRow(int x, int y, int w, int h, java.util.List<FormattedCharSequence> lines) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    private int contentX, contentY, contentW, contentH;
    private int tabY, tabH, tabW;

    public SorcererScreen(SorcererInfoPayload info) {
        super(Component.translatable("gui.kegare.sorcerer.title"));
        this.info = info;
    }

    /** New data from the server while open (e.g. pressing K again). */
    void update(SorcererInfoPayload info) {
        this.info = info;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 12, 400);
        panelH = Math.min(height - 12, 280);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        tabH = 18;
        tabY = panelY + 18;
        tabW = (panelW - 12) / Page.values().length;
        contentX = panelX + 10;
        contentY = tabY + tabH + 6;
        contentW = panelW - 20 - 6; // room for the scrollbar
        contentH = panelY + panelH - 8 - contentY;
        clampScroll();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        GuiDraw.sprite(g, GuiDraw.PANEL, panelX, panelY, panelW, panelH);
        GuiDraw.text(g, font, title, panelX + 8, panelY + 6, 1f, GuiDraw.GOLD);
        renderTabs(g, mouseX, mouseY);

        hoverRows.clear();
        g.enableScissor(contentX, contentY, contentX + contentW + 6, contentY + contentH);
        int top = contentY - (int) scroll;
        int bottom = switch (page) {
            case MALICE -> renderMalice(g, top);
            case INNATE_TECHNIQUE -> info.severed() ? renderSevered(g, top) : renderInnateTechnique(g, top);
            case TRAITS -> renderTraits(g, top);
            case PITY -> renderPity(g, top);
        };
        g.disableScissor();
        contentHeight = bottom - top;
        renderScrollbar(g);
        if (mouseY >= contentY && mouseY < contentY + contentH) {
            for (HoverRow row : hoverRows) { // first match wins: special lines are added before their whole card
                if (row.contains(mouseX, mouseY)) {
                    if (!row.lines().isEmpty()) g.renderTooltip(font, row.lines(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private Component tabLabel(Page p) {
        return Component.translatable(switch (p) {
            case MALICE -> "gui.kegare.sorcerer.tab.malice";
            case INNATE_TECHNIQUE -> info.severed() ? "gui.kegare.sorcerer.tab.severed" : "gui.kegare.sorcerer.tab.innate_technique";
            case TRAITS -> "gui.kegare.sorcerer.tab.traits";
            case PITY -> "gui.kegare.sorcerer.tab.pity";
        });
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (Page p : Page.values()) {
            int x = panelX + 6 + p.ordinal() * tabW;
            boolean selected = p == page;
            GuiDraw.sprite(g, selected ? GuiDraw.TAB_SELECTED : GuiDraw.TAB, x, tabY, tabW - 2, tabH);
            Component label = tabLabel(p);
            int labelWidth = font.width(label);
            float scale = Math.min(1f, (tabW - 8) / (float) Math.max(1, labelWidth));
            int color = selected ? GuiDraw.WHITE : (hoverTab(mouseX, mouseY) == p ? 0xFFDDDDDD : GuiDraw.GRAY);
            GuiDraw.centered(g, font, label, x + (tabW - 2) / 2, tabY + (tabH - (int) (8 * scale)) / 2, scale, color);
        }
    }

    // ---------------------------------------------------------------- pages

    private int renderMalice(GuiGraphics g, int y) {
        Component tier = Component.translatable(tierKey(info.tier()));
        y += GuiDraw.heading(g, font, Component.translatable("gui.kegare.sorcerer.malice.heading"), contentX, y, 1.5f, contentW, GuiDraw.MALICE) + 2;
        y += line(g, Component.translatable("gui.kegare.sorcerer.malice.capacity", info.capacity(), tier), y, GuiDraw.WHITE);
        y += line(g, Component.translatable("gui.kegare.sorcerer.malice.reserve", (int) Math.floor(info.current()), info.capacity()), y, GuiDraw.WHITE);
        GuiDraw.maliceBar(g, contentX, y + 1, Math.min(contentW, 200), info.capacity() <= 0 ? 0f : info.current() / info.capacity());
        y += GuiDraw.BAR_HEIGHT + 5;
        y += line(g, Component.translatable("gui.kegare.sorcerer.malice.regen", GuiDraw.amount(Math.round(info.regenPerSecond() * 10f) / 10f),
                GuiDraw.amount(info.regenPercent())), y, GuiDraw.WHITE);
        if (info.regenPaused()) y += line(g, Component.translatable("gui.kegare.sorcerer.malice.regen_paused"), y, GuiDraw.RED);
        if (info.severed()) y += line(g, Component.translatable("gui.kegare.sorcerer.malice.severed"), y, GuiDraw.RED);
        return y;
    }

    private int renderInnateTechnique(GuiGraphics g, int y) {
        InnateTechnique innateTechnique = info.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        if (innateTechnique == null) return y + line(g, Component.translatable("gui.kegare.sorcerer.innate_technique.none"), y, GuiDraw.GRAY);
        int color = 0xFF000000 | innateTechnique.rarity().color();

        // Header: emblem on the left; name, rarity and description beside it.
        int emblem = 48;
        GuiDraw.framedEmblem(g, innateTechnique, contentX + 2, y + 2, emblem);
        int tx = contentX + emblem + 10;
        int tw = contentW - emblem - 10;
        int ty = y + 2;
        Component name = innateTechnique.displayName();
        ty += GuiDraw.text(g, font, name, tx, ty, font.width(name) * 1.5f <= tw ? 1.5f : 1f, color) + 2;
        ty += GuiDraw.wrapped(g, font, Component.translatable(innateTechnique.rarity().translationKey()), tx, ty, tw, color) + 2;
        ty += GuiDraw.wrapped(g, font, GuiDraw.description(innateTechnique.descriptionId()), tx, ty, tw, GuiDraw.GRAY);
        y = Math.max(y + emblem + 4, ty) + 8;

        y += GuiDraw.heading(g, font, Component.translatable("gui.kegare.sorcerer.abilities"), contentX, y, 1f, contentW, GuiDraw.GOLD) + 2;
        int slot = 1;
        int textX = contentX + GuiDraw.ICON_SLOT_SIZE + 6;
        int textW = contentW - GuiDraw.ICON_SLOT_SIZE - 6;
        for (SorcererInfoPayload.AbilityInfo a : info.abilities()) {
            Ability ability = innateTechnique.findAbility(a.id()).orElse(null);
            Component abilityName = ability != null ? ability.displayName() : Component.literal(a.id().toString());
            CastType castType = castType(a.castType());
            Component slotLabel = a.ultimate() ? KegareKeys.ULTIMATE.getTranslatedKeyMessage() : Component.literal(Integer.toString(slot));
            Component header = a.ultimate()
                    ? Component.translatable("gui.kegare.sorcerer.ability.ultimate", abilityName, Component.translatable(castType.translationKey()))
                    : Component.translatable("gui.kegare.sorcerer.ability.slot", slot++, abilityName, Component.translatable(castType.translationKey()));

            int rowTop = y;
            if (ability != null && !GuiDraw.abilitySlot(g, ability, contentX, y, a.ultimate())) {
                GuiDraw.centered(g, font, slotLabel, contentX + GuiDraw.ICON_SLOT_SIZE / 2, y + 6, 1f, GuiDraw.GRAY);
            }
            int rowY = y;
            rowY += GuiDraw.wrapped(g, font, header, textX, rowY, textW, a.ultimate() ? GuiDraw.GOLD : GuiDraw.WHITE);
            Component stats = (castType == CastType.CHANNELED || castType == CastType.TOGGLE)
                    ? Component.translatable("gui.kegare.sorcerer.ability.stats_upkeep", GuiDraw.amount(a.cost()),
                            GuiDraw.amount(a.upkeepPerSecond()), GuiDraw.seconds(a.cooldownTicks()))
                    : Component.translatable("gui.kegare.sorcerer.ability.stats", GuiDraw.amount(a.cost()), GuiDraw.seconds(a.cooldownTicks()));
            rowY += GuiDraw.wrapped(g, font, stats, textX, rowY, textW, GuiDraw.MALICE);
            if (ability != null) {
                rowY += GuiDraw.wrapped(g, font, GuiDraw.description(ability.descriptionId()), textX, rowY, textW, GuiDraw.DARK_GRAY);
            }
            if (!a.ultimate()) rowY += renderSpecialLine(g, ability, a, textX, rowY + 1, textW) + 1;
            y = Math.max(y + GuiDraw.ICON_SLOT_SIZE + 2, rowY) + 5;
            hoverRows.add(new HoverRow(contentX, rowTop, contentW, y - rowTop, visual(CostTooltip.lines(a.id()))));
        }
        if (innateTechnique.ultimateLocked()) {
            GuiDraw.lockedSlot(g, font, contentX, y, KegareKeys.ULTIMATE.getTranslatedKeyMessage());
            int rowY = y;
            rowY += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.ability.ultimate_locked"), textX, rowY, textW, GuiDraw.DARK_GRAY);
            rowY += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.ability.ultimate_locked.desc"), textX, rowY, textW, GuiDraw.DARK_GRAY);
            y = Math.max(y + GuiDraw.ICON_SLOT_SIZE + 2, rowY) + 5;
        }
        return y;
    }

    /** The R line under an ability: its special (hover for details) or "No special". Returns the height used. */
    private int renderSpecialLine(GuiGraphics g, @Nullable Ability ability, SorcererInfoPayload.AbilityInfo a, int x, int y, int w) {
        Ability special = ability == null ? null : ability.special().orElse(null);
        SorcererInfoPayload.SpecialInfo sp = a.special().orElse(null);
        GuiDraw.specialBadge(g, x, y, sp != null);
        if (sp == null || special == null) {
            int h = GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.special.none"), x + 12, y + 1, w - 12, GuiDraw.DARK_GRAY);
            return Math.max(10, h + 1);
        }
        CastType castType = castType(sp.castType());
        Component typeName = Component.translatable(castType.translationKey());
        Component line = sp.upkeepPerSecond() > 0
                ? Component.translatable("gui.kegare.sorcerer.special.line_upkeep", special.displayName(), typeName,
                        GuiDraw.amount(sp.cost()), GuiDraw.amount(sp.upkeepPerSecond()))
                : Component.translatable("gui.kegare.sorcerer.special.line", special.displayName(), typeName, GuiDraw.amount(sp.cost()));
        int h = Math.max(10, GuiDraw.wrapped(g, font, line, x + 12, y + 1, w - 12, sp.available() ? GuiDraw.GOLD : GuiDraw.GRAY) + 1);
        hoverRows.add(new HoverRow(x, y, w, h, specialTooltip(ability, special, sp, castType)));
        return h;
    }

    private java.util.List<FormattedCharSequence> specialTooltip(Ability base, Ability special, SorcererInfoPayload.SpecialInfo sp, CastType castType) {
        java.util.List<FormattedCharSequence> out = new java.util.ArrayList<>();
        out.add(special.displayName().copy().withStyle(ChatFormatting.GOLD).getVisualOrderText());
        out.add(Component.translatable("gui.kegare.special.kind", KegareKeys.SPECIAL.getTranslatedKeyMessage(),
                Component.translatable(castType.translationKey())).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        out.addAll(font.split(GuiDraw.description(special.descriptionId()).copy().withStyle(ChatFormatting.WHITE), TOOLTIP_WIDTH));
        out.addAll(visual(CostTooltip.lines(special.id())));
        for (Requirement requirement : special.requirements()) {
            out.addAll(font.split(Component.translatable("gui.kegare.special.requires", requirement.description())
                    .withStyle(sp.available() ? ChatFormatting.GREEN : ChatFormatting.RED), TOOLTIP_WIDTH));
        }
        out.addAll(font.split(Component.translatable("gui.kegare.special.shared_cooldown", base.displayName())
                .withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        return out;
    }

    private static java.util.List<FormattedCharSequence> visual(java.util.List<Component> lines) {
        return lines.stream().map(Component::getVisualOrderText).toList();
    }

    private static CastType castType(int ordinal) {
        return CastType.values()[Mth.clamp(ordinal, 0, CastType.values().length - 1)];
    }

    private int renderSevered(GuiGraphics g, int y) {
        int emblem = 32;
        GuiDraw.sprite(g, GuiDraw.SEVERED_EMBLEM, contentX + contentW / 2 - emblem / 2, y, emblem, emblem);
        y += emblem + 6;
        GuiDraw.centered(g, font, Component.translatable("gui.kegare.sorcerer.severed.heading"), contentX + contentW / 2, y, 1.5f, 0xFFE6E6E6);
        y += 16;
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.sorcerer.severed.desc"), contentX + contentW / 2, y, contentW, GuiDraw.GRAY);
        return y;
    }

    private int renderTraits(GuiGraphics g, int y) {
        y += GuiDraw.heading(g, font, Component.translatable("gui.kegare.sorcerer.traits.heading"), contentX, y, 1.5f, contentW, GuiDraw.AQUA) + 2;
        if (info.traits().isEmpty()) return y + line(g, Component.translatable("gui.kegare.sorcerer.traits.none"), y, GuiDraw.GRAY);
        for (ResourceLocation id : info.traits()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null) continue;
            Component name = trait.isUnique()
                    ? Component.translatable("gui.kegare.sorcerer.traits.unique", trait.displayName())
                    : trait.displayName();
            y += line(g, name, y, GuiDraw.AQUA);
            y += GuiDraw.wrapped(g, font, GuiDraw.description(trait.descriptionId()), contentX + 8, y, contentW - 8, GuiDraw.GRAY) + 4;
        }
        return y;
    }

    private int renderPity(GuiGraphics g, int y) {
        y += GuiDraw.heading(g, font, Component.translatable("gui.kegare.sorcerer.pity.heading"), contentX, y, 1.5f, contentW, GuiDraw.GOLD) + 2;
        y += line(g, Component.translatable("gui.kegare.sorcerer.pity.count", info.rollsSinceLegendary()), y, GuiDraw.WHITE);
        if (info.hardPity() > 0) {
            GuiDraw.progress(g, contentX, y + 1, Math.min(contentW, 200), 6, info.rollsSinceLegendary() / (float) info.hardPity(), GuiDraw.GOLD);
            y += 6 + 5;
        }
        Component soft = info.rollsUntilSoftPity() <= 0
                ? Component.translatable("gui.kegare.sorcerer.pity.soft_active", info.legendaryWeight(), info.baseLegendaryWeight())
                : Component.translatable("gui.kegare.sorcerer.pity.soft_in", info.rollsUntilSoftPity());
        y += GuiDraw.wrapped(g, font, soft, contentX, y, contentW, GuiDraw.WHITE);
        Component hard;
        if (info.rollsUntilHardPity() < 0) hard = Component.translatable("gui.kegare.sorcerer.pity.hard_disabled");
        else if (info.rollsUntilHardPity() == 1) hard = Component.translatable("gui.kegare.sorcerer.pity.hard_next");
        else hard = Component.translatable("gui.kegare.sorcerer.pity.hard_in", info.rollsUntilHardPity());
        y += GuiDraw.wrapped(g, font, hard, contentX, y, contentW, GuiDraw.GOLD);
        y += line(g, Component.translatable("gui.kegare.sorcerer.pity.total", info.totalRolls()), y, GuiDraw.GRAY);
        return y;
    }

    // ---------------------------------------------------------------- drawing helpers

    private int line(GuiGraphics g, Component text, int y, int color) {
        return GuiDraw.wrapped(g, font, text, contentX, y, contentW, color);
    }

    private void renderScrollbar(GuiGraphics g) {
        if (contentHeight <= contentH) return;
        int trackX = contentX + contentW + 2;
        g.fill(trackX, contentY, trackX + 3, contentY + contentH, GuiDraw.INK);
        int thumbH = Math.max(12, contentH * contentH / contentHeight);
        int thumbY = contentY + (int) ((contentH - thumbH) * (scroll / Math.max(1, contentHeight - contentH)));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF7D6A94);
        g.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, 0xFFAB98C4);
    }

    private static String tierKey(int ordinal) {
        return MaliceTier.values()[Mth.clamp(ordinal, 0, MaliceTier.values().length - 1)].translationKey();
    }

    // ---------------------------------------------------------------- input

    private Page hoverTab(double mouseX, double mouseY) {
        if (mouseY < tabY || mouseY >= tabY + tabH) return null;
        int index = (int) ((mouseX - panelX - 6) / tabW);
        return index >= 0 && index < Page.values().length && mouseX >= panelX + 6 ? Page.values()[index] : null;
    }

    private void setPage(Page p) {
        page = p;
        lastPage = p;
        scroll = 0;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - contentH));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Page p = hoverTab(mouseX, mouseY);
        if (p != null) {
            setPage(p);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 12;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KegareKeys.SORCERER.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + Page.values().length) {
            setPage(Page.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            setPage(Page.values()[(page.ordinal() + 1) % Page.values().length]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
            scroll += keyCode == GLFW.GLFW_KEY_DOWN ? 12 : -12;
            clampScroll();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
