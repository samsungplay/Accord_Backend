package com.infiniteplay.accord.configs;

import com.infiniteplay.accord.models.ICECandidate;
import com.infiniteplay.accord.models.JanusSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class JanusConfig {
    @Bean
    public ConcurrentHashMap<Integer, JanusSession> janusSessionCache() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Map>> janusEventCache() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentHashMap<Integer, ConcurrentLinkedQueue<ICECandidate>> janusICECandidateCache() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentHashMap<Integer, ReentrantLock> lockMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
        return new RestTemplate();
    }

}
