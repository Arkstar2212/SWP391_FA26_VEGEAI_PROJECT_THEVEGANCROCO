package com.vegeai.backend.modules.interactions.controller;

import com.vegeai.backend.modules.interactions.service.ContentInteractionAssetService;
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
public class ContentInteractionAssetController {
    private final ContentInteractionAssetService assets;

    @GetMapping("/api/public/content-interactions/assets/{filename}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) {
        Path path = assets.locate(filename);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(path));
    }
}
