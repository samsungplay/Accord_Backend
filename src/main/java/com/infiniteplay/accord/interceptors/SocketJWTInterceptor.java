package com.infiniteplay.accord.interceptors;

import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.utils.UnauthorizedSocketException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SocketJWTInterceptor implements ChannelInterceptor {
    @Autowired
    private JWTHandler jwtHandler;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("Stompcommand CONNECT: " + accessor.getFirstNativeHeader("Authorization"));
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            if(authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);
                try {
                    Authentication authentication = jwtHandler.readAccessToken(token);
                    //jwt valid!
                    SecurityContext securityContext = SecurityContextHolder.getContext();
                    securityContext.setAuthentication(authentication);

                    accessor.setUser(authentication);

                    log.debug("JWT valid and have correctly set user information");


                } catch (ExpiredJwtException e) {
                    //jwt expired (i.e. access token expired)
                    throw new UnauthorizedSocketException("Access token expired");
                }
                catch(JwtException e) {
                    //jwt outright invalid; ignore
                    throw new UnauthorizedSocketException("Access token invalid");
                }
            }
            else {
                throw new UnauthorizedSocketException("Access token absent");
            }
        }
        return message;
    }
}
