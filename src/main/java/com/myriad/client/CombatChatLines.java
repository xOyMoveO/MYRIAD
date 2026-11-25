package com.myriad.client;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Derives visible runs for the vanilla display queue. The original message remains on each line. */
public final class CombatChatLines {
    private static final int MIN_CONTENT_WIDTH = 20;
    private static final Style CHAT_FONT = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("myriad", "combat_chat")));

    private CombatChatLines() {}

    public static List<FormattedCharSequence> wrap(GuiMessage message, Font font, int width, CombatChatTime time,
                                                   ZoneId zone, boolean colors) {
        int usable = width;
        if (message.tag() != null && message.tag().icon() != null) usable -= message.tag().icon().width + 6;
        FormattedCharSequence clock = timePrefix(time, zone, usable < 168, colors);
        int clockWidth = font.width(clock);
        if (usable - clockWidth < MIN_CONTENT_WIDTH) return message.splitLines(font, width);

        Component root = message.content();
        if (root.getSiblings().isEmpty() && root.getContents() instanceof TranslatableContents content
                && content.getKey().equals("chat.type.text") && content.getArgs().length == 2
                && content.getArgs()[0] instanceof Component name && content.getArgs()[1] instanceof Component body) {
            Component styledName = name.copy().withStyle(root.getStyle());
            Component colon = Component.literal("：").withStyle(root.getStyle());
            int bodyWidth = Math.max(MIN_CONTENT_WIDTH, Math.min(96, usable / 2));
            int nameWidth = usable - clockWidth - font.width(colon) - bodyWidth;
            if (nameWidth >= font.width("…")) {
                var nameRun = font.width(styledName) <= nameWidth ? styledName.getVisualOrderText()
                        : ComponentRenderUtils.clipText(styledName, font, nameWidth);
                var prefix = FormattedCharSequence.composite(clock, nameRun, colon.getVisualOrderText());
                return prefix(prefix, bodyLines(body, root.getStyle(), font, usable - font.width(prefix), colors), font);
            }
        }
        return prefix(clock, message.splitLines(font, width - clockWidth), font);
    }

    private static List<FormattedCharSequence> bodyLines(Component body, Style inherited, Font font, int width, boolean colors) {
        // Same styled splitting and bidi order as vanilla, without its extra continuation space.
        ComponentCollector collector = new ComponentCollector();
        body.visit((style, text) -> {
            collector.append(FormattedText.of(colors ? text : ChatFormatting.stripFormatting(text), style));
            return Optional.empty();
        }, inherited);
        var lines = new ArrayList<FormattedCharSequence>();
        font.getSplitter().splitLines(collector.getResultOrEmpty(), width, Style.EMPTY,
                (line, wrapped) -> lines.add(Language.getInstance().getVisualOrder(line)));
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    private static List<FormattedCharSequence> prefix(FormattedCharSequence header, List<FormattedCharSequence> body, Font font) {
        var continuation = indent(font.width(header));
        var result = new ArrayList<FormattedCharSequence>(body.size());
        for (int i = 0; i < body.size(); i++) {
            result.add(FormattedCharSequence.composite(i == 0 ? header : continuation, body.get(i)));
        }
        return result;
    }

    private static FormattedCharSequence timePrefix(CombatChatTime time, ZoneId zone, boolean compact, boolean colors) {
        String tooltip = time.sent() ? "chat.myriad.time.sent" : "chat.myriad.time.received";
        Style style = CHAT_FONT.withHoverEvent(new HoverEvent.ShowText(Component.translatable(tooltip, time.fullTime(zone))));
        if (colors) style = style.withColor(0x82ACA7);
        return FormattedCharSequence.forward("[" + time.clock(zone, compact) + "] ", style);
    }

    private static FormattedCharSequence indent(int pixels) {
        StringBuilder spaces = new StringBuilder();
        while (pixels > 0) {
            int bit = Integer.numberOfTrailingZeros(pixels);
            spaces.append((char)(0xE000 + bit));
            pixels &= pixels - 1;
        }
        return FormattedCharSequence.forward(spaces.toString(), CHAT_FONT);
    }
}
