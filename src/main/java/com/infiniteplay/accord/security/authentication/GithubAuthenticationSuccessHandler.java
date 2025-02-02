package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GithubAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    JWTHandler jwtHandler;
    @Value("${process.env}")
    String processEnv;
    @Value("${client.url}")
    String clientUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);

        String refreshToken = jwtHandler.createToken(authResult.getName(), authResult.getAuthorities(), true);
        String accessToken = jwtHandler.createToken(authResult.getName(),authResult.getAuthorities(),false);


//        Map<String,String> jwtMap = new HashMap<>();
//        jwtMap.put("refresh_token",refreshToken);
//        jwtMap.put("access_token",accessToken);

        ResponseCookie cookie = ResponseCookie.from("accord_access_token", accessToken)
                .path("/")
                .sameSite("Strict")
                .httpOnly(false)
                //dev only
                .secure(processEnv.equals("prod"))
                .build();
        ResponseCookie cookie2 = ResponseCookie.from("accord_refresh_token", refreshToken)
                .path("/")
                        .sameSite("Strict")
                                .httpOnly(false)
                                        .secure(processEnv.equals("prod"))
                                                .build();

//        response.getOutputStream().println(mapper.writeValueAsString(jwtMap));
        response.addHeader("Set-Cookie", cookie.toString());
        response.addHeader("Set-Cookie",cookie2.toString());
        response.setHeader("Location",clientUrl + "/dashboard");
        log.info("[OAuth 2.0 GITHUB] : Jwt token issued");
    }
}
