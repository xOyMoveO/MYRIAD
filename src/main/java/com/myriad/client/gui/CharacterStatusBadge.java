package com.myriad.client.gui;

import com.myriad.character.CharacterType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class CharacterStatusBadge {
    private CharacterStatusBadge() {}

    static Component halfCompleteLabel() {
        return Component.translatable("screen.myriad.character.half_complete");
    }

    static void draw(GuiGraphicsExtractor gui, Font font, CharacterType type, int x, int y, int availableWidth) {
        if (!type.isHalfComplete()) return;
        var label = halfCompleteLabel();
        int width = font.width(label) + 6;
        if (width > availableWidth) {
            gui.fill(x, y, x + 5, y + 5, 0xFFFFD48B);
            return;
        }
        gui.fill(x, y, x + width, y + 12, 0xEE493118);
        gui.text(font, label, x + 3, y + 2, 0xFFFFD48B);
    }
}
