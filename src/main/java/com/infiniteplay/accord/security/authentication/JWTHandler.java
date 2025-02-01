package com.infiniteplay.accord.security.authentication;


import com.infiniteplay.accord.entities.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JWTHandler {

    //todo: finish jwt authentication


    private final SecretKey key;
    private final SecretKey refreshKey;
    private final SecretKey oauthRegistrationKey;

    private final JwtParserBuilder parser;

    public JWTHandler(@Qualifier("access_token_secret") SecretKey key, @Qualifier("refresh_token_secret") SecretKey refreshKey, @Qualifier("oauth_reg_token_secret") SecretKey oauthKey, JwtParserBuilder parser) {
        this.key = key;
        this.refreshKey = refreshKey;
        this.parser = parser;
        this.oauthRegistrationKey = oauthKey;
    }


    public String createGithubRegistrationToken(String accountId) {
        JwtBuilder builder = Jwts.builder();
        //20 minutes
        Date expiration = new Date(System.currentTimeMillis() + 1000 * 60 * 20);

        builder.subject(accountId)
                .issuedAt(new Date())
                .claim("registrationType","github")
                .expiration(expiration)
                .signWith(oauthRegistrationKey);

        return builder.compact();

    }

    public String createToken(String user, Collection<? extends GrantedAuthority> authorities, boolean refresh) {



        JwtBuilder builder = Jwts.builder();

        String authoritiesString = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        //1d
        Date refreshTokenExpiration = new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24);
        //1hr
        Date acccessTokenExpiration = new Date(System.currentTimeMillis() + 1000 * 60 * 60);
        builder.subject(user)
                .issuedAt(new Date())
                .claim("authorities", authoritiesString)
                //lasts 5hrs
                .expiration(refresh ? refreshTokenExpiration : acccessTokenExpiration)
                .signWith(refresh ? refreshKey : key);


        return builder.compact();
    }


    public int isValidGithubOauthToken(String token) {
        try {
            Jws<Claims> jws = parser.verifyWith(oauthRegistrationKey).build().parseSignedClaims(token);

            if(!jws.getPayload().get("registrationType").equals("github")) {
                return -1;
            }
            return Integer.parseInt(jws.getPayload().getSubject());
        }
        catch (JwtException | IllegalArgumentException e) {
            return -1;
        }
    }


    public Authentication readAccessToken(String token) throws JwtException {

        Jws<Claims> jws = parser.verifyWith(key).build().parseSignedClaims(token);

        List<SimpleGrantedAuthority> authorities = Arrays.stream(jws.getPayload().get("authorities").toString().split(",")).map(SimpleGrantedAuthority::new).toList();

        return new UsernamePasswordAuthenticationToken(jws.getPayload().getSubject(),"jwt_verified",authorities);

    }

    public Authentication readRefreshToken(String token) throws JwtException {
        Jws<Claims> jws = parser.verifyWith(refreshKey).build().parseSignedClaims(token);

        List<SimpleGrantedAuthority> authorities = Arrays.stream(jws.getPayload().get("authorities").toString().split(",")).map(SimpleGrantedAuthority::new).toList();

        return new UsernamePasswordAuthenticationToken(jws.getPayload().getSubject(),"jwt_verified",authorities);
    }


}
