package com.infiniteplay.accord.configs;

import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.crypto.SecretKey;

@Configuration
@ComponentScan(basePackages = {"com.infiniteplay.accord.security"})

public class JWTConfig {

    @Value("${jwt.secret}")
    private String JWT_SECRET;
    @Value("${jwt.refresh.secret}")
    private String JWT_REFRESH_SECRET;
    @Value("${jwt.oauth.registration.secret}")
    private String OAUTH_REGISTRATION_SECRET;

    @Bean(name="access_token_secret")
    public SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET));
    }

    @Bean(name = "refresh_token_secret")
    public SecretKey secretKeyRefresh() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_REFRESH_SECRET));
    }

    @Bean(name = "oauth_reg_token_secret")
    public SecretKey secretKeyOauthReg() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(OAUTH_REGISTRATION_SECRET));
    }
    @Bean
    public JwtParserBuilder parser() {
        return Jwts.parser();
    }


}
