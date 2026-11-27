import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Extract one of the 11 fixed #68 panels and compare it with the untouched client frame. */
class Comparison {
    private static void writePair(BufferedImage old, BufferedImage fresh, String title, String baseline, Path output)
            throws java.io.IOException {
        var pair = new BufferedImage(1584, 492, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pair.createGraphics();
        g.setColor(new Color(19, 23, 30));
        g.fillRect(0, 0, pair.getWidth(), pair.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawString(title + "  ·  " + baseline, 8, 29);
        g.drawString(title + "  ·  本轮", 796, 29);
        g.drawImage(old, 8, 44, 780, 438, null);
        g.drawImage(fresh, 796, 44, 780, 438, null);
        g.dispose();
        ImageIO.write(pair, "jpg", output.toFile());
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--runs")) {
            for (int i = 1; i < args.length; i += 4) {
                var old = ImageIO.read(Path.of(args[i + 1]).toFile());
                var fresh = ImageIO.read(Path.of(args[i + 2]).toFile());
                if (old.getWidth() != fresh.getWidth() || old.getHeight() != fresh.getHeight()) {
                    throw new IllegalStateException("Framebuffer dimensions differ for " + args[i]);
                }
                writePair(old, fresh, args[i], "上一轮", Path.of(args[i + 3]));
            }
            return;
        }
        Path references = Path.of(args[0]);
        int count = (args.length - 1) / 4;
        Path output = Path.of(args[4]).getParent().getParent();
        BufferedImage overview = null;
        Graphics2D gallery = null;
        for (int i = 1; i < args.length; i += 4) {
            int index = Integer.parseInt(args[i]);
            var sheet = ImageIO.read(references.resolve("closer-grip-" + (1 + index / 6) + ".jpg").toFile());
            if (sheet.getWidth() != 1608 || sheet.getHeight() != 1498) {
                throw new IllegalStateException("Unexpected #68 sheet dimensions");
            }
            int cell = index % 6;
            var old = sheet.getSubimage(16 + (cell % 2) * 796, 56 + (cell / 2) * 494, 780, 438);
            var fresh = ImageIO.read(Path.of(args[i + 2]).toFile());
            if (Math.abs((double) fresh.getWidth() / fresh.getHeight() - 16.0 / 9) > .005) {
                throw new IllegalStateException("Client framebuffer has a different aspect ratio: " + args[i + 2]);
            }
            writePair(old, fresh, args[i + 1], "#68", Path.of(args[i + 3]));

            int entry = (i - 1) / 4;
            int slot = entry % 6;
            if (slot == 0) {
                int entries = Math.min(6, count - entry);
                int columns = Math.min(2, entries);
                int rows = (entries + columns - 1) / columns;
                overview = new BufferedImage(16 + columns * 796, 16 + rows * 494, BufferedImage.TYPE_INT_RGB);
                gallery = overview.createGraphics();
                gallery.setColor(new Color(19, 23, 30));
                gallery.fillRect(0, 0, overview.getWidth(), overview.getHeight());
                gallery.setColor(Color.WHITE);
                gallery.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                gallery.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
            int x = 16 + slot % 2 * 796;
            int y = slot / 2 * 494;
            gallery.drawString(String.format("%02d  %s", index + 1, args[i + 1]), x, y + 42);
            gallery.drawImage(fresh, x, y + 56, 780, 438, null);
            if (slot == 5 || entry == count - 1) {
                gallery.dispose();
                ImageIO.write(overview, "jpg", output.resolve("overview-" + (entry / 6 + 1) + ".jpg").toFile());
            }
        }
    }
}
