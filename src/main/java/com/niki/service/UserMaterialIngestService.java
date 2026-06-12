package com.niki.service;

import com.niki.model.User;
import com.niki.util.ImageNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMaterialIngestService {

    private static final int MAX_STORE_CHARS = 6000;
    private static final int MAX_EXTRACT_CHARS = 12000;

    private final TelegramFileService telegramFileService;
    private final MentorProfileService mentorProfileService;
    private final LlmService llmService;

    @Transactional
    public String ingestDocument(AbsSender bot, User user, Document document, String caption) throws TelegramApiException {
        String fileName = document.getFileName() != null ? document.getFileName() : "файл";
        String mime = document.getMimeType() != null ? document.getMimeType() : "";
        byte[] bytes = telegramFileService.downloadDocument(bot, document);

        if (ImageNormalizer.isImage(fileName, mime)) {
            String imageMime = ImageNormalizer.resolveMime(fileName, mime);
            String text = llmService.extractTextFromImage(bytes, imageMime, caption);
            if (!StringUtils.hasText(text)) {
                return imageReadFailedMessage();
            }
            return saveMaterial(user, fileName, caption, text);
        }

        String text = extractText(bytes, fileName, mime);
        if (!StringUtils.hasText(text)) {
            return "❌ Не смог прочитать *" + escapeMd(fileName) + "*.\n"
                    + "Пришли `.txt`, `.md`, PDF или скрин (PNG/JPG).";
        }
        return saveMaterial(user, fileName, caption, text);
    }

    @Transactional
    public String ingestPhoto(User user, byte[] imageBytes, String mimeType, String caption) {
        String label = StringUtils.hasText(caption) ? caption : "скриншот";
        String ocr = llmService.extractTextFromImage(imageBytes, mimeType, caption);
        if (!StringUtils.hasText(ocr)) {
            return imageReadFailedMessage();
        }
        return saveMaterial(user, label, caption, ocr);
    }

    private static String imageReadFailedMessage() {
        return """
                📷 Скрин получил, но распознать не смог.
                
                Проверь на Render:
                • `CLAUDE_API_KEY` или `GROQ_API_KEY`
                
                Или пришли текстом / PDF — сохраню в профиль.""";
    }

    private String saveMaterial(User user, String fileName, String caption, String rawText) {
        String trimmed = trimText(rawText);
        String summary = llmService.summarizeMaterialForProfile(trimmed, fileName, caption);
        String section = detectSection(fileName, caption, trimmed);
        mentorProfileService.mergeIntoProfile(user, section, fileName, summary);
        mentorProfileService.appendMemoryNote(user, fileName, summary);

        return """
                ✅ *Данные сохранены*
                
                📄 %s
                📌 Раздел: *%s*
                
                Теперь помню это в профиле и в ответах.
                Напиши «что помнишь обо мне?» или жми 🧠 *Профиль*.""".formatted(
                escapeMd(fileName),
                sectionLabel(section)
        );
    }

    private static String extractText(byte[] bytes, String fileName, String mime) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String lowerMime = mime.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf") || lowerMime.contains("pdf")) {
            return extractPdf(bytes);
        }
        if (isTextLike(lowerName, lowerMime)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return "";
    }

    private static boolean isTextLike(String lowerName, String lowerMime) {
        return lowerName.endsWith(".txt")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown")
                || lowerMime.startsWith("text/")
                || lowerMime.contains("markdown");
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            log.warn("PDF extract failed: {}", e.getMessage());
            return "";
        }
    }

    private static String trimText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u0000', ' ').trim();
        if (normalized.length() <= MAX_EXTRACT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_EXTRACT_CHARS) + "\n…";
    }

    static String detectSection(String fileName, String caption, String text) {
        String hay = (fileName + " " + (caption != null ? caption : "") + " " + text.substring(0, Math.min(500, text.length())))
                .toLowerCase(Locale.ROOT);
        if (hay.contains("резюме") || hay.contains("resume") || hay.contains("cv")) {
            return "remember";
        }
        if (hay.contains("свп") || hay.contains("вектор") || hay.contains("анальн") || hay.contains("звуков")) {
            return "remember";
        }
        if (hay.contains("spring") || hay.contains("java") || hay.contains("учеб") || hay.contains("конспект")) {
            return "learning";
        }
        return "remember";
    }

    static String sectionLabel(String section) {
        return switch (section) {
            case "learning" -> "Чему учусь сейчас";
            case "problems" -> "Проблемы";
            default -> "Важно помнить";
        };
    }

    static String escapeMd(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("_", "\\_");
    }
}
