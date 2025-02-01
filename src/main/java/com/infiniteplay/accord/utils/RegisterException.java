package com.infiniteplay.accord.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public class RegisterException extends RuntimeException {
    private final String type;
    private final String message;

    public Map<String, String> toErrorData() {
        Map<String, String> errorData = new HashMap<>();
        errorData.put("type", type);
        errorData.put("message", message);
        return errorData;
    }
}
