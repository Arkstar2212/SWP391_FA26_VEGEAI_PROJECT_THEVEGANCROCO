package com.vegeai.backend.modules.interactions.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Iterator;

@Service
public class ContentInteractionAssetService {
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private final Path uploadDirectory;

    public ContentInteractionAssetService(
            @Value("${app.content-interactions.upload-dir:./uploads/content-interactions}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public List<String> store(List<MultipartFile> photos) {
        if (photos == null || photos.isEmpty()) return new ArrayList<>();
        if (photos.size() > 4) throw new AppException(ErrorCode.VALIDATION_ERROR, "A review can include up to four photos.");
        List<String> stored = new ArrayList<>();
        try {
            Files.createDirectories(uploadDirectory);
            for (MultipartFile photo : photos) {
                if (photo.isEmpty() || photo.getSize() > MAX_IMAGE_SIZE)
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Each review photo must be between 1 byte and 5 MB.");
                String extension = imageExtension(photo);
                String filename = UUID.randomUUID() + extension;
                Path destination = uploadDirectory.resolve(filename);
                photo.transferTo(destination);
                stored.add("/api/public/content-interactions/assets/" + filename);
            }
            return stored;
        } catch (IOException exception) {
            delete(stored);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "The review photos could not be saved.");
        } catch (RuntimeException exception) {
            delete(stored);
            throw exception;
        }
    }

    public Path locate(String filename) {
        if (filename == null || !filename.matches("[a-f0-9-]{36}\\.(png|jpg)"))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        Path path = uploadDirectory.resolve(filename).normalize();
        if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return path;
    }

    public void delete(List<String> urls) {
        if (urls == null) return;
        for (String url : urls) {
            if (url == null) continue;
            String filename = url.substring(url.lastIndexOf('/') + 1);
            try {
                Files.deleteIfExists(locate(filename));
            } catch (IOException exception) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "A review photo could not be removed.");
            } catch (AppException ignored) {
                // A missing photo must not prevent removing its associated comment.
            }
        }
    }

    private String imageExtension(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String extension = "image/png".equals(contentType) ? ".png"
                : "image/jpeg".equals(contentType) ? ".jpg" : null;
        if (extension == null)
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Review photos must be valid JPG or PNG images.");
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null)
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Review photos must be valid JPG or PNG images.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new AppException(ErrorCode.VALIDATION_ERROR, "Review photos must be valid JPG or PNG images.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 25_000_000L)
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Review photos must be no larger than 25 megapixels.");
            } finally {
                reader.dispose();
            }
        }
        return extension;
    }
}
