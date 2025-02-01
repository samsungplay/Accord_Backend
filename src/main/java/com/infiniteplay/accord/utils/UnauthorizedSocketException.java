package com.infiniteplay.accord.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class UnauthorizedSocketException extends RuntimeException {
    private final String message;
}
