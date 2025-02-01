package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if(SecurityContextHolder.getContext().getAuthentication() != null) {
            //issue a new jwt refresh token & jwt token
            Authentication authResult = SecurityContextHolder.getContext().getAuthentication();
            if(authResult.getCredentials() != null && authResult.getCredentials().equals("jwt_verified")) {
                //simply proceed with the filter if previously issued jwt token already
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_CREATED);

            String refreshToken = jwtHandler.createToken(authResult.getName(), authResult.getAuthorities(), true);
            String accessToken = jwtHandler.createToken(authResult.getName(),authResult.getAuthorities(),false);

            Map<String,String> jwtMap = new HashMap<>();
            jwtMap.put("refresh_token",refreshToken);
            jwtMap.put("access_token",accessToken);

            response.getOutputStream().println(mapper.writeValueAsString(jwtMap));

            logger.info("Jwt token issued");
        }
        filterChain.doFilter(request, response);
    }
}
