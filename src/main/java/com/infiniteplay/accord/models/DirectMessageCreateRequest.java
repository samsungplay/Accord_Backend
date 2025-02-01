package com.infiniteplay.accord.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class DirectMessageCreateRequest {
    private List<String> friendNames;
    private String chatRoomName;
    private boolean dm;
}
