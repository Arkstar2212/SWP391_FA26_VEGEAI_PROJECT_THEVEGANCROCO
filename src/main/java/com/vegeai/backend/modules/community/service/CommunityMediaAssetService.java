package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class CommunityMediaAssetService {
    private static final long MAX_IMAGE = 5L * 1024 * 1024;
    private static final long MAX_VIDEO = 150L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp",
            "video/mp4", ".mp4", "video/webm", ".webm");
    private final Path directory;

    public CommunityMediaAssetService(@Value("${app.community.upload-dir:./uploads/community}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, boolean video) {
        if (file == null || file.isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn tệp cần tải lên.");
        String type = file.getContentType();
        String extension = TYPES.get(type);
        boolean validType = video ? type != null && type.startsWith("video/") : type != null && type.startsWith("image/");
        if (!validType || extension == null || file.getSize() > (video ? MAX_VIDEO : MAX_IMAGE))
            throw new AppException(ErrorCode.VALIDATION_ERROR, video ? "Video phải là MP4/WebM và không quá 150 MB." : "Ảnh phải là JPG/PNG/WebP và không quá 5 MB.");
        try {
            if (!video && ImageIO.read(file.getInputStream()) == null) throw new IOException("Invalid image");
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + extension;
            file.transferTo(directory.resolve(filename));
            return "/api/public/community-media/" + filename;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Không thể lưu tệp đã tải lên.");
        }
    }

    public Path locate(String filename) {
        if (filename == null || !filename.matches("[a-f0-9-]{36}\\.(jpg|png|webp|mp4|webm)")) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        Path result = directory.resolve(filename).normalize();
        if (!result.startsWith(directory) || !Files.isRegularFile(result)) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return result;
    }
}
