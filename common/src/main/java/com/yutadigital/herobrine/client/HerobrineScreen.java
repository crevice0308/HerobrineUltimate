package com.yutadigital.herobrine.client;

import com.yutadigital.herobrine.core.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Native widgets, scissored scroll areas and live inventory rendering, never a background screenshot. */
public final class HerobrineScreen extends Screen {
    private static final int CYAN = 0xFF58EFFF, PURPLE = 0xFFCB79FF;
    private static final int TEXT = 0xFFE3F6FF, MUTED = 0xFF94AABF;
    private final List<Placed> widgets = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private Ability category = Ability.HEROBRINE;
    private int left, top, panelWidth, panelHeight, bodyTop, bodyBottom, navWidth;
    private int contentX, contentWidth, previewX, previewWidth, contentHeight;
    private int navScroll, contentScroll;
    private long revision = -1;
    private boolean dirty;
    private AbstractWidget pointerCapture;

    public HerobrineScreen() { super(Component.literal("Herobrine Ultimate")); }

    @Override protected void init() {
        left = 8;
        top = 8;
        panelWidth = Math.max(100, width - 16);
        panelHeight = Math.max(90, height - 16);
        bodyTop = top + 48;
        bodyBottom = top + panelHeight - 49;
        navWidth = Mth.clamp(panelWidth / 5, 88, 132);
        previewWidth = panelWidth >= 620 && bodyBottom - bodyTop > 150 ? Mth.clamp(panelWidth / 5, 128, 180) : 0;
        previewX = left + navWidth + 14;
        contentX = previewX + (previewWidth == 0 ? 0 : previewWidth + 8);
        contentWidth = Math.max(64, left + panelWidth - 8 - contentX);
        rebuild();
    }

    private void rebuild() {
        int focused = -1;
        for (int i = 0; i < widgets.size(); i++) if (widgets.get(i).widget == getFocused()) focused = i;
        clearWidgets();
        widgets.clear();
        notes.clear();
        dirty = false;
        revision = HerobrineClient.revision();
        int row = 0;
        for (Ability ability : Ability.values()) {
            NeonButton button = new NeonButton(left + 7, bodyTop + row * 27, navWidth, 24,
                    () -> label(ability), () -> category == ability, () -> {
                category = ability;
                contentScroll = 0;
                dirty = true;
            });
            button.setTooltip(Tooltip.create(tr("ability." + id(ability) + ".description")));
            add(button, Area.NAV, row++ * 27);
        }
        int y = 5;
        y = note(label(category), y, CYAN) + 6;
        y = note(tr("ability." + id(category) + ".description"), y, MUTED) + 9;
        NeonButton toggle = new NeonButton(contentX + 7, bodyTop + y, contentWidth - 14, 26,
                () -> Component.translatable("herobrineultimate.ui.ability_toggle", label(category),
                        tr(HerobrineClient.settings().selected(category) ? "ui.on" : "ui.off")),
                () -> HerobrineClient.settings().selected(category), () -> HerobrineClient.toggle(category));
        toggle.active = HerobrineClient.allowed();
        toggle.setTooltip(Tooltip.create(tr("ui.toggle_hint")));
        add(toggle, Area.CONTENT, y);
        y += 34;
        y = note(tr("ability." + id(category) + ".details"), y, TEXT) + 10;
        switch (category) {
            case LIGHTNING -> {
                y = action(y, "ui.strike", "lightning", false);
                y = action(y, "ui.strike_destructive", "lightning", true, "", true);
            }
            case COMBAT -> {
                y = slider(y, "damage", 1000);
                y = action(y, "ui.attack", "attack", false);
                y = action(y, "ui.instant", "attack", true, "", true);
            }
            case TELEPORT -> {
                y = action(y, "ui.teleport", "teleport", false);
                y = action(y, "ui.save", "save", false);
                y = action(y, "ui.return", "return", false);
            }
            case MOB -> {
                y = action(y, "ui.summon.wolf", "summon", false, "wolf", false);
                y = action(y, "ui.summon.cat", "summon", false, "cat", false);
                y = action(y, "ui.summon.parrot", "summon", false, "parrot", false);
                y = action(y, "ui.summon.iron_golem", "summon", false, "iron_golem", false);
                y = action(y, "ui.rally", "rally", false);
                y = action(y, "ui.dismiss", "dismiss", false);
            }
            case WORLD -> {
                y = action(y, "ui.day", "day", false);
                y = action(y, "ui.night", "night", false);
                y = action(y, "ui.clear", "clear", false);
                y = action(y, "ui.rain", "rain", false);
                y = action(y, "ui.thunder", "thunder", false);
                y = action(y, "ui.terrain", "terrain", true);
                y = note(tr("ui.backup"), y + 5, PURPLE);
            }
            case MOTION -> {
                y = slider(y, "move", 10);
                y = slider(y, "mine", 100);
                y = slider(y, "attack", 10);
                y = note(tr("ui.multiplier_hint"), y + 5, MUTED);
            }
            case HEROBRINE -> y = note(tr("ui.identity"), y, PURPLE);
            case EFFECTS -> y = note(tr("ui.effect_cost"), y, MUTED);
            default -> { }
        }
        contentHeight = y + 8;
        int footerY = top + panelHeight - 31;
        int buttonWidth = Math.max(26, (panelWidth - 26) / 3);
        NeonButton master = new NeonButton(left + 7, footerY, buttonWidth, 23,
                () -> shortcut(HerobrineClient.settings().master ? "ui.master_on" : "ui.master_off", "master"),
                () -> HerobrineClient.settings().master, HerobrineClient::master);
        master.active = HerobrineClient.allowed();
        add(master, Area.FIXED, 0);
        NeonButton save = new NeonButton(left + 13 + buttonWidth, footerY, buttonWidth, 23,
                () -> shortcut("ui.save_short", "save"), () -> false, () -> HerobrineClient.action("save"));
        save.active = HerobrineClient.allowed() && HerobrineClient.settings().enabled(Ability.TELEPORT);
        save.setTooltip(Tooltip.create(tr("ui.action_hint")));
        add(save, Area.FIXED, 0);
        NeonButton restore = new NeonButton(left + 19 + buttonWidth * 2, footerY, buttonWidth, 23,
                () -> shortcut("ui.return_short", "return"), () -> false, () -> HerobrineClient.action("return"));
        restore.active = save.active;
        restore.setTooltip(Tooltip.create(tr("ui.action_hint")));
        add(restore, Area.FIXED, 0);
        NeonButton glow = new NeonButton(left + panelWidth - 48, top + 23, 18, 16,
                () -> Component.literal("G"), HerobrineClient::panelGlow, HerobrineClient::togglePanelGlow);
        glow.setTooltip(Tooltip.create(Component.translatable("herobrineultimate.ui.glow",
                tr(HerobrineClient.panelGlow() ? "ui.on" : "ui.off"))));
        add(glow, Area.FIXED, 0);
        NeonButton controls = new NeonButton(left + panelWidth - 27, top + 23, 18, 16,
                () -> Component.literal("K"), () -> false,
                () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
        controls.setTooltip(Tooltip.create(tr("ui.controls")));
        add(controls, Area.FIXED, 0);
        add(new NeonButton(left + panelWidth - 26, top + 6, 18, 14,
                () -> Component.literal("×"), () -> false, this::onClose), Area.FIXED, 0);
        clampScroll();
        placeWidgets();
        if (focused >= 0 && focused < widgets.size()) {
            Placed previousFocus = widgets.get(focused);
            reveal(previousFocus);
            setFocused(previousFocus.widget);
        }
    }

    private int note(Component message, int y, int color) {
        List<FormattedCharSequence> lines = font.split(message, Math.max(32, contentWidth - 18));
        notes.add(new Note(lines, y, color));
        return y + lines.size() * 11;
    }

    private int action(int y, String label, String operation, boolean confirm) {
        return action(y, label, operation, confirm, "", false);
    }

    private int action(int y, String label, String operation, boolean confirm, String key, boolean flag) {
        NeonButton button = new NeonButton(contentX + 7, bodyTop + y, contentWidth - 14, 24,
                () -> actionLabel(label, operation), () -> false, () -> {
            if (confirm) {
                minecraft.setScreen(new ConfirmScreen(accepted -> {
                    if (accepted) HerobrineClient.request(operation, key, flag, true);
                    minecraft.setScreen(this);
                }, tr("ui.confirm"), tr("ui.confirm." + operation)));
            } else HerobrineClient.request(operation, key, flag, false);
        });
        button.active = HerobrineClient.allowed();
        button.setTooltip(Tooltip.create(tr("ui.action_hint")));
        add(button, Area.CONTENT, y);
        return y + 29;
    }

    private int slider(int y, String key, double maximum) {
        BoostSlider slider = new BoostSlider(contentX + 7, bodyTop + y, contentWidth - 14, key, maximum);
        slider.active = HerobrineClient.allowed();
        slider.setTooltip(Tooltip.create(tr("ui.slider_hint")));
        add(slider, Area.CONTENT, y);
        return y + 33;
    }

    private void add(AbstractWidget widget, Area area, int baseY) {
        addWidget(widget);
        widgets.add(new Placed(widget, area, baseY));
    }

    private void clampScroll() {
        int viewport = Math.max(1, bodyBottom - bodyTop);
        navScroll = Mth.clamp(navScroll, 0, Math.max(0, Ability.values().length * 27 - viewport));
        contentScroll = Mth.clamp(contentScroll, 0, Math.max(0, contentHeight - viewport));
    }

    private void placeWidgets() {
        for (Placed placed : widgets) {
            if (placed.area == Area.FIXED) continue;
            AbstractWidget widget = placed.widget;
            widget.setY(bodyTop + placed.baseY - (placed.area == Area.NAV ? navScroll : contentScroll));
            widget.visible = widget.getY() < bodyBottom && widget.getY() + widget.getHeight() > bodyTop;
        }
    }

    @Override public void tick() {
        if ((dirty || revision != HerobrineClient.revision()) && !isDragging()) rebuild();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x78000818);
        frame(g, left, top, panelWidth, panelHeight, true);
        g.drawString(font, "HEROBRINE ULTIMATE", left + 12, top + 11, TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(tr("ui.navigation").getString(), panelWidth - 74),
                left + 12, top + 27, MUTED, false);
        String status = tr(!HerobrineClient.hasState() ? "ui.connecting"
                : !HerobrineClient.allowed() ? "ui.denied"
                : HerobrineClient.settings().master ? "ui.active" : "ui.suspended").getString();
        g.drawString(font, font.plainSubstrByWidth(status, Math.max(30, panelWidth - 185)),
                left + panelWidth - 35 - Math.min(font.width(status), Math.max(30, panelWidth - 185)),
                top + 14, HerobrineClient.allowed() ? CYAN : PURPLE, false);
        g.hLine(left + 8, left + panelWidth - 9, top + 40, 0x8058EFFF);
        g.fill(left + 6, bodyTop - 2, left + navWidth + 8, bodyBottom + 1, 0x78030A18);
        frame(g, contentX, bodyTop - 2, contentWidth, Math.max(4, bodyBottom - bodyTop + 3), false);
        if (previewWidth > 0) renderPreview(g, mouseX, mouseY);

        g.enableScissor(contentX + 1, bodyTop, contentX + contentWidth - 1, bodyBottom);
        for (Note note : notes) {
            for (int i = 0; i < note.lines.size(); i++) {
                g.drawString(font, note.lines.get(i), contentX + 8,
                        bodyTop + note.y - contentScroll + i * 11, note.color, false);
            }
        }
        for (Placed placed : widgets) if (placed.area == Area.CONTENT) placed.widget.render(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        g.enableScissor(left + 6, bodyTop, left + navWidth + 9, bodyBottom);
        for (Placed placed : widgets) if (placed.area == Area.NAV) placed.widget.render(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        for (Placed placed : widgets) if (placed.area == Area.FIXED) placed.widget.render(g, mouseX, mouseY, partialTick);
        scrollbar(g, left + navWidth + 8, navScroll, Ability.values().length * 27);
        scrollbar(g, contentX + contentWidth - 3, contentScroll, contentHeight);
        String footer = minecraft.player == null ? "" : Component.translatable("herobrineultimate.ui.status",
                Math.round(minecraft.player.getHealth()), Math.round(minecraft.player.getMaxHealth()),
                minecraft.player.getFoodData().getFoodLevel(), minecraft.player.experienceLevel).getString();
        if (!HerobrineClient.hasState()) footer = tr("message.waiting").getString();
        else if (!HerobrineClient.allowed()) footer = tr("message.denied").getString();
        g.drawString(font, font.plainSubstrByWidth(footer, panelWidth - 18), left + 9,
                top + panelHeight - 43, MUTED, false);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        frame(g, previewX, bodyTop - 2, previewWidth, bodyBottom - bodyTop + 3, false);
        g.drawCenteredString(font, tr("ui.preview"), previewX + previewWidth / 2, bodyTop + 7, CYAN);
        if (minecraft.player == null) return;
        int baseline = bodyBottom - 32;
        int scale = Mth.clamp((bodyBottom - bodyTop - 60) / 2, 20, 65);
        int center = previewX + previewWidth / 2;
        g.enableScissor(previewX + 1, bodyTop + 20, previewX + previewWidth - 1, bodyBottom - 20);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, center, baseline, scale,
                center - mouseX, baseline - scale - mouseY, minecraft.player);
        g.disableScissor();
        Component name = HerobrineClient.transformed(minecraft.player.getUUID())
                ? Component.literal("Herobrine") : minecraft.player.getDisplayName();
        g.drawCenteredString(font, name, center, bodyBottom - 15, PURPLE);
    }

    private void scrollbar(GuiGraphics g, int x, int scroll, int total) {
        int viewport = bodyBottom - bodyTop;
        if (total <= viewport || viewport <= 0) return;
        int handle = Math.max(10, viewport * viewport / total);
        int y = bodyTop + (viewport - handle) * scroll / Math.max(1, total - viewport);
        g.fill(x, bodyTop, x + 2, bodyBottom, 0x304F90B0);
        g.fill(x, y, x + 2, y + handle, CYAN);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, boolean outer) {
        g.fill(x, y, x + w, y + h, outer ? 0xDE030915 : 0x99050918);
        if (outer && HerobrineClient.panelGlow()) {
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0x584BDFFF);
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x887B45C5);
        }
        g.hLine(x, x + w - 1, y, CYAN);
        g.vLine(x, y, y + h - 1, CYAN);
        g.hLine(x, x + w - 1, y + h - 1, PURPLE);
        g.vLine(x + w - 1, y, y + h - 1, PURPLE);
        if (outer) {
            g.fill(x + 5, y - 1, x + Math.min(w / 3, 110), y + 2, CYAN);
            g.fill(x + w - Math.min(w / 4, 80), y + h - 2, x + w - 5, y + h + 1, PURPLE);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0) return false;
        if (mouseY >= bodyTop && mouseY <= bodyBottom) {
            if (mouseX >= left + 6 && mouseX < left + navWidth + 12) navScroll -= (int) Math.copySign(Math.max(1, Math.abs(amount * 27)), amount);
            else if (mouseX >= contentX && mouseX <= contentX + contentWidth) contentScroll -= (int) Math.copySign(Math.max(1, Math.abs(amount * 25)), amount);
            else return super.mouseScrolled(mouseX, mouseY, amount);
            clampScroll();
            placeWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().startsWith("key.herobrineultimate.") && mapping.matchesMouse(button)
                    && shortcutOperation(mapping.getName().substring("key.herobrineultimate.".length()))) return true;
        }
        // Scissor only clips rendering; restrict hit testing to the same viewport as well.
        for (Area area : new Area[]{Area.FIXED, Area.NAV, Area.CONTENT}) {
            if (area != Area.FIXED && (mouseY < bodyTop || mouseY >= bodyBottom)) continue;
            for (Placed placed : widgets) {
                if (placed.area != area || !placed.widget.visible) continue;
                if (placed.widget.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(placed.widget);
                    setDragging(button == 0);
                    pointerCapture = placed.widget;
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AbstractWidget pressed = pointerCapture;
        pointerCapture = null;
        setDragging(false);
        // Sliders commit on release even when a drag ends outside their rectangle or viewport.
        if (pressed != null) return pressed.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override public void removed() {
        if (pointerCapture instanceof AbstractSliderButton slider) {
            slider.onRelease(slider.getX(), slider.getY());
        }
        pointerCapture = null;
        setDragging(false);
        super.removed();
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            int current = -1;
            for (int i = 0; i < widgets.size(); i++) if (widgets.get(i).widget == getFocused()) current = i;
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            for (int i = 1; i <= widgets.size(); i++) {
                Placed next = widgets.get(Math.floorMod(current + direction * i, widgets.size()));
                if (!next.widget.active) continue;
                reveal(next);
                setFocused(next.widget);
                return true;
            }
            return true;
        }
        // Minecraft normally routes keys to the open screen rather than in-world key bindings.
        // Resolve registered mappings here too, so Insert and rebound shortcuts work while open.
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (!mapping.getName().startsWith("key.herobrineultimate.") || !mapping.matches(key, scanCode)) continue;
            if (shortcutOperation(mapping.getName().substring("key.herobrineultimate.".length()))) return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0 && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            Ability[] categories = Ability.values();
            int index = Math.floorMod(category.ordinal() + (key == GLFW.GLFW_KEY_UP ? -1 : 1), categories.length);
            category = categories[index];
            contentScroll = 0;
            navScroll = Mth.clamp(index * 27 - (bodyBottom - bodyTop) / 2, 0, categories.length * 27);
            rebuild();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            contentScroll += (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(30, bodyBottom - bodyTop - 26);
            clampScroll();
            placeWidgets();
            return true;
        }
        // All loader key bindings also remain available in Minecraft's Controls menu.
        return super.keyPressed(key, scanCode, modifiers);
    }

    private boolean shortcutOperation(String operation) {
        switch (operation) {
            case "open" -> onClose();
            case "master" -> HerobrineClient.master();
            case "lightning", "save", "return", "teleport" -> HerobrineClient.action(operation);
            default -> { return false; }
        }
        return true;
    }

    private void reveal(Placed placed) {
        if (placed.area == Area.FIXED) return;
        int scroll = placed.area == Area.NAV ? navScroll : contentScroll;
        int viewport = bodyBottom - bodyTop;
        if (placed.baseY < scroll) scroll = placed.baseY;
        else if (placed.baseY + placed.widget.getHeight() > scroll + viewport) {
            scroll = placed.baseY + placed.widget.getHeight() - viewport;
        }
        if (placed.area == Area.NAV) navScroll = scroll; else contentScroll = scroll;
        clampScroll();
        placeWidgets();
    }

    private static Component shortcut(String label, String operation) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().equals("key.herobrineultimate." + operation)) {
                return tr(label).copy().append(" [").append(mapping.getTranslatedKeyMessage()).append("]");
            }
        }
        return tr(label);
    }

    private static Component actionLabel(String label, String operation) {
        return switch (label) {
            case "ui.strike", "ui.teleport", "ui.save", "ui.return" -> shortcut(label, operation);
            default -> tr(label);
        };
    }

    @Override public boolean isPauseScreen() { return false; }

    private static String id(Ability ability) { return ability.name().toLowerCase(Locale.ROOT); }
    private static Component label(Ability ability) { return tr("ability." + id(ability)); }
    private static Component tr(String key) { return Component.translatable("herobrineultimate." + key); }
    private enum Area { NAV, CONTENT, FIXED }
    private record Placed(AbstractWidget widget, Area area, int baseY) { }
    private record Note(List<FormattedCharSequence> lines, int y, int color) { }

    private static final class NeonButton extends AbstractButton {
        private final Supplier<Component> label;
        private final BooleanSupplier selected;
        private final Runnable press;

        NeonButton(int x, int y, int width, int height, Supplier<Component> label,
                   BooleanSupplier selected, Runnable press) {
            super(x, y, width, height, label.get());
            this.label = label;
            this.selected = selected;
            this.press = press;
        }

        @Override public void onPress() { press.run(); }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            setMessage(label.get());
            boolean highlighted = selected.getAsBoolean();
            int color = !active ? 0xFF637484 : highlighted || isHoveredOrFocused() ? CYAN : PURPLE;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), highlighted ? 0xBD0B3145 : 0xB4071227);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), color);
            if (highlighted) g.fill(getX(), getY() + 2, getX() + 3, getY() + getHeight() - 2, CYAN);
            renderScrollingString(g, Minecraft.getInstance().font, 5, color);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class BoostSlider extends AbstractSliderButton {
        private final String key;
        private final double maximum;

        BoostSlider(int x, int y, int width, String key, double maximum) {
            super(x, y, width, 27, Component.empty(),
                    (HerobrineClient.settings().multiplier(key) - 1) / (maximum - 1));
            this.key = key;
            this.maximum = maximum;
            updateMessage();
        }

        private double multiplier() { return Math.round((1 + value * (maximum - 1)) * 10) / 10.0; }

        @Override protected void updateMessage() {
            if (key != null) setMessage(Component.translatable("herobrineultimate.ui.multiplier",
                    tr("multiplier." + key), String.format(Locale.ROOT, "%.1f", multiplier())));
        }

        @Override protected void applyValue() { updateMessage(); }

        @Override public void onRelease(double mouseX, double mouseY) { HerobrineClient.number(key, multiplier()); }

        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
                HerobrineClient.number(key, multiplier());
            }
            return handled;
        }

        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int color = active ? isHoveredOrFocused() ? CYAN : PURPLE : 0xFF637484;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xC0071528);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), color);
            renderScrollingString(g, Minecraft.getInstance().font, 5, TEXT);
            int sliderY = getY() + getHeight() - 5;
            int handleX = getX() + 4 + (int) ((getWidth() - 10) * value);
            g.fill(getX() + 4, sliderY, getX() + getWidth() - 4, sliderY + 1, 0xFF35556D);
            g.fill(getX() + 4, sliderY, handleX, sliderY + 1, PURPLE);
            g.fill(handleX - 1, sliderY - 2, handleX + 2, sliderY + 3, color);
        }
    }
}
