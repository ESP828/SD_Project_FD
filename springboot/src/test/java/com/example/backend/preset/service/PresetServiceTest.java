package com.example.backend.preset.service;

import com.example.backend.preset.dto.request.PresetCreateRequest;
import com.example.backend.preset.query.PresetImageQueryRepository;
import com.example.backend.preset.query.PresetQueryRepository;
import com.example.backend.preset.storage.PresetImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresetServiceTest {

    @Mock private PresetQueryRepository queryRepository;
    @Mock private PresetImageQueryRepository imageQueryRepository;
    @Mock private PresetImageStorage imageStorage;
    @InjectMocks private PresetService presetService;

    @Test
    void linksGeneratedImageIdToCreatedPreset() {
        PresetCreateRequest request = new PresetCreateRequest("테스트 Presset", "데이트", true);
        MockMultipartFile image = new MockMultipartFile(
                "image", "cover.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        when(queryRepository.create(11L, request)).thenReturn(31L);
        when(imageQueryRepository.findStoredFilename(31L)).thenReturn(Optional.empty());
        when(imageStorage.save(image, "png")).thenReturn("new-image.png");
        when(imageQueryRepository.replace(
                31L, "new-image.png", "cover.png", "image/png", 4L
        )).thenReturn(41L);
        when(queryRepository.linkImage(31L, 41L)).thenReturn(1);

        assertEquals(31L, presetService.createPreset(11L, request, image));

        var order = inOrder(queryRepository, imageQueryRepository);
        order.verify(queryRepository).create(11L, request);
        order.verify(imageQueryRepository).replace(
                31L, "new-image.png", "cover.png", "image/png", 4L
        );
        order.verify(queryRepository).linkImage(31L, 41L);
    }

    @Test
    void removesNewFileWhenImageMetadataInsertFails() {
        PresetCreateRequest request = new PresetCreateRequest("테스트 Presset", "데이트", true);
        MockMultipartFile image = new MockMultipartFile(
                "image", "cover.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        when(queryRepository.create(11L, request)).thenReturn(31L);
        when(imageQueryRepository.findStoredFilename(31L)).thenReturn(Optional.empty());
        when(imageStorage.save(image, "png")).thenReturn("new-image.png");
        doThrow(new DataIntegrityViolationException("preset_image insert failed"))
                .when(imageQueryRepository)
                .replace(31L, "new-image.png", "cover.png", "image/png", 4L);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> presetService.createPreset(11L, request, image)
        );

        verify(imageStorage).delete("new-image.png");
    }
}
