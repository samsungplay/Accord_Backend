package com.infiniteplay.accord.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PollMessage {
    String question;
    List<String> answers;
    boolean allowMultiple;
    long duration;
}
