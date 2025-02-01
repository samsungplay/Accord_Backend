package com.infiniteplay.accord.listeners;


import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.UserStatus;
import com.infiniteplay.accord.services.*;
import com.infiniteplay.accord.utils.GenericException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class SocketEventListener {
    @Autowired
    private UserService userService;
    @Autowired
    private SimpMessagingTemplate broker;
    @Autowired
    private RateLimiterService rateLimiterService;
    @Autowired
    private CallService callService;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private JanusService janusService;
    @Qualifier("socketSessionMap")
    @Autowired
    private Map socketSessionMap;


    @EventListener
    private void onClientConnected(SessionConnectedEvent event) {
        if (event.getUser() != null) {




            janusService.createConnection(userService.extractId(event.getUser().getName()));




        }

    }


    @EventListener
    private void onClientDisconnected(SessionDisconnectEvent event) {
        if (event.getUser() != null) {
            userService.updateStatus(event.getUser().getName(), UserStatus.OFFLINE);

            rateLimiterService.freeCache("dispatchMessage", event.getUser().getName());

            try {
                callService.leaveCall(event.getUser().getName());
            } catch (RuntimeException e) {
                if (!e.getMessage().equals("There is no call ongoing for the user")) {
                    log.error(e.getMessage());
                }

            }

            janusService.destroyConnection(userService.extractId(event.getUser().getName()));

            socketSessionMap.remove(event.getUser().getName());
        }
    }
}
