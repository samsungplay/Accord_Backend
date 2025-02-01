package com.infiniteplay.accord;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;

public class Test {
    public static void main(String[] args) {
        String jwtSecret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        System.out.println(jwtSecret);
    }
}
