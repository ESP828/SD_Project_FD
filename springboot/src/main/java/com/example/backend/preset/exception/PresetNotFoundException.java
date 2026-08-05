package com.example.backend.preset.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class PresetNotFoundException extends BusinessException {

    public PresetNotFoundException(Long presetId) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "프리셋을 찾을 수 없습니다. id=" + presetId);
    }
}
