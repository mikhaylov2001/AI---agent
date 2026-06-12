package com.niki.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class ImageNormalizer {

    private static final int MAX_EDGE = 1568;

    private ImageNormalizer() {
    }

    public record NormalizedImage(byte[] bytes, String mimeType) {
    }

    public static NormalizedImage normalize(byte[] raw, String mimeHint) {
        if (raw == null || raw.length == 0) {
            return new NormalizedImage(raw, fallbackMime(mimeHint));
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
            if (src == null) {
                return new NormalizedImage(raw, fallbackMime(mimeHint));
            }
            BufferedImage scaled = scaleDown(src, MAX_EDGE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", out);
            return new NormalizedImage(out.toByteArray(), "image/jpeg");
        } catch (Exception e) {
            return new NormalizedImage(raw, fallbackMime(mimeHint));
        }
    }

    public static boolean isImage(String fileName, String mimeType) {
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        String lowerMime = mimeType != null ? mimeType.toLowerCase() : "";
        if (lowerMime.startsWith("image/")) {
            return true;
        }
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".gif");
    }

    public static String resolveMime(String fileName, String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return mimeType;
        }
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static BufferedImage scaleDown(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxEdge && h <= maxEdge) {
            return src;
        }
        double scale = Math.min((double) maxEdge / w, (double) maxEdge / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static String fallbackMime(String mimeHint) {
        return mimeHint != null && mimeHint.startsWith("image/") ? mimeHint : "image/jpeg";
    }
}
