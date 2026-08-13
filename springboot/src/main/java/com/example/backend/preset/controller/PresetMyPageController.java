package com.example.backend.preset.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.preset.dto.response.PresetSummaryResponse;
import com.example.backend.preset.service.PresetService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mypage/presets")
public class PresetMyPageController {

    private final PresetService presetService;

    public PresetMyPageController(PresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping
    public ApiResponse<List<PresetSummaryResponse>> getCreatedPresets(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(presetService.getCreatedPresets(account.accountId()));
    }
}
