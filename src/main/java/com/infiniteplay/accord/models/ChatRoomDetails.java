package com.infiniteplay.accord.models;

import com.infiniteplay.accord.entities.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public class ChatRoomDetails {
    private int id;
    private String name;
    private List<String> participants;
    private boolean deleteRoomImage;
}
