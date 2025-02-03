package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JWTSuccessfulAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JWTHandler jwtHandler;
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${process.env}")
    private String processEnv;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if(SecurityContextHolder.getContext().getAuthentication() != null) {
            //issue a new jwt refresh token & jwt token
            logger.info("user=" + SecurityContextHolder.getContext().getAuthentication().getName());
            Authentication authResult = SecurityContextHolder.getContext().getAuthentication();
            if(authResult.getCredentials() != null && authResult.getCredentials().equals("jwt_verified")) {
                //simply proceed with the filter if previously issued jwt token already
                logger.info("skipping verification as jwt verified already");
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_CREATED);

            String refreshToken = jwtHandler.createToken(authResult.getName(), authResult.getAuthorities(), true);
            String accessToken = jwtHandler.createToken(authResult.getName(),authResult.getAuthorities(),false);


            ResponseCookie cookie1 = ResponseCookie.from("accord_access_token", accessToken)
                            .path("/")
                                    .sameSite(processEnv.equals("prod") ? "Lax" : "None")
                                            .httpOnly(true)
                                                    .secure(true)
                                                            .build();
            ResponseCookie cookie2 = ResponseCookie.from("accord_refresh_token", refreshToken)
                            .path("/")
                                    .sameSite(processEnv.equals("prod") ? "Lax" : "None")
                                            .httpOnly(true)
                                                    .secure(true)
                                                            .build();
            response.addHeader("Set-Cookie", cookie1.toString());
            response.addHeader("Set-Cookie", cookie2.toString());
            logger.info("Jwt token issued");
        }
        filterChain.doFilter(request, response);
    }
}
