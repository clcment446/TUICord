package com.c446.disctui_client.tui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KittyGraphicsRenderer {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public boolean supportsKitty() {
        String term = System.getenv("TERM");
        return (term != null && term.toLowerCase().contains("kitty")) || System.getenv("KITTY_WINDOW_ID") != null;
    }

    public String renderInlineUrl(String url, int maxWidthPx, int maxHeightPx) {
        if (!supportsKitty() || url == null || url.isBlank()) {
            return "";
        }

        return cache.computeIfAbsent(url, key -> {
            try (InputStream in = new URL(key).openStream()) {
                BufferedImage source = ImageIO.read(in);
                if (source == null) {
                    return "";
                }
                BufferedImage scaled = scale(source, maxWidthPx, maxHeightPx);
                return encodeKittyImage(scaled);
            } catch (Exception e) {
                return "";
            }
        });
    }

    private BufferedImage scale(BufferedImage source, int maxWidthPx, int maxHeightPx) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min((double) maxWidthPx / Math.max(1, width), (double) maxHeightPx / Math.max(1, height));
        scale = Math.min(scale, 1.0d);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        Image resized = source.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(resized, 0, 0, null);
        g.dispose();
        return out;
    }

    private String encodeKittyImage(BufferedImage image) {
        byte[] pngBytes;
        try (var baos = new java.io.ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            pngBytes = baos.toByteArray();
        } catch (Exception e) {
            return "";
        }

        String payload = Base64.getEncoder().encodeToString(pngBytes);
        return "\u001b_Gf=100,a=T,t=d,m=1;" + payload + "\u001b\\";
    }
}

