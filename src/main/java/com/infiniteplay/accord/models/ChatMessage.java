package com.infiniteplay.accord.models;

import com.infiniteplay.accord.entities.ChatRecord;
import jakarta.annotation.Nullable;
import lombok.*;


@Getter
@AllArgsConstructor
public class ChatMessage {
    private String payload;
    private String replyTarget;
    private String replyTargetSenderId;
    private String replyTargetMessage;

}
