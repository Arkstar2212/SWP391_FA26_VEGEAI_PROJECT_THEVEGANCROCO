package com.vegeai.backend.modules.fridge.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Lưu trữ và cung cấp lại ảnh chụp tủ lạnh đã qua AI phân tích trên ổ đĩa/Docker volume.
 */
@Service
@Slf4j
public class FridgeScanAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Duration ORPHAN_MAX_AGE = Duration.ofDays(14);
    private static final long CLEANUP_COOLDOWN_MS = Duration.ofHours(6).toMillis();
    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadDirectory;
    private final FridgeItemRepository fridgeItemRepository;
    private final AtomicLong lastCleanupAtMs = new AtomicLong(0);

    public FridgeScanAssetService(String uploadDirectory) {
        this(uploadDirectory, null);
    }

    @Autowired
    public FridgeScanAssetService(
            @Value("${app.fridge.upload-dir:./uploads/fridge-scans}") String uploadDirectory,
            @Autowired(required = false) FridgeItemRepository fridgeItemRepository) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.fridgeItemRepository = fridgeItemRepository;
    }

    /**
     * Lưu byte ảnh đã được AI quét thành công xuống đĩa theo UUID tất định của nội dung (chống trùng file)
     * và trả về URL truy cập công khai. Lỗi I/O cục bộ chỉ ghi log cảnh báo để không gián đoạn AI.
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
            String filename = UUID.nameUUIDFromBytes(imageBytes) + extension;
            Path target = uploadDirectory.resolve(filename).normalize();
            if (!target.startsWith(uploadDirectory)) {
                return null;
            }
            if (Files.isRegularFile(target)) {
                Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
            } else {
                Files.write(target, imageBytes);
            }
            maybePurgeExpiredOrphans();
            return "/api/public/fridge-scans/" + filename;
        } catch (IOException ex) {
            log.warn("[FRIDGE-ASSET] Could not persist scanned fridge image to '{}': {}",
                    uploadDirectory, ex.getMessage());
            return null;
        }
    }

    private void maybePurgeExpiredOrphans() {
        if (fridgeItemRepository == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long prev = lastCleanupAtMs.get();
        if (nowMs - prev < CLEANUP_COOLDOWN_MS || !lastCleanupAtMs.compareAndSet(prev, nowMs)) {
            return;
        }
        purgeExpiredOrphans(ORPHAN_MAX_AGE);
    }

    /**
     * Dọn các file ảnh tạm đã quá hạn và không còn gắn với nguyên liệu nào trong kho tủ lạnh.
     */
    public int purgeExpiredOrphans(Duration maxAge) {
        if (!Files.isDirectory(uploadDirectory)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        int deleted = 0;
        try (Stream<Path> files = Files.list(uploadDirectory)) {
            for (Path candidate : files.toList()) {
                if (!Files.isRegularFile(candidate)) continue;
                String name = candidate.getFileName().toString();
                if (!name.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) continue;
                FileTime modified = Files.getLastModifiedTime(candidate);
                if (modified.toInstant().isAfter(cutoff)) continue;
                String url = "/api/public/fridge-scans/" + name;
                if (fridgeItemRepository != null && fridgeItemRepository.existsByImageUrl(url)) {
                    continue;
                }
                if (Files.deleteIfExists(candidate)) {
                    deleted++;
                }
            }
        } catch (Exception ex) {
            log.debug("[FRIDGE-ASSET] Orphan purge skipped: {}", ex.getMessage());
        }
        return deleted;
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
