package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.ClientLoadout;
import com.cnethuka.kegare.client.CombatPanelLayer;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.TechniqueSelect;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.CostEnvironment;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.Requirement;
import com.cnethuka.kegare.loadout.AbilityCategory;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.network.LoadoutEditPayload;
import com.cnethuka.kegare.network.SorcererInfoPayload;
import com.cnethuka.kegare.network.SorcererRequestPayload;
import com.cnethuka.kegare.network.ProgressionSnapshot;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The Sorcerer screen (key K): one window (our own art).
 * <ul>
 *   <li>Left edge: ability category tabs (Innate Technique, Shared).</li>
 *   <li>Header: the innate technique's emblem, name and rarity, and the selected category's abilities (padlocks for
 *       locked ones; a gold number marks the slot each is on).</li>
 *   <li>The large box: whatever ability is hovered (or clicked): category, cast type, current cost and cooldown,
 *       description, its special and its slot; greyed out with a lock and the level it needs while Refinement hasn't
 *       unlocked it. The right-edge buttons switch the box to Progress (Grade, Assessment, Refinement, Insight, all
 *       private), Traits, Pity or Settings (client options, saved at once: which control picks abilities in Technique
 *       Mode, effects, animations).</li>
 *   <li>Right column: your player model, Malice capacity and tier, reserve, regen.</li>
 *   <li>Bottom: the combat panel itself, live, as the loadout bar. Drag an ability onto a slot (or click it, then a
 *       slot); drag slots to swap, off the bar or right-click to empty; the ultimate slot is fixed.</li>
 * </ul>
 * Everything shown is decided by the server ({@link SorcererInfoPayload} and the sync); loadout changes are requests
 * ({@link LoadoutEditPayload}) the server checks.
 */
public final class SorcererScreen extends Screen {
    private enum View {
        TECHNIQUE("gui.kegare.sorcerer.view.technique"),
        PROGRESS("gui.kegare.sorcerer.view.progress"),
        TRAITS("gui.kegare.sorcerer.tab.traits"),
        PITY("gui.kegare.sorcerer.tab.pity"),
        SETTINGS("gui.kegare.sorcerer.view.settings");

        final String key;

        View(String key) {
            this.key = key;
        }
    }

    private static final ResourceLocation FRAME = Kegare.id("menu_frame");
    private static final ResourceLocation WELL = Kegare.id("menu_well");
    private static final ResourceLocation TAB = Kegare.id("menu_tab");
    private static final ResourceLocation TAB_SELECTED = Kegare.id("menu_tab_selected");
    static final ResourceLocation ICON_PROGRESS = Kegare.id("view_progress");
    private static final ResourceLocation ICON_TRAITS = Kegare.id("view_traits");
    private static final ResourceLocation ICON_PITY = Kegare.id("view_pity");
    private static final ResourceLocation ICON_SETTINGS = Kegare.id("view_settings");
    /** Text buttons (option values, Reset): the ink button, gold-framed when selected or hovered. */
    private static final ResourceLocation BUTTON = Kegare.id("button");
    private static final ResourceLocation BUTTON_LIT = Kegare.id("button_highlighted");
    private static final int BUTTON_H = 16;
    private static final ResourceLocation ICON_TECHNIQUE = Kegare.id("view_technique");
    private static final int TILE = CombatPanelLayer.SLOT_SIZE;
    private static final int TILE_INSET = 2;
    private static final int TAB_SIZE = 24;
    private static final long STATUS_MS = 3500;

    private static View lastView = View.TECHNIQUE;
    private static AbilityCategory lastCategory = AbilityCategory.INNATE_TECHNIQUE;

    private SorcererInfoPayload info;
    private View view = lastView;
    private AbilityCategory category = lastCategory;

    /** Clicked ability: its details stay in the box, and it is "in hand" for click-to-assign. */
    @Nullable
    private ResourceLocation pinned;
    private boolean inHand;
    /** Mouse-down on a tile or a loadout slot, waiting to see if it becomes a drag. */
    @Nullable
    private ResourceLocation pressAbility;
    private int pressSlot = -1;
    private double pressX, pressY;
    private boolean dragging;
    @Nullable
    private Component status;
    @Nullable
    private LoadoutResult statusResult;
    private long statusUntil;

    /** The big box scrolls (mouse wheel) when what it shows is taller than it. */
    private int scroll, scrollMax;
    @Nullable
    private Object scrollFor;

    /** What the info was built against: when the sync says it changed (an unlock, a reroll), ask for fresh info. */
    @Nullable
    private Object infoBasis;

    private int px, py, pw, ph;
    private int lx0, lx1, rcx, rcw, wellY0, wellY1, trayX, trayY;

    private enum Kind { CATEGORY, VIEW, TILE, RESET, OPTION }

    private record Hit(Kind kind, int x, int y, int w, int h, int index, @Nullable ResourceLocation ability, boolean unlocked) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<CombatPanelLayer.SlotRect> traySlots = List.of();

    public SorcererScreen(SorcererInfoPayload info) {
        super(Component.translatable("gui.kegare.sorcerer.title"));
        this.info = info;
    }

    /** New data from the server while open (e.g. pressing K again). */
    void update(SorcererInfoPayload info) {
        boolean hadBar = hasBar();
        this.info = info;
        if (hasBar() != hadBar) layout();
        SyncPayload data = ClientData.get();
        infoBasis = data == null ? null : basis(data);
    }

    private static Object basis(SyncPayload data) {
        return List.of(data.severed(), data.innateTechnique(), Set.copyOf(data.sharedUnlocked()), Set.copyOf(data.traits()));
    }

    /** A loadout edit was refused by the server. */
    void onLoadoutResult(LoadoutResult result) {
        if (result.translationKey() == null) return;
        status = Component.translatable(result.translationKey());
        statusResult = result;
        statusUntil = Util.getMillis() + STATUS_MS;
    }

    @Override
    protected void init() {
        layout();
    }

    /** No innate technique (or Severed) means no bar: the frame ends below the big box. */
    private boolean hasBar() {
        return !info.severed() && info.innateTechnique().isPresent();
    }

    private void layout() {
        boolean bar = hasBar();
        pw = Math.min(340, width - 56);
        ph = Math.min(bar ? 262 : 196, height - 12);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        rcw = pw >= 290 ? 84 : 70;
        rcx = px + pw - 10 - rcw;
        lx0 = px + 10;
        lx1 = rcx - 5;
        trayX = px + (pw - CombatPanelLayer.W) / 2;
        trayY = py + ph - 9 - CombatPanelLayer.H;
        wellY0 = py + 66;
        wellY1 = bar ? trayY - 13 : py + ph - 11;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC0050308);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        SyncPayload data = ClientData.get();
        if (data != null) {
            Object basis = basis(data);
            if (infoBasis == null) infoBasis = basis;
            else if (!basis.equals(infoBasis)) {
                infoBasis = basis;
                PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
            }
        }
        InnateTechnique technique = info.severed() ? null : info.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;

        renderEdgeTabs(g, technique, mouseX, mouseY);
        GuiDraw.sprite(g, FRAME, px, py, pw, ph);
        renderHeader(g, technique, data, mouseX, mouseY);
        renderRightColumn(g, technique, mouseX, mouseY);

        // The big box.
        GuiDraw.sprite(g, WELL, lx0, wellY0, lx1 - lx0, wellY1 - wellY0);
        ResourceLocation shown = view == View.TECHNIQUE ? shownAbility(mouseX, mouseY) : null;
        Object key = view == View.TECHNIQUE ? (shown != null ? shown : category) : view;
        if (!key.equals(scrollFor)) {
            scrollFor = key;
            scroll = 0;
        }
        g.enableScissor(lx0 + 3, wellY0 + 3, lx1 - 3, wellY1 - 3);
        int bx = lx0 + 7, by = wellY0 + 6 - scroll, bw = lx1 - lx0 - 16;
        int end = switch (view) {
            case TECHNIQUE -> renderDetails(g, technique, data, shown, bx, by, bw);
            case PROGRESS -> renderProgress(g, technique, data, bx, by, bw);
            case TRAITS -> renderTraits(g, bx, by, bw);
            case PITY -> renderPity(g, bx, by, bw);
            case SETTINGS -> renderSettings(g, bx, by, bw, mouseX, mouseY);
        };
        g.disableScissor();
        scrollMax = Math.max(0, end + scroll - (wellY1 - 5));
        scroll = Math.min(scroll, scrollMax);
        if (scrollMax > 0) { // a thin scrollbar on the box's right edge
            int trackY = wellY0 + 4, trackH = wellY1 - wellY0 - 8;
            int thumbH = Math.max(8, trackH * trackH / (trackH + scrollMax));
            int thumbY = trackY + (trackH - thumbH) * scroll / scrollMax;
            g.fill(lx1 - 6, trackY, lx1 - 4, trackY + trackH, 0x40FFFFFF);
            g.fill(lx1 - 6, thumbY, lx1 - 4, thumbY + thumbH, 0xC0D9B3FF);
        }

        // The loadout bar: the combat panel itself, live.
        if (technique != null && data != null) {
            int dropTarget = -1;
            if (dragging || inHand) {
                for (CombatPanelLayer.SlotRect r : traySlots) if (r.contains(mouseX, mouseY) && !r.ultimate()) dropTarget = r.index();
            }
            traySlots = CombatPanelLayer.drawPanel(g, font, data, technique, ClientLoadout.abilities(), now, trayX, trayY, dropTarget);
            renderLoadoutStatus(g, data, mouseX, mouseY);
        } else {
            traySlots = List.of();
        }

        renderOverlay(g, technique, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- edges

    private void renderEdgeTabs(GuiGraphics g, @Nullable InnateTechnique technique, int mouseX, int mouseY) {
        if (technique != null) {
            AbilityCategory[] categories = AbilityCategory.values();
            for (int i = 0; i < categories.length; i++) {
                int x = px - TAB_SIZE + 4, y = py + 12 + i * (TAB_SIZE + 3);
                boolean selected = view == View.TECHNIQUE && category == categories[i];
                GuiDraw.sprite(g, selected ? TAB_SELECTED : TAB, x, y, TAB_SIZE, TAB_SIZE);
                if (categories[i] == AbilityCategory.INNATE_TECHNIQUE) GuiDraw.emblem(g, technique, x + 4, y + 4, 16);
                else GuiDraw.sprite(g, GuiDraw.CATEGORY_SHARED, x + 4, y + 4, 16, 16);
                hits.add(new Hit(Kind.CATEGORY, x, y, TAB_SIZE, TAB_SIZE, i, null, true));
            }
        }
        View[] views = View.values();
        for (int i = 0; i < views.length; i++) {
            int x = px + pw - 4, y = py + 12 + i * (TAB_SIZE + 3);
            GuiDraw.sprite(g, view == views[i] ? TAB_SELECTED : TAB, x, y, TAB_SIZE, TAB_SIZE);
            switch (views[i]) {
                case TECHNIQUE -> GuiDraw.sprite(g, ICON_TECHNIQUE, x + 4, y + 4, 16, 16);
                case PROGRESS -> GuiDraw.sprite(g, ICON_PROGRESS, x + 4, y + 4, 16, 16);
                case TRAITS -> GuiDraw.sprite(g, ICON_TRAITS, x + 4, y + 4, 16, 16);
                case PITY -> GuiDraw.sprite(g, ICON_PITY, x + 4, y + 4, 16, 16);
                case SETTINGS -> GuiDraw.sprite(g, ICON_SETTINGS, x + 4, y + 4, 16, 16);
            }
            hits.add(new Hit(Kind.VIEW, x, y, TAB_SIZE, TAB_SIZE, i, null, true));
        }
    }

    // ---------------------------------------------------------------- header

    private void renderHeader(GuiGraphics g, @Nullable InnateTechnique technique, @Nullable SyncPayload data, int mouseX, int mouseY) {
        int ex = lx0 + 3, ey = py + 11;
        if (technique == null) {
            GuiDraw.sprite(g, GuiDraw.SEVERED_EMBLEM, ex, ey, 40, 40);
            GuiDraw.text(g, font, Component.translatable(info.severed() ? "gui.kegare.sorcerer.severed.heading" : "gui.kegare.sorcerer.innate_technique.none"),
                    ex + 48, ey + 1, 1f, info.severed() ? 0xFFE6E6E6 : GuiDraw.GRAY);
            if (info.severed()) GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.severed.short"), ex + 48, ey + 13, lx1 - ex - 50, GuiDraw.GRAY);
            return;
        }
        GuiDraw.framedEmblem(g, technique, ex, ey, 40);
        int tx = ex + 48;
        int color = 0xFF000000 | technique.rarity().color();
        Component name = technique.displayName();
        GuiDraw.text(g, font, name, tx, ey + 1, font.width(name) * 1.25f <= lx1 - tx ? 1.25f : 1f, color);
        GuiDraw.text(g, font, technique.epithet(), tx, ey + 12, 1f, GuiDraw.mix(color & 0xFFFFFF, 0xFFFFFF, 0.45f) | 0xFF000000);
        Component sub = view == View.TECHNIQUE
                ? Component.translatable("gui.kegare.sorcerer.header.category", Component.translatable(technique.rarity().translationKey()),
                        Component.translatable(category.translationKey()))
                : Component.translatable(view.key);
        GuiDraw.text(g, font, sub, tx, ey + 22, 1f, view == View.TECHNIQUE ? GuiDraw.GRAY : GuiDraw.GOLD);
        if (view != View.TECHNIQUE || data == null) return;

        // The category's abilities.
        List<Ability> tiles = category == AbilityCategory.INNATE_TECHNIQUE ? technique.abilities() : SharedAbilities.all();
        int ty = ey + 32;
        if (tiles.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("gui.kegare.loadout.category_empty"), tx, ty + 7, 1f, GuiDraw.DARK_GRAY);
        }
        for (int i = 0; i < tiles.size(); i++) {
            Ability ability = tiles.get(i);
            int x = tx + i * (TILE + 3);
            if (x + TILE > lx1) break;
            boolean ofTechnique = category == AbilityCategory.INNATE_TECHNIQUE;
            boolean unlocked = ofTechnique ? !ClientLoadout.isLocked(data, ability) : data.sharedUnlocked().contains(ability.id());
            CombatPanelLayer.slotFrame(g, x, ty, ability.id().equals(pinned) || (mouseX >= x && mouseX < x + TILE && mouseY >= ty && mouseY < ty + TILE));
            if (unlocked) {
                if (!GuiDraw.abilityIcon(g, ability, x + TILE_INSET, ty + TILE_INSET)) {
                    g.drawCenteredString(font, ability.displayName().getString().substring(0, 1), x + TILE / 2, ty + 6, GuiDraw.WHITE);
                }
                int slot = data.loadout().indexOf(Optional.of(ability.id()));
                if (slot >= 0) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.drawString(font, Integer.toString(slot + 1), x + 2, ty + 1, GuiDraw.GOLD, true);
                    g.pose().popPose();
                }
            } else {
                if (ofTechnique) GuiDraw.greyedAbilityIcon(g, ability, x + TILE_INSET, ty + TILE_INSET);
                GuiDraw.sprite(g, GuiDraw.LOCK, x + (TILE - 11) / 2, ty + (TILE - 11) / 2, 11, 11);
                if (ofTechnique) { // the level it needs, where the bar's slots have their number
                    String level = Integer.toString(ClientLoadout.lockedLevel(data, ability));
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.drawString(font, level, x + TILE - font.width(level), ty + TILE - 8, 0xFFD8D2E0, true);
                    g.pose().popPose();
                }
            }
            hits.add(new Hit(Kind.TILE, x, ty, TILE, TILE, i, ability.id(), unlocked));
        }
    }

    // ---------------------------------------------------------------- right column

    private void renderRightColumn(GuiGraphics g, @Nullable InnateTechnique technique, int mouseX, int mouseY) {
        int top = py + 11, bottom = wellY1;
        GuiDraw.sprite(g, WELL, rcx, top, rcw, bottom - top);
        int x = rcx + 6, w = rcw - 12;
        // Live from the sync (the info payload is a snapshot from when the screen opened).
        SyncPayload live = ClientData.get();
        float current = live != null ? live.current() : info.current();
        int capacity = live != null ? live.capacity() : info.capacity();
        int tier = live != null ? live.tier() : info.tier();
        Component amount = Component.translatable("gui.kegare.sorcerer.side.amount", Mth.floor(current), capacity);
        Component regen = info.regenPaused() ? Component.translatable("gui.kegare.sorcerer.side.regen_paused")
                : Component.translatable("gui.kegare.sorcerer.side.regen", GuiDraw.amount(Math.round(info.regenPerSecond() * 10f) / 10f));
        // Text may run a little past the bar, up to 2px short of the well's edge. In a small window even that's too narrow for large
        // numbers. Shrinking them would close up the zeros (the scale wouldn't land on whole pixels), so they take a second line
        // instead, and the player model gives up the height.
        int textW = rcw - 8;
        boolean stackAmount = font.width(amount) > textW;
        boolean wrapRegen = font.width(regen) > textW;
        int extraLines = (stackAmount ? 1 : 0) + (wrapRegen ? 1 : 0);
        int modelH = Mth.clamp(bottom - top - 64 - extraLines * 10, 44, 96);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, rcx + 3, top + 3, rcx + rcw - 3, top + modelH, modelH * 2 / 5, 0.0625f,
                    mouseX, mouseY, minecraft.player);
        }
        int y = top + modelH + 2;
        if (info.severed()) {
            GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.malice.severed_short"), x, y, w, GuiDraw.RED);
            return;
        }
        int color = technique != null ? technique.maliceColor() : InnateTechnique.DEFAULT_MALICE_COLOR;
        GuiDraw.text(g, font, Component.translatable("gui.kegare.sorcerer.side.malice"), x, y, 1f, GuiDraw.GRAY);
        y += 10;
        if (stackAmount) {
            GuiDraw.text(g, font, Component.translatable("gui.kegare.sorcerer.side.amount_stacked", Mth.floor(current)), x, y, 1f, GuiDraw.WHITE);
            y += 10;
            GuiDraw.text(g, font, Component.literal(Integer.toString(capacity)), x, y, 1f, GuiDraw.WHITE);
        } else {
            GuiDraw.text(g, font, amount, x, y, 1f, GuiDraw.WHITE);
        }
        y += 10;
        GuiDraw.maliceBar(g, x, y, w, capacity <= 0 ? 0f : current / capacity, color, -1f);
        y += GuiDraw.BAR_HEIGHT + 3;
        GuiDraw.text(g, font, Component.translatable(MaliceTier.values()[Mth.clamp(tier, 0, MaliceTier.values().length - 1)].translationKey()),
                x, y, 1f, GuiDraw.MALICE);
        y += 10;
        GuiDraw.wrapped(g, font, regen, x, y, textW, info.regenPaused() ? GuiDraw.RED : GuiDraw.GRAY);
    }

    // ---------------------------------------------------------------- the big box

    /** Draws the details of {@code shown} (or the overview); returns the y below the last line. */
    /** Lines an innate technique adds to one of its abilities' details, under its stats (e.g. which stage it's at). */
    @FunctionalInterface
    public interface DetailLines {
        java.util.List<Component> lines(Ability ability);
    }

    private static final java.util.List<DetailLines> DETAIL_LINES = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addDetailLines(DetailLines lines) {
        DETAIL_LINES.add(lines);
    }

    private int renderDetails(GuiGraphics g, @Nullable InnateTechnique technique, @Nullable SyncPayload data, @Nullable ResourceLocation shown,
                              int x, int y, int w) {
        if (technique == null) {
            return y + GuiDraw.wrapped(g, font, Component.translatable(info.severed() ? "gui.kegare.sorcerer.severed.desc" : "gui.kegare.sorcerer.innate_technique.none"),
                    x, y, w, GuiDraw.GRAY);
        }
        Ability ability = shown == null ? null : AbilityLookup.find(technique, shown).orElse(null);
        boolean locked = ability != null && SharedAbilities.isShared(ability.id()) && data != null && !data.sharedUnlocked().contains(ability.id());
        if (ability == null) { // overview
            y += GuiDraw.wrapped(g, font, GuiDraw.description(technique.descriptionId()), x, y, w, 0xFFD8D2E0) + 6;
            return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.overview_hint"), x, y, w, GuiDraw.DARK_GRAY);
        }
        if (locked) {
            y += GuiDraw.text(g, font, Component.translatable("gui.kegare.loadout.locked"), x, y, 1.25f, GuiDraw.GRAY) + 3;
            return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.loadout.locked.desc"), x, y, w, GuiDraw.DARK_GRAY);
        }
        // Locked by Refinement: the same card, greyed out, with a padlock and the level it needs.
        int requires = data == null ? -1 : ClientLoadout.lockedLevel(data, ability);
        boolean refinementLocked = requires >= 0;
        if (refinementLocked) GuiDraw.sprite(g, GuiDraw.LOCK, x, y, 11, 11);
        y += GuiDraw.text(g, font, ability.displayName(), refinementLocked ? x + 13 : x, y, 1.25f, refinementLocked ? GuiDraw.GRAY : GuiDraw.WHITE) + 2;
        if (refinementLocked) {
            y += GuiDraw.wrapped(g, font, Component.translatable("message.kegare.cast.refinement_locked", requires), x, y, w, GuiDraw.RED) + 1;
        }
        y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.loadout.kind",
                Component.translatable(AbilityLookup.category(ability.id()).translationKey()),
                Component.translatable(ability.castType().translationKey())), x, y, w, refinementLocked ? GuiDraw.DARK_GRAY : GuiDraw.GRAY) + 1;
        SorcererInfoPayload.AbilityInfo abilityInfo = abilityInfo(ability.id());
        SyncPayload.AbilityCost cost = CostTooltip.cost(ability.id());
        float costNow = cost != null ? cost.cost() : abilityInfo != null ? abilityInfo.cost() : 0f;
        float upkeep = cost != null ? cost.upkeepPerSecond() : abilityInfo != null ? abilityInfo.upkeepPerSecond() : 0f;
        String cooldown = abilityInfo != null ? GuiDraw.seconds(abilityInfo.cooldownTicks()) : "?";
        Component stats = (ability.castType() == CastType.CHANNELED || ability.castType() == CastType.TOGGLE) && upkeep > 0
                ? Component.translatable("gui.kegare.sorcerer.ability.stats_upkeep", GuiDraw.amount(round(costNow)), GuiDraw.amount(round(upkeep)), cooldown)
                : Component.translatable("gui.kegare.sorcerer.ability.stats", GuiDraw.amount(round(costNow)), cooldown);
        y += GuiDraw.wrapped(g, font, stats, x, y, w, refinementLocked ? GuiDraw.DARK_GRAY : GuiDraw.MALICE);
        if (data != null && !refinementLocked && !SharedAbilities.isShared(ability.id())) { // surroundings (innate technique abilities only)
            CostEnvironment env = data.environment();
            for (CostEnvironment.Factor f : env.factors()) {
                y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.cost.factor", GuiDraw.multiplier(f.multiplier()),
                        Component.translatable(f.translationKey())), x, y, w, f.multiplier() < 1f ? GuiDraw.AQUA : GuiDraw.RED);
            }
        }
        if (!refinementLocked) {
            for (DetailLines lines : DETAIL_LINES) {
                for (Component line : lines.lines(ability)) y += GuiDraw.wrapped(g, font, line, x, y, w, GuiDraw.MALICE);
            }
        }
        y += 4;
        y += GuiDraw.wrapped(g, font, GuiDraw.description(ability.descriptionId()), x, y, w, refinementLocked ? GuiDraw.GRAY : 0xFFD8D2E0) + 4;

        // Its special.
        Optional<Ability> special = ability.special();
        if (special.isPresent()) {
            Ability sp = special.get();
            SorcererInfoPayload.SpecialInfo spInfo = abilityInfo != null ? abilityInfo.special().orElse(null) : null;
            int spRequires = data == null ? -1 : ClientLoadout.lockedLevel(data, sp);
            boolean spLocked = spRequires >= 0;
            // The special's own icon when it has art (with the R badge on its corner), else just the badge; a padlock while it's locked.
            boolean icon = !spLocked && GuiDraw.abilityIcon(g, sp, x, y - 2);
            if (spLocked) GuiDraw.sprite(g, GuiDraw.LOCK, x, y - 1, 11, 11);
            else if (icon) GuiDraw.specialBadge(g, x + 9, y + 7, true);
            else GuiDraw.specialBadge(g, x, y - 1, true);
            int ix = spLocked ? 13 : icon ? 20 : 12;
            Component line = spInfo != null && spInfo.upkeepPerSecond() > 0
                    ? Component.translatable("gui.kegare.sorcerer.special.line_upkeep", sp.displayName(), Component.translatable(sp.castType().translationKey()),
                            GuiDraw.amount(spInfo.cost()), GuiDraw.amount(spInfo.upkeepPerSecond()))
                    : Component.translatable("gui.kegare.sorcerer.special.line", sp.displayName(), Component.translatable(sp.castType().translationKey()),
                            GuiDraw.amount(spInfo != null ? spInfo.cost() : 0f));
            int top = y;
            y += GuiDraw.wrapped(g, font, line, x + ix, y, w - ix, !spLocked && (spInfo == null || spInfo.available()) ? GuiDraw.GOLD : GuiDraw.GRAY);
            if (spLocked) {
                y += GuiDraw.wrapped(g, font, Component.translatable("message.kegare.cast.refinement_locked", spRequires), x + ix, y, w - ix, GuiDraw.RED);
            }
            y += GuiDraw.wrapped(g, font, GuiDraw.description(sp.descriptionId()), x + ix, y, w - ix, GuiDraw.DARK_GRAY);
            for (Requirement requirement : sp.requirements()) {
                boolean met = spInfo != null && spInfo.available();
                y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.special.requires", requirement.description()), x + ix, y, w - ix,
                        met ? 0xFF7CFC8A : GuiDraw.RED);
            }
            y = Math.max(y, icon ? top + 16 : y) + 3;
        } else if (!SharedAbilities.isShared(ability.id()) || ability.special().isEmpty()) {
            GuiDraw.specialBadge(g, x, y - 1, false);
            y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.special.none"), x + 12, y, w - 12, GuiDraw.DARK_GRAY) + 3;
        }

        // Where it is.
        if (data != null && !refinementLocked) {
            int slot = data.loadout().indexOf(Optional.of(ability.id()));
            boolean ultimate = technique.ultimate().map(u -> u.id().equals(ability.id())).orElse(false);
            Component where = ultimate ? Component.translatable("gui.kegare.loadout.ultimate_fixed")
                    : slot >= 0 ? Component.translatable("gui.kegare.loadout.in_slot", slot + 1)
                    : Component.translatable("gui.kegare.loadout.not_slotted");
            y += GuiDraw.wrapped(g, font, where, x, y, w, slot >= 0 ? GuiDraw.GOLD : GuiDraw.DARK_GRAY);
        }
        return y;
    }

    /** Grade, Assessment against what the next step asks for, Refinement on the current technique, and Insight. All private. */
    private int renderProgress(GuiGraphics g, @Nullable InnateTechnique technique, @Nullable SyncPayload data, int x, int y, int w) {
        if (data == null) return y;
        ProgressionSnapshot p = data.progression();
        Grade grade = Grade.byOrdinal(p.grade());
        Optional<Grade> next = grade.next();

        y += GuiDraw.text(g, font, Component.translatable(grade.translationKey()), x, y, 1.25f, GuiDraw.GOLD) + 3;
        if (next.isEmpty()) {
            y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.progress.top"), x, y, w, GuiDraw.GRAY);
        } else {
            Component nextName = Component.translatable(next.get().translationKey());
            if (p.gradeXpNeeded() > 0) {
                GuiDraw.progress(g, x, y, w, 6, p.gradeXp() / p.gradeXpNeeded(), GuiDraw.GOLD & 0xFFFFFF);
                y += 8;
                y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.progress.xp", Mth.floor(p.gradeXp()), Mth.floor(p.gradeXpNeeded()), nextName), x, y, w, GuiDraw.GRAY);
            } else {
                y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.progress.assessment_only", nextName), x, y, w, GuiDraw.GRAY);
            }
        }
        if (p.assessment().isPresent()) y = renderAssessment(g, p.assessment().get(), x, y + 3, w);
        y += 5;

        y += GuiDraw.text(g, font, Component.translatable("gui.kegare.sorcerer.progress.refinement"), x, y, 1f, 0xFFD8D2E0) + 1;
        if (data.severed() || technique == null) {
            y += GuiDraw.wrapped(g, font, Component.translatable(data.severed() ? "gui.kegare.sorcerer.progress.refinement.severed" : "gui.kegare.sorcerer.progress.refinement.none"), x, y, w, GuiDraw.DARK_GRAY);
        } else {
            boolean top = p.refinementXpNeeded() <= 0;
            GuiDraw.progress(g, x, y, w, 6, top ? 1f : p.refinementXp() / p.refinementXpNeeded(), technique.maliceColor());
            y += 8;
            y += GuiDraw.wrapped(g, font, top ? Component.translatable("gui.kegare.sorcerer.progress.refinement.top", p.refinement())
                    : Component.translatable("gui.kegare.sorcerer.progress.refinement.level", p.refinement(), p.maxRefinement(), Mth.floor(p.refinementXp()), Mth.floor(p.refinementXpNeeded())), x, y, w, GuiDraw.GRAY);
        }
        if (!data.severed()) y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.progress.peak", p.peakRefinement()), x, y, w, GuiDraw.DARK_GRAY);
        y += 5;

        y += GuiDraw.text(g, font, Component.translatable("gui.kegare.sorcerer.progress.insight"), x, y, 1f, 0xFFD8D2E0) + 1;
        return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.progress.insight.values",
                Component.translatable(InsightCategory.DOMINION.translationKey()), p.dominion(),
                Component.translatable(InsightCategory.WARD.translationKey()), p.ward(),
                Component.translatable(InsightCategory.OUTPUT.translationKey()), p.output()), x, y, w, GuiDraw.GRAY);
    }

    /** Assessment against the requirement, each line green once it's met. Two columns where the box is wide enough. */
    private int renderAssessment(GuiGraphics g, ProgressionSnapshot.Assessment a, int x, int y, int w) {
        y += GuiDraw.text(g, font, Component.translatable("gui.kegare.sorcerer.progress.assessment"), x, y, 1f, 0xFFD8D2E0) + 1;
        List<Component> lines = new ArrayList<>();
        List<Boolean> met = new ArrayList<>();
        lines.add(Component.translatable("gui.kegare.sorcerer.progress.assessment.average", Math.round(a.average()), Math.round(a.averageNeeded())));
        met.add(a.fights() > 0 && a.average() + 1.0e-4f >= a.averageNeeded());
        lines.add(Component.translatable(a.severed() ? "gui.kegare.sorcerer.progress.assessment.melee" : "gui.kegare.sorcerer.progress.assessment.technique",
                Math.round(a.technique()), Math.round(a.techniqueNeeded())));
        met.add(a.fights() > 0 && a.technique() + 1.0e-4f >= a.techniqueNeeded());
        lines.add(Component.translatable("gui.kegare.sorcerer.progress.assessment.fights", a.fights(), a.fightsNeeded()));
        met.add(a.fights() >= a.fightsNeeded());
        lines.add(Component.translatable("gui.kegare.sorcerer.progress.assessment.opponents", a.opponents(), a.opponentsNeeded()));
        met.add(a.opponents() >= a.opponentsNeeded());
        if (!a.severed()) { // they skip this one
            lines.add(Component.translatable("gui.kegare.sorcerer.progress.assessment.peak", a.peak(), a.peakNeeded()));
            met.add(a.peak() >= a.peakNeeded());
        }
        int column = w / 2;
        boolean twoColumns = lines.stream().allMatch(line -> font.width(line) <= column - 2);
        int perRow = twoColumns ? 2 : 1;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + (i % perRow) * column, y, met.get(i) ? 0xFF7CFC8A : GuiDraw.GRAY, true);
            if (i % perRow == perRow - 1 || i == lines.size() - 1) y += font.lineHeight + 1;
        }
        return y;
    }

    private int renderTraits(GuiGraphics g, int x, int y, int w) {
        if (info.traits().isEmpty()) {
            return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.traits.none"), x, y, w, GuiDraw.GRAY);
        }
        for (ResourceLocation id : info.traits()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null) continue;
            Component name = trait.isUnique() ? Component.translatable("gui.kegare.sorcerer.traits.unique", trait.displayName()) : trait.displayName();
            y += GuiDraw.text(g, font, name, x, y, 1f, GuiDraw.AQUA) + 1;
            y += GuiDraw.wrapped(g, font, GuiDraw.description(trait.descriptionId()), x + 6, y, w - 6, GuiDraw.GRAY) + 5;
        }
        return y;
    }

    private int renderPity(GuiGraphics g, int x, int y, int w) {
        y += GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.pity.count", info.rollsSinceLegendary()), x, y, w, GuiDraw.WHITE) + 1;
        if (info.hardPity() > 0) {
            GuiDraw.progress(g, x, y, w, 6, info.rollsSinceLegendary() / (float) info.hardPity(), GuiDraw.GOLD);
            y += 10;
        }
        Component soft = info.rollsUntilSoftPity() <= 0
                ? Component.translatable("gui.kegare.sorcerer.pity.soft_active", info.legendaryWeight(), info.baseLegendaryWeight())
                : Component.translatable("gui.kegare.sorcerer.pity.soft_in", info.rollsUntilSoftPity());
        y += GuiDraw.wrapped(g, font, soft, x, y, w, GuiDraw.WHITE) + 1;
        Component hard;
        if (info.rollsUntilHardPity() < 0) hard = Component.translatable("gui.kegare.sorcerer.pity.hard_disabled");
        else if (info.rollsUntilHardPity() == 1) hard = Component.translatable("gui.kegare.sorcerer.pity.hard_next");
        else hard = Component.translatable("gui.kegare.sorcerer.pity.hard_in", info.rollsUntilHardPity());
        y += GuiDraw.wrapped(g, font, hard, x, y, w, GuiDraw.GOLD) + 1;
        return y + GuiDraw.wrapped(g, font, Component.translatable("gui.kegare.sorcerer.pity.total", info.totalRolls()), x, y, w, GuiDraw.GRAY);
    }

    // ---------------------------------------------------------------- settings (client config, saved at once)

    private static final String[] SELECT_VALUES = {"gui.kegare.settings.number_keys", "gui.kegare.settings.scroll_wheel"};
    private static final String[] VFX_VALUES = {"gui.kegare.settings.vfx.full", "gui.kegare.settings.vfx.reduced", "gui.kegare.settings.vfx.vanilla"};
    private static final String[] ON_OFF = {"gui.kegare.settings.on", "gui.kegare.settings.off"};

    private int renderSettings(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        TechniqueSelect select = KegareClientConfig.TECHNIQUE_SELECT.get();
        y = optionRow(g, 0, "gui.kegare.settings.technique_select", SELECT_VALUES, select.ordinal(), x, y, w, mouseX, mouseY);
        y += GuiDraw.wrapped(g, font, Component.translatable(select == TechniqueSelect.NUMBER_KEYS
                ? "gui.kegare.settings.technique_select.number_keys" : "gui.kegare.settings.technique_select.scroll_wheel"), x, y, w, GuiDraw.DARK_GRAY) + 8;
        y = optionRow(g, 1, "gui.kegare.settings.vfx", VFX_VALUES, KegareClientConfig.VFX_QUALITY.get().ordinal(), x, y, w, mouseX, mouseY) + 5;
        y = optionRow(g, 2, "gui.kegare.settings.first_person", ON_OFF, KegareClientConfig.FIRST_PERSON_ANIMATIONS.get() ? 0 : 1, x, y, w, mouseX, mouseY) + 5;
        y = optionRow(g, 4, "gui.kegare.settings.animation_fades", ON_OFF, KegareClientConfig.ANIMATION_FADES.get() ? 0 : 1, x, y, w, mouseX, mouseY) + 5;
        y = optionRow(g, 3, "gui.kegare.settings.reveal", ON_OFF, KegareClientConfig.REVEAL_ANIMATION.get() ? 0 : 1, x, y, w, mouseX, mouseY) + 5;
        y = optionRow(g, 5, "gui.kegare.settings.reveal_always_full", ON_OFF, KegareClientConfig.REVEAL_ALWAYS_FULL.get() ? 0 : 1, x, y, w, mouseX, mouseY) + 5;
        return optionRow(g, 6, "gui.kegare.settings.reveal_reduced_motion", ON_OFF, KegareClientConfig.REVEAL_REDUCED_MOTION.get() ? 0 : 1, x, y, w, mouseX, mouseY);
    }

    /** A label, then one button per value (the current one lit gold). Returns the y below the row. */
    private int optionRow(GuiGraphics g, int setting, String labelKey, String[] values, int current, int x, int y, int w, int mouseX, int mouseY) {
        y += GuiDraw.wrapped(g, font, Component.translatable(labelKey), x, y, w, 0xFFD8D2E0) + 2;
        int bx = x;
        for (int v = 0; v < values.length; v++) {
            Component label = Component.translatable(values[v]);
            int bw = font.width(label) + 12;
            if (bx > x && bx + bw > x + w) { // wrap
                bx = x;
                y += BUTTON_H + 3;
            }
            int top = Math.max(y, wellY0 + 3), bottom = Math.min(y + BUTTON_H, wellY1 - 3); // the part not scrolled out of the box
            boolean hover = bottom > top && mouseX >= bx && mouseX < bx + bw && mouseY >= top && mouseY < bottom;
            boolean selected = v == current;
            GuiDraw.sprite(g, selected ? BUTTON_LIT : BUTTON, bx, y, bw, BUTTON_H);
            GuiDraw.centered(g, font, label, bx + bw / 2, y + 4, 1f, selected ? GuiDraw.GOLD : hover ? GuiDraw.WHITE : GuiDraw.GRAY);
            if (bottom > top) hits.add(new Hit(Kind.OPTION, bx, top, bw, bottom - top, setting * 10 + v, null, true));
            bx += bw + 3;
        }
        return y + BUTTON_H + 3;
    }

    private void applyOption(int setting, int value) {
        switch (setting) {
            case 0 -> KegareClientConfig.TECHNIQUE_SELECT.set(TechniqueSelect.values()[value]);
            case 1 -> KegareClientConfig.VFX_QUALITY.set(VfxQuality.values()[value]);
            case 2 -> KegareClientConfig.FIRST_PERSON_ANIMATIONS.set(value == 0);
            case 3 -> KegareClientConfig.REVEAL_ANIMATION.set(value == 0);
            case 4 -> KegareClientConfig.ANIMATION_FADES.set(value == 0);
            case 5 -> KegareClientConfig.REVEAL_ALWAYS_FULL.set(value == 0);
            case 6 -> KegareClientConfig.REVEAL_REDUCED_MOTION.set(value == 0);
            default -> {
                return;
            }
        }
        KegareClientConfig.SPEC.save();
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    // ---------------------------------------------------------------- loadout status, overlay

    private void renderLoadoutStatus(GuiGraphics g, SyncPayload data, int mouseX, int mouseY) {
        int y = wellY1 + 2;
        Component line;
        int color;
        boolean repeatsCombat = statusResult == LoadoutResult.IN_COMBAT && data.inCombat();
        if (status != null && Util.getMillis() < statusUntil && !repeatsCombat) {
            line = status;
            color = GuiDraw.RED;
        } else if (data.inCombat()) {
            line = Component.translatable("message.kegare.loadout.in_combat");
            color = GuiDraw.RED;
        } else {
            line = Component.translatable(inHand ? "gui.kegare.sorcerer.hint.in_hand" : "gui.kegare.sorcerer.hint");
            color = inHand ? GuiDraw.GOLD : 0xFF6B6276;
        }
        // Reset, at the right end of the status row (the nine-slot bar leaves no room beside it).
        Component reset = Component.translatable("gui.kegare.loadout.reset_short");
        int bw = font.width(reset) + 10;
        int bx = px + pw - 10 - bw;
        int by = wellY1 + 1;

        // The line: centered on the panel while that clears Reset, else in the space left of it.
        int left = px + 6, right = bx - 3;
        float scale = font.width(line) > right - left ? 0.75f : 1f;
        int center = px + pw / 2;
        if (center + Mth.ceil(font.width(line) * scale / 2f) > right) center = (left + right) / 2;
        GuiDraw.centered(g, font, line, center, y, scale, color);

        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_H;
        GuiDraw.sprite(g, hover ? BUTTON_LIT : BUTTON, bx, by, bw, BUTTON_H);
        GuiDraw.centered(g, font, reset, bx + bw / 2, by + 4, 1f, hover ? GuiDraw.WHITE : GuiDraw.GRAY);
        hits.add(new Hit(Kind.RESET, bx, by, bw, BUTTON_H, 0, null, true));
    }

    private void renderOverlay(GuiGraphics g, @Nullable InnateTechnique technique, int mouseX, int mouseY) {
        if (technique != null && dragging && pressAbility != null) {
            AbilityLookup.find(technique, pressAbility).ifPresent(ability -> {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                CombatPanelLayer.slotFrame(g, mouseX - TILE / 2, mouseY - TILE / 2, true);
                GuiDraw.abilityIcon(g, ability, mouseX - TILE / 2 + TILE_INSET, mouseY - TILE / 2 + TILE_INSET);
                g.pose().popPose();
            });
            return;
        }
        Hit hit = hitAt(mouseX, mouseY);
        if (hit != null && (hit.kind == Kind.CATEGORY || hit.kind == Kind.VIEW || hit.kind == Kind.RESET)) {
            Component label = switch (hit.kind) {
                case CATEGORY -> Component.translatable(AbilityCategory.values()[hit.index].translationKey());
                case VIEW -> Component.translatable(View.values()[hit.index].key);
                default -> Component.translatable("gui.kegare.loadout.reset.desc");
            };
            g.renderTooltip(font, label, mouseX, mouseY);
            return;
        }
        for (CombatPanelLayer.SlotRect r : traySlots) {
            if (!r.contains(mouseX, mouseY)) continue;
            List<Component> lines = new ArrayList<>();
            Ability ability = technique == null ? null : r.ultimate() ? technique.ultimate().orElse(null) : ClientLoadout.abilities().get(r.index());
            lines.add(ability != null ? ability.displayName()
                    : Component.translatable(r.ultimate() ? "gui.kegare.sorcerer.ability.ultimate_locked" : "hud.kegare.empty_slot"));
            SyncPayload data = ClientData.get();
            int requires = ability != null && data != null ? ClientLoadout.lockedLevel(data, ability) : -1;
            String hint = !r.ultimate() ? (ability != null ? "gui.kegare.loadout.slot_hint" : "gui.kegare.loadout.empty_slot_hint")
                    : ability != null ? "gui.kegare.loadout.ultimate_fixed" : "gui.kegare.sorcerer.ability.ultimate_locked.desc";
            lines.add(requires >= 0 ? Component.translatable("message.kegare.cast.refinement_locked", requires).withStyle(ChatFormatting.RED)
                    : Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Hit hit = hitAt(mouseX, mouseY);
        CombatPanelLayer.SlotRect slot = slotAt(mouseX, mouseY);
        if (button == 1) {
            if (slot != null && !slot.ultimate()) send(LoadoutService.Action.CLEAR, slot.index(), 0, null);
            inHand = false;
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (hit != null) {
            switch (hit.kind) {
                case CATEGORY -> {
                    category = AbilityCategory.values()[hit.index];
                    lastCategory = category;
                    setView(View.TECHNIQUE);
                    pinned = null;
                    inHand = false;
                }
                case VIEW -> setView(View.values()[hit.index]);
                case TILE -> {
                    if (hit.unlocked) press(hit.ability, -1, mouseX, mouseY);
                    else pinned = hit.ability; // its "locked" card
                }
                case RESET -> send(LoadoutService.Action.RESET, 0, 0, null);
                case OPTION -> applyOption(hit.index / 10, hit.index % 10);
            }
            return true;
        }
        if (slot != null) {
            if (inHand && pinned != null) {
                send(LoadoutService.Action.SET, slot.index(), 0, pinned); // the ultimate's index: the server refuses it
                inHand = false;
            } else if (!slot.ultimate()) {
                Ability ability = ClientLoadout.abilities().size() > slot.index() ? ClientLoadout.abilities().get(slot.index()) : null;
                press(ability != null ? ability.id() : null, slot.index(), mouseX, mouseY);
            }
            return true;
        }
        inHand = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && pressAbility != null) {
            if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > 3) dragging = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || (pressAbility == null && pressSlot < 0)) return super.mouseReleased(mouseX, mouseY, button);
        ResourceLocation ability = pressAbility;
        int fromSlot = pressSlot;
        pressAbility = null;
        pressSlot = -1;
        if (!dragging) { // a click: pick up a tile (click it again to put it down)
            if (fromSlot < 0 && ability != null) {
                boolean same = ability.equals(pinned) && inHand;
                pinned = ability;
                inHand = !same;
            }
            return true;
        }
        dragging = false;
        CombatPanelLayer.SlotRect target = slotAt(mouseX, mouseY);
        if (fromSlot < 0) {
            if (target != null) send(LoadoutService.Action.SET, target.index(), 0, ability);
        } else if (target != null) {
            if (target.index() != fromSlot) send(LoadoutService.Action.SWAP, fromSlot, target.index(), null);
        } else if (ability != null) {
            send(LoadoutService.Action.CLEAR, fromSlot, 0, null); // dragged off the bar
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollMax > 0 && mouseX >= lx0 && mouseX < lx1 && mouseY >= wellY0 && mouseY < wellY1) {
            scroll = Mth.clamp(scroll - (int) Math.round(scrollY * 12), 0, scrollMax);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KegareKeys.SORCERER.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + View.values().length) { // 1-5: the views
            setView(View.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            setView(View.values()[(view.ordinal() + 1) % View.values().length]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private void setView(View v) {
        view = v;
        lastView = v;
    }

    private void press(@Nullable ResourceLocation ability, int slot, double mx, double my) {
        pressAbility = ability;
        pressSlot = slot;
        pressX = mx;
        pressY = my;
        dragging = false;
    }

    /** The ability the big box is about: the hovered tile or bar slot, else the clicked one. */
    @Nullable
    private ResourceLocation shownAbility(int mouseX, int mouseY) {
        if (dragging && pressAbility != null) return pressAbility;
        ResourceLocation hovered = hoveredAbility(mouseX, mouseY);
        return hovered != null ? hovered : pinned;
    }

    @Nullable
    private ResourceLocation hoveredAbility(int mouseX, int mouseY) {
        Hit hit = hitAt(mouseX, mouseY);
        if (hit != null && hit.kind == Kind.TILE) return hit.ability;
        CombatPanelLayer.SlotRect slot = slotAt(mouseX, mouseY);
        InnateTechnique technique = info.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        if (slot != null && technique != null) {
            Ability ability = slot.ultimate() ? technique.ultimate().orElse(null)
                    : ClientLoadout.abilities().size() > slot.index() ? ClientLoadout.abilities().get(slot.index()) : null;
            return ability == null ? null : ability.id();
        }
        return null;
    }

    @Nullable
    private SorcererInfoPayload.AbilityInfo abilityInfo(ResourceLocation id) {
        for (SorcererInfoPayload.AbilityInfo a : info.abilities()) if (a.id().equals(id)) return a;
        for (SorcererInfoPayload.AbilityInfo a : info.shared()) if (a.id().equals(id)) return a;
        return null;
    }

    @Nullable
    private Hit hitAt(double mx, double my) {
        for (Hit hit : hits) if (hit.contains(mx, my)) return hit;
        return null;
    }

    @Nullable
    private CombatPanelLayer.SlotRect slotAt(double mx, double my) {
        for (CombatPanelLayer.SlotRect r : traySlots) if (r.contains(mx, my)) return r;
        return null;
    }

    private static void send(LoadoutService.Action action, int slot, int other, @Nullable ResourceLocation ability) {
        PacketDistributor.sendToServer(new LoadoutEditPayload(action, slot, other, Optional.ofNullable(ability)));
    }

    private static float round(float value) {
        return Math.round(value * 10f) / 10f;
    }
}
