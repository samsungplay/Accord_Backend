package com.infiniteplay.accord.interceptors;

import com.infiniteplay.accord.security.authentication.JWTHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class SocketAuthenticationInterceptor implements HandshakeInterceptor {
    @Autowired
    JWTHandler jwtHandler;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        HttpHeaders headers = request.getHeaders();
        if(headers.containsKey("cookie")) {
            System.out.println("handshake="+headers);
            //parse cookie values
            String accessToken = null;
            String[] entries = headers.get("cookie").get(0).split("; ");
            for(String entry : entries) {
                String[] split = entry.split("=");
                String key = split[0];
                String value = split[1];
                if(key.equals("accord_access_token")) {
                    accessToken = value;
                }
            }

            if(accessToken == null) {
                return false;
            }

            try {
                jwtHandler.readAccessToken(accessToken);

            }
            catch(Exception e) {
                return false;
            }

        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
