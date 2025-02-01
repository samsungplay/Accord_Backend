package com.infiniteplay.accord.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class JanusSession {

    private String sessionId;
    private String handleId;
    private String secondaryHandleId;
}
