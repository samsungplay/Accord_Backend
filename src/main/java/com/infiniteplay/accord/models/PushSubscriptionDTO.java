package com.infiniteplay.accord.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushSubscriptionDTO {
    private String endpoint;
    private String loginSessionToken;
    private String p256dh;
    private String auth;
}
