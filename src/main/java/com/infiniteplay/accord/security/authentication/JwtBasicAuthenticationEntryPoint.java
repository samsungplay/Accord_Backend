package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtBasicAuthenticationEntryPoint extends BasicAuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Map<String, String> errorJson = new HashMap<>();
        errorJson.put("error", failed.getMessage());
//        response.addHeader("WWW-Authenticate", "Basic realm=" + super.getRealmName());
        response.getOutputStream().println(mapper.writeValueAsString(errorJson));
    }

    @Override
    public void afterPropertiesSet() {
        super.setRealmName("accord");
        super.afterPropertiesSet();
    }
}
