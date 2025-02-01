package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GithubAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private JWTHandler jwtHandler;
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        if(exception.getMessage().contains("302")) {
            //redirect back to client
         response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
         String username = exception.getMessage().split(" ")[1];
         String accountId = exception.getMessage().split(" ")[2];
         String token = jwtHandler.createGithubRegistrationToken(accountId);
         response.setHeader("Location","http://localhost:3000/authentication?register=true&github=" + username);
            ResponseCookie cookie = ResponseCookie.from("github-registration-token", token)
                    .path("/")
                    .sameSite("Strict")
                    .httpOnly(false)
                    //dev only
                    .secure(false)
                    .build();
        response.setHeader("Set-Cookie", cookie.toString());
     }
    }
}
