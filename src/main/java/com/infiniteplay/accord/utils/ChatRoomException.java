package com.infiniteplay.accord.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class ChatRoomException extends GenericException {

    public ChatRoomException(String message) {
        super(message);
    }
}
