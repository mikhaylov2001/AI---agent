package com.niki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class TelegramFileService {

    private final WebClient webClient;
    private final String botToken;

    public TelegramFileService(@Value("${telegram.bot.token}") String botToken) {
        this.botToken = botToken;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public byte[] downloadDocument(AbsSender bot, Document document) throws TelegramApiException {
        if (document == null || document.getFileId() == null) {
            throw new IllegalArgumentException("Документ без file_id");
        }
        var file = bot.execute(new GetFile(document.getFileId()));
        return downloadByPath(file.getFilePath());
    }

    public byte[] downloadLargestPhoto(AbsSender bot, List<PhotoSize> photos) throws TelegramApiException {
        if (photos == null || photos.isEmpty()) {
            throw new IllegalArgumentException("Нет фото");
        }
        PhotoSize largest = photos.stream()
                .max(Comparator.comparingInt(s -> s.getFileSize() != null ? s.getFileSize() : 0))
                .orElseThrow();
        var file = bot.execute(new GetFile(largest.getFileId()));
        return downloadByPath(file.getFilePath());
    }

    public String mimeTypeForPhoto(List<PhotoSize> photos) {
        return "image/jpeg";
    }

    private byte[] downloadByPath(String filePath) {
        String url = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
        byte[] bytes = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Пустой файл из Telegram");
        }
        return bytes;
    }
}
