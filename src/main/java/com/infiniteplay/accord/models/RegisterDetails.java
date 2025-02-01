package com.infiniteplay.accord.models;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;


@AllArgsConstructor
@Getter
public class RegisterDetails {

    private final String email;
    private final String password;
    private final String username;
    private String nickname;
    private final Date birthDate;

}
