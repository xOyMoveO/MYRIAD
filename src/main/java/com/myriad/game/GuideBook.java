package com.myriad.game;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
public final class GuideBook {
    public static void give(ServerPlayer player) {
        if (!ModState.isEnabled()) return;
        var pages = new java.util.ArrayList<Filterable<Component>>();
        for (int page = 1; page <= 10; page++) pages.add(Filterable.passThrough(Component.translatable("guide.myriad.page" + page)));
        var content = new WrittenBookContent(Filterable.passThrough("MYRIAD Guide"), "MYRIAD", 0, pages, true);
        var title = Component.translatable("guide.myriad.title");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (item.is(Items.WRITTEN_BOOK) && title.equals(item.get(DataComponents.CUSTOM_NAME))) {
                if (!content.equals(item.get(DataComponents.WRITTEN_BOOK_CONTENT))) {
                    item.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
                    player.getInventory().setChanged();
                }
                return;
            }
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.CUSTOM_NAME, title);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        if (!player.getInventory().add(book)) player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
}
