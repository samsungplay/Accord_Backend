package com.infiniteplay.accord.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class UserException extends GenericException {
    public UserException(String message) {
        super(message);
    }
}
