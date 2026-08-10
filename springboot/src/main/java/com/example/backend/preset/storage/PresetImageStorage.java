package com.example.backend.preset.storage;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class PresetImageStorage {

    private final Path root;

    public PresetImageStorage(@Value("${app.upload.preset-image-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 업로드 폴더를 생성할 수 없습니다: " + root, e);
        }
    }

    public String save(MultipartFile file, String extension) {
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(storedFilename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지 저장에 실패했습니다.");
        }
        return storedFilename;
    }

    public void delete(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(storedFilename).normalize());
        } catch (IOException ignored) {
            // 파일 정리 실패는 이미지 교체/삭제 자체를 막을 이유가 아니다.
        }
    }
}
