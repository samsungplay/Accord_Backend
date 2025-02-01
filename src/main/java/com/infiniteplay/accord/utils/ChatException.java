package com.infiniteplay.accord.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class ChatException extends GenericException {
    public ChatException(String message) {
        super(message);
    }
}
