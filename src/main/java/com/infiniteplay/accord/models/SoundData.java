package com.infiniteplay.accord.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@AllArgsConstructor
public class SoundData {
    private String type;
    private String name;
    private String icon;
    private MultipartFile soundFile;
    private long duration;
}
