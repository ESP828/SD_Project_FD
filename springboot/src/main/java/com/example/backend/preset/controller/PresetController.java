package com.example.backend.preset.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.preset.dto.request.PresetCreateRequest;
import com.example.backend.preset.dto.response.FavoriteStateResponse;
import com.example.backend.preset.dto.response.PresetDetailResponse;
import com.example.backend.preset.dto.response.PresetMapResponse;
import com.example.backend.preset.dto.response.PresetPageResponse;
import com.example.backend.preset.dto.response.PresetTagResponse;
import com.example.backend.preset.service.PresetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/presets")
public class PresetController {

    private final PresetService presetService;

    public PresetController(PresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping
    public ApiResponse<PresetPageResponse> getPresets(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(presetService.getPresets(
                accountId(account), page, size, sort, tagId, keyword
        ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createPreset(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestPart("data") PresetCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ApiResponse.success(
                "프리셋을 등록했습니다.",
                presetService.createPreset(account.accountId(), request, image)
        );
    }

    @PutMapping(value = "/{presetId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> updatePreset(
            @PathVariable Long presetId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestPart("data") PresetCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        presetService.updatePreset(presetId, account.accountId(), request, image);
        return ApiResponse.success("프리셋을 수정했습니다.", null);
    }

    @GetMapping("/tags")
    public ApiResponse<List<PresetTagResponse>> getTags() {
        return ApiResponse.success(presetService.getFilterTags());
    }

    @GetMapping("/{presetId}")
    public ApiResponse<PresetDetailResponse> getPreset(
            @PathVariable Long presetId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(presetService.getPreset(presetId, accountId(account)));
    }

    @GetMapping("/{presetId}/map-restaurants")
    public ApiResponse<PresetMapResponse> getMapRestaurants(
            @PathVariable Long presetId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(presetService.getMapPreset(presetId, accountId(account)));
    }

    @PostMapping("/{presetId}/favorite")
    public ApiResponse<FavoriteStateResponse> addFavorite(
            @PathVariable Long presetId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(
                "Presset을 저장했습니다.",
                presetService.addFavorite(presetId, account.accountId())
        );
    }

    @DeleteMapping("/{presetId}/favorite")
    public ApiResponse<FavoriteStateResponse> removeFavorite(
            @PathVariable Long presetId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(
                "Presset 저장을 해제했습니다.",
                presetService.removeFavorite(presetId, account.accountId())
        );
    }

    private static Long accountId(AuthenticatedAccount account) {
        return account == null ? null : account.accountId();
    }
}
