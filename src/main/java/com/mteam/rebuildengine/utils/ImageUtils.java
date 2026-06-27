package com.mteam.rebuildengine.utils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    public static byte[] convertToJpgAndResize(InputStream input, int maxWidth, int maxHeight) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(input);

            // Calculate new dimensions maintaining aspect ratio
            int scaledWidth = image.getWidth();
            int scaledHeight = image.getHeight();
            double aspectRatio = (double) image.getWidth() / image.getHeight();

            if (scaledWidth > maxWidth) {
                scaledWidth = maxWidth;
                scaledHeight = (int) (scaledWidth / aspectRatio);
            }

            if (scaledHeight > maxHeight) {
                scaledHeight = maxHeight;
                scaledWidth = (int) (scaledHeight * aspectRatio);
            }

            // Create scaled image
            Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            BufferedImage scaledBufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaledBufferedImage.createGraphics();
            graphics.drawImage(scaledImage, 0, 0, null);
            graphics.dispose();

            // Write the scaled image as JPG to the ByteArrayOutputStream
            ImageIO.write(scaledBufferedImage, "jpg", baos);

            // Convert ByteArrayOutputStream to byte array
            byte[] jpgBytes = baos.toByteArray();

            return jpgBytes;
        }
    }
}
