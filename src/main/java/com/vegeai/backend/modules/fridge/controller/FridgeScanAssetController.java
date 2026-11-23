package com.vegeai.backend.modules.fridge.controller;

import com.vegeai.backend.modules.fridge.service.FridgeScanAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class FridgeScanAssetController {

    private final FridgeScanAssetService assets;

    @GetMapping("/api/public/fridge-scans/{filename}")
    public ResponseEntity<Resource> getScannedImage(@PathVariable String filename) {
        Path path = assets.locate(filename);
        MediaType mediaType = filename.endsWith(".png")
                ? MediaType.IMAGE_PNG
                : filename.endsWith(".webp")
                ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(path));
    }
}
