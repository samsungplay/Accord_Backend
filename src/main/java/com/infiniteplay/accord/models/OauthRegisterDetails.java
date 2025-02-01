package com.infiniteplay.accord.models;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

@AllArgsConstructor
@Getter
public class OauthRegisterDetails {
    private final String email;
    private final String username;
    private final String nickname;
    private final Date birthDate;
    private final String registrationToken;
}
