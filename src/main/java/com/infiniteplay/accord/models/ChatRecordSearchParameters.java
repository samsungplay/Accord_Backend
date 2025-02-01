package com.infiniteplay.accord.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class ChatRecordSearchParameters {
    private Integer cursorId;
    private Boolean previous;
    private SearchOrder order;
    private String content;
    private List<String> tags;
    private String localTimezone;
}
