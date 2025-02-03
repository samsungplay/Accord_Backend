package com.infiniteplay.accord.configs;

import com.infiniteplay.accord.interceptors.SocketAuthenticationInterceptor;
import com.infiniteplay.accord.interceptors.SocketUsernameInterceptor;
import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.utils.GenericException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
public class SocketConfig implements WebSocketMessageBrokerConfigurer {


    @Autowired
    JWTHandler jwtHandler;
    @Value("${client.url}")
    private String clientUrl;
    @Autowired
    SocketAuthenticationInterceptor socketAuthenticationInterceptor;

    private Map<Integer, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    @Autowired
    private SocketUsernameInterceptor socketUsernameInterceptor;


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/general", "/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Bean(name = "socketSessionMap")
    public Map<Integer, WebSocketSession> sessionMap() {
        return sessionMap;
    }


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/socket").setAllowedOrigins("http://localhost:3000", clientUrl).addInterceptors(socketAuthenticationInterceptor);
        registry.addEndpoint("/socket").setAllowedOrigins("http://localhost:3000", clientUrl).addInterceptors(socketAuthenticationInterceptor).withSockJS();
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(socketUsernameInterceptor);
        WebSocketMessageBrokerConfigurer.super.configureClientInboundChannel(registration);
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

                        String query = session.getUri().getQuery();

                        int userId = 0;
                        String[] pairs = query.split("&");
                        for (String pair : pairs) {
                            int idx = pair.indexOf("=");
                            if (idx > 0) {
                                String key = pair.substring(0, idx);
                                String value = pair.substring(idx + 1);
                                if(key.equals("userId")) {
                                    userId = Integer.parseInt(value);
                                }
                            }
                        }

                        if(userId == 0) {
                            throw new GenericException("Invalid connection");
                        }

                        try {
                            if (sessionMap.containsKey(userId)) {
                                //duplicate session found, close the old connection
                                sessionMap.get(userId).close();
                            }
                            sessionMap.put(userId, session);
                        } catch (Exception e) {
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
