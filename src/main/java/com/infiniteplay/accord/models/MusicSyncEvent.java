package com.infiniteplay.accord.models;

import lombok.Data;

@Data
public class MusicSyncEvent {
    private String type;
    private long timestamp;
    private long time;
    private String src;
}
