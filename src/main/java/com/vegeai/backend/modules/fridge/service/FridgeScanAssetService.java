package com.vegeai.backend.modules.fridge.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu trữ và cung cấp lại ảnh chụp tủ lạnh đã qua AI phân tích trên ổ đĩa/Docker volume.
 */
@Service
@Slf4j
public class FridgeScanAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadDirectory;

    public FridgeScanAssetService(
            @Value("${app.fridge.upload-dir:./uploads/fridge-scans}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /**
     * Lưu byte ảnh đã được AI quét thành công xuống đĩa và trả về URL truy cập công khai.
     * Lỗi I/O cục bộ chỉ ghi log cảnh báo để không làm gián đoạn kết quả phân tích AI.
     */
    public String storeSafely(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0 || imageBytes.length > MAX_IMAGE_BYTES) {
            return null;
        }
        String normalizedType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        String extension = EXTENSION_BY_MIME.get(normalizedType);
        if (extension == null) {
            return null;
        }

        try {
            Files.createDirectories(uploadDirectory);
            String filename = UUID.randomUUID() + extension;
            Path target = uploadDirectory.resolve(filename).normalize();
            if (!target.startsWith(uploadDirectory)) {
                return null;
            }
            Files.write(target, imageBytes);
            return "/api/public/fridge-scans/" + filename;
        } catch (IOException ex) {
            log.warn("[FRIDGE-ASSET] Could not persist scanned fridge image to '{}': {}",
                    uploadDirectory, ex.getMessage());
            return null;
        }
    }

    public Path locate(String filename) {
        if (filename == null || !filename.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Path path = uploadDirectory.resolve(filename).normalize();
        if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return path;
    }
}
