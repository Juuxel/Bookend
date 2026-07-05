package juuxel.bookend.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class QrGenerator {
    public static byte[] generateQrImage(String qrText, @Nullable String note) {
        try {
            var writer = new QRCodeWriter();
            var matrix = writer.encode(qrText, BarcodeFormat.QR_CODE, 64, 64);
            var codeImage = MatrixToImageWriter.toBufferedImage(matrix);
            if (note == null) return renderImageToPng(codeImage);

            var font = new Font("Noto Sans", Font.PLAIN, 16);
            var noteLines = note.split("\n");
            int noteWidth = calculateNoteWidth(noteLines, font);
            var fullImage = new BufferedImage(64 + 8 + noteWidth, 64, BufferedImage.TYPE_INT_ARGB);
            var g = fullImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setBackground(Color.WHITE);
            g.setColor(Color.BLACK);
            g.clearRect(0, 0, fullImage.getWidth(), fullImage.getHeight());
            g.drawImage(codeImage, 0, 0, null);

            // Draw note
            g.setFont(font);
            int y = (64 - 18 * noteLines.length) / 2 + 14;
            for (var line : noteLines) {
                g.drawString(line, 68, y);
                y += 18;
            }
            g.dispose();
            return renderImageToPng(fullImage);
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] renderImageToPng(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    private static int calculateNoteWidth(String[] noteLines, Font font) {
        var dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = dummyImage.createGraphics();
        try {
            g.setFont(font);
            var metrics = g.getFontMetrics();
            int width = 0;
            for (var line : noteLines) {
                width = Math.max(width, metrics.stringWidth(line));
            }
            return width;
        } finally {
            g.dispose();
        }
    }
}
