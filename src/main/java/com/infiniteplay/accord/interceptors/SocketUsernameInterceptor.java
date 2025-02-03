package com.infiniteplay.accord.interceptors;

import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.utils.UnauthorizedSocketException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SocketUsernameInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("Stompcommand CONNECT: " + accessor.getFirstNativeHeader("Username"));
            String authorizationHeader = accessor.getFirstNativeHeader("Username");
            if(authorizationHeader != null) {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(authorizationHeader, "jwt_verified");

                    accessor.setUser(authentication);

                    log.debug("JWT valid and have correctly set user information");
            }
            else {
                throw new UnauthorizedSocketException("Access token absent");
            }
        }
        return message;
    }
}
