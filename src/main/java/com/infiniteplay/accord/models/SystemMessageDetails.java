package com.infiniteplay.accord.models;

import com.infiniteplay.accord.entities.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@AllArgsConstructor
public class SystemMessageDetails {
    String messageType;
    @Nullable User associatedUser;
    @Nullable User secondaryAssociatedUser;
    @Nullable String additionalMessage;
}
