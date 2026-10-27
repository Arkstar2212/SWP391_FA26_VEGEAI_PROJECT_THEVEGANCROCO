package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.modules.community.service.CommunityMediaAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class CommunityMediaAssetController {
    private final CommunityMediaAssetService assets;
    @GetMapping("/api/public/community-media/{filename}")
    public ResponseEntity<FileSystemResource> asset(@PathVariable String filename) {
        var path = assets.locate(filename);
        String type = filename.endsWith(".mp4") ? "video/mp4" : filename.endsWith(".webm") ? "video/webm"
                : filename.endsWith(".png") ? "image/png" : filename.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(path));
    }
}
