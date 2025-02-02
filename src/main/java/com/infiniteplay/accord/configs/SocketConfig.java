package com.infiniteplay.accord.configs;

import com.infiniteplay.accord.interceptors.SocketJWTInterceptor;
import com.infiniteplay.accord.security.authentication.JWTHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.sockjs.transport.session.WebSocketServerSockJsSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
public class SocketConfig  implements WebSocketMessageBrokerConfigurer {


    @Autowired
    private SocketJWTInterceptor socketJWTInterceptor;
    @Autowired
    JWTHandler jwtHandler;
    @Value("${client.url}")
    private String clientUrl;

    private Map<Integer, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public SocketConfig(SocketJWTInterceptor socketJWTInterceptor) {
        this.socketJWTInterceptor = socketJWTInterceptor;
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/general","/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Bean(name = "socketSessionMap")
    public Map<Integer, WebSocketSession> sessionMap() {
        return sessionMap;
    }



    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/socket").setAllowedOrigins("http://localhost:3000",clientUrl);
        registry.addEndpoint("/socket").setAllowedOrigins("http://localhost:3000",clientUrl).withSockJS();
    }


    //duplicate session handler
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(final WebSocketHandler handler) {

                return new WebSocketHandlerDecorator(handler) {

                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

                        System.out.println("socket connection query=" + session.getUri().getQuery());
                        int userId = Integer.parseInt(session.getUri().getQuery().split("=")[1]);

                        try {
                            if(sessionMap.containsKey(userId)) {
                                //duplicate session found, close the old connection
                                sessionMap.get(userId).close();
                            }
                            sessionMap.put(userId, session);
                        }
                        catch(Exception e) {
                            session.close();
                            e.printStackTrace();
                        }
                        super.afterConnectionEstablished(session);

                    }
                };
            }
        });
        WebSocketMessageBrokerConfigurer.super.configureWebSocketTransport(registry);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(socketJWTInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        WebSocketMessageBrokerConfigurer.super.configureClientOutboundChannel(registration);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        WebSocketMessageBrokerConfigurer.super.addArgumentResolvers(argumentResolvers);
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        WebSocketMessageBrokerConfigurer.super.addReturnValueHandlers(returnValueHandlers);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        return WebSocketMessageBrokerConfigurer.super.configureMessageConverters(messageConverters);
    }



}
