package com.myriad.client.gui;

import com.myriad.network.DeathRecapPayload;
import com.myriad.network.DeathRecapPayload.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Non-pausing, wrapped and scrollable. Opens on request so it never interrupts death spectating. */
public final class DeathRecapScreen extends Screen {
    private final DeathRecapPayload recap;
    private int scroll, maximum;
    private record Line(Component text, int color) {}
    private record Key(Source source, int kind) {}
    public DeathRecapScreen(DeathRecapPayload recap) { super(Component.translatable("recap.myriad.title")); this.recap = recap; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 55, height - 26, 110, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(null); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.clamp(scroll - (int)(dy * 24), 0, maximum); return true;
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static Component source(Source s) {
        Component name = s.category() == 0 ? Component.literal(s.name())
                : s.category() == 4 ? Component.translatable(s.name()) : Component.translatable("recap.myriad.source." + s.category());
        var text = Component.empty().append(name);
        if (!s.role().isBlank()) text.append(" · ").append(Component.translatable("character.myriad." + s.role()));
        if (s.category() == 0) text.append(" / ").append(s.basic() ? Component.translatable("recap.myriad.basic")
                : s.skill().isBlank() ? Component.translatable("recap.myriad.skill") : Component.translatable("skill.myriad." + s.skill()));
        else if (s.category() == 5) text.append(" / ").append(Component.translatable("recap.myriad.environment." + switch (s.name()) {
            case "fall" -> "fall";
            case "inFire", "onFire", "lava", "hotFloor" -> "fire";
            case "drown" -> "drown";
            case "outOfWorld" -> "void";
            case "mob", "magic", "indirectMagic" -> "field";
            default -> "other";
        }));
        return text;
    }
    private List<Line> lines() {
        var lines = new ArrayList<Line>();
        double health = recap.hits().stream().mapToDouble(Hit::health).sum(), shield = recap.hits().stream().mapToDouble(Hit::shield).sum();
        lines.add(new Line(Component.translatable("recap.myriad.window", DeathRecapPayload.WINDOW / 20), 0xFFB4C8D8));
        lines.add(new Line(Component.translatable("recap.myriad.totals", number(health), number(shield)), 0xFFF3E2BB));
        double[] kinds = new double[4];
        for (var hit : recap.hits()) kinds[hit.kind()] += hit.health() + hit.shield();
        int[] colors = {0xFFFFAD7D, 0xFF83BFFF, 0xFFF1F4FF, 0xFFB9C2CF};
        for (int i = 0; i < kinds.length; i++) if (kinds[i] > 0) lines.add(new Line(Component.translatable("recap.myriad.share",
                Component.translatable("recap.myriad.kind." + i), number(kinds[i]), number(100 * kinds[i] / (health + shield))), colors[i]));
        lines.add(new Line(Component.translatable("recap.myriad.scope"), 0xFF90A9BA));
        if (recap.limited()) lines.add(new Line(Component.translatable("recap.myriad.limited"), 0xFFFFBB66));
        lines.add(new Line(Component.translatable("recap.myriad.breakdown"), 0xFF8CD6F5));
        var groups = new LinkedHashMap<Key, double[]>();
        for (var hit : recap.hits()) {
            var totals = groups.computeIfAbsent(new Key(hit.source(), hit.kind()), ignored -> new double[2]);
            totals[0] += hit.health(); totals[1] += hit.shield();
        }
        var ranked = new ArrayList<>(groups.entrySet());
        ranked.sort(Comparator.<Map.Entry<Key, double[]>>comparingDouble(e -> e.getValue()[0] + e.getValue()[1]).reversed());
        for (var group : ranked) lines.add(new Line(Component.translatable("recap.myriad.damage_row", source(group.getKey().source),
                Component.translatable("recap.myriad.kind." + group.getKey().kind), number(group.getValue()[0]), number(group.getValue()[1])), 0xFFE3ECF4));
        if (groups.isEmpty()) lines.add(new Line(Component.translatable("recap.myriad.no_damage"), 0xFF90A9BA));
        lines.add(new Line(Component.translatable("recap.myriad.controls"), 0xFF8CD6F5));
        for (var control : recap.controls()) lines.add(new Line(Component.translatable("recap.myriad.control_row", number(control.before() / 20d),
                source(control.source()), Component.translatable("recap.myriad.control." + control.kind()), number(control.ticks() / 20d)), 0xFFD4B4EE));
        if (recap.controls().isEmpty()) lines.add(new Line(Component.translatable("recap.myriad.no_control"), 0xFF90A9BA));
        lines.add(new Line(Component.translatable("recap.myriad.timeline"), 0xFF8CD6F5));
        for (var hit : recap.hits()) lines.add(new Line(Component.translatable("recap.myriad.hit_row", number(hit.before() / 20d),
                source(hit.source()), Component.translatable("recap.myriad.kind." + hit.kind()), number(hit.health()), number(hit.shield()),
                hit.fatal() ? Component.translatable("recap.myriad.fatal") : Component.empty()), hit.fatal() ? 0xFFFF887C : 0xFFC4D1DC));
        return lines;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xEF070E18);
        gui.text(font, title, (width - font.width(title)) / 2, 11, 0xFFFFD48B);
        int panelWidth = Math.max(20, Math.min(600, width - 20)), left = (width - panelWidth) / 2;
        int top = 31, bottom = Math.max(top + 1, height - 34), wrap = Math.max(1, panelWidth - 22);
        var paragraphs = lines();
        var wrapped = paragraphs.stream().map(l -> font.split(l.text, wrap)).toList();
        int total = wrapped.stream().mapToInt(p -> p.size() * 12 + 7).sum();
        maximum = Math.max(0, total - (bottom - top)); scroll = Math.clamp(scroll, 0, maximum);
        gui.fill(left, top, left + panelWidth, bottom, 0xC51A2633);
        gui.enableScissor(left, top, left + panelWidth, bottom);
        int y = top + 2 - scroll;
        for (int i = 0; i < paragraphs.size(); i++) {
            for (var text : wrapped.get(i)) { gui.text(font, text, left + 8, y, paragraphs.get(i).color); y += 12; }
            y += 7;
        }
        gui.disableScissor();
        if (maximum > 0) {
            int h = bottom - top, thumb = Math.max(8, h * h / Math.max(h, total));
            int y0 = top + (h - thumb) * scroll / maximum;
            gui.fill(left + panelWidth - 4, y0, left + panelWidth - 2, y0 + thumb, 0xFF6DA6BE);
        }
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }
}
