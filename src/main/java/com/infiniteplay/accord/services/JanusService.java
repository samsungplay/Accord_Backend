package com.infiniteplay.accord.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteplay.accord.models.ICECandidate;
import com.infiniteplay.accord.models.JanusSession;
import com.infiniteplay.accord.utils.GenericException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class JanusService {

    private static final Logger log = LoggerFactory.getLogger(JanusService.class);
    @Autowired
    private RestTemplate restTemplate;

    @Value("${janus.endpoint.url}")
    private String JANUS_ENDPOINT;
    @Autowired
    private ConcurrentHashMap<Integer, JanusSession> janusCache;
    @Autowired
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Map>> eventCache;
    @Qualifier("lockMap")
    @Autowired
    private ConcurrentHashMap<Integer, ReentrantLock> lockMap;
    @Qualifier("janusICECandidateCache")
    @Autowired
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<ICECandidate>> janusICECandidateCache;

    private final ObjectMapper mapper = new ObjectMapper();

    private boolean isResponseValid(Map responseBody) {
        if (responseBody.get("janus").equals("error")) {
            return false;
        }
        if (responseBody.get("data") instanceof Map && ((Map<?, ?>) responseBody.get("data")).containsKey("error")) {
            log.error("error while testing janus response: " + ((Map<?, ?>) responseBody.get("data")).get("error"));
            return false;
        }
        if (responseBody.get("plugindata") instanceof Map<?, ?>) {
            Map plugindata = (Map) responseBody.get("plugindata");

            if (plugindata.get("data") instanceof Map<?, ?> && ((Map<?, ?>) plugindata.get("data")).containsKey("error")) {
                log.error("error while testing janus response: " + ((Map<?, ?>) plugindata.get("data")).get("error"));
                return false;
            }
        }
        return true;
    }


    private boolean isResponseValid(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return false;
        }
        if (response.getBody() == null) {
            return false;
        }
        if (response.getBody().get("janus").equals("error")) {
            return false;
        }
        if (response.getBody().get("data") instanceof Map && ((Map<?, ?>) response.getBody().get("data")).containsKey("error")) {
            log.error("error while testing janus response: " + ((Map<?, ?>) response.getBody().get("data")).get("error"));
            return false;
        }
        if (response.getBody().get("plugindata") instanceof Map<?, ?>) {
            Map plugindata = (Map) response.getBody().get("plugindata");

            if (plugindata.get("data") instanceof Map<?, ?> && ((Map<?, ?>) plugindata.get("data")).containsKey("error")) {
                log.error("error while testing janus response: " + ((Map<?, ?>) plugindata.get("data")).get("error"));
                return false;
            }
        }
        return true;
    }

    public JanusSession getUserSession(int userId) throws GenericException {
        JanusSession session = janusCache.get(userId);
        if (session == null) {
            throw new GenericException("Janus session not found");
        }
        return session;
    }

    public void createConnection(int userId) throws GenericException {
        try {
            String sessionId = createSession();
            String handleId = attachPlugin(sessionId);
            String secondaryHandleId = attachPlugin(sessionId);
            janusCache.put(userId, new JanusSession(sessionId, handleId, secondaryHandleId));
            eventCache.put(userId, new ConcurrentLinkedQueue<>());
            janusICECandidateCache.put(userId, new ConcurrentLinkedQueue<>());
        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }

    public void destroyConnection(int userId) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            janusCache.remove(userId);
            eventCache.remove(userId);
            janusICECandidateCache.remove(userId);
            detachPlugin(session.getSessionId(), session.getHandleId());
            detachPlugin(session.getSessionId(), session.getSecondaryHandleId());
            destroySession(session.getSessionId());
        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }

    public String createSession() throws GenericException {
        ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT, Map.of("janus", "create", "transaction", UUID.randomUUID().toString()), Map.class);

        if (!isResponseValid(response)) {
            throw new GenericException("Failed to create janus session");
        }
        Long sessionId = ((Map<String, Long>) response.getBody().get("data")).get("id");


        return sessionId.toString();
    }

    public String attachPlugin(String sessionId) throws GenericException {
        ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + sessionId, Map.of("janus", "attach", "plugin", "janus.plugin.videoroom", "transaction", UUID.randomUUID().toString()), Map.class);
        if (!isResponseValid(response)) {
            throw new GenericException("Failed to attach janus plugin");
        }
        Long handleId = ((Map<String, Long>) response.getBody().get("data")).get("id");

        return handleId.toString();
    }

    public void refreshPlugin(int userId, boolean primaryHandle, boolean secondaryHandle) throws GenericException {
        JanusSession session = getUserSession(userId);
        if (primaryHandle)
            detachPlugin(session.getSessionId(), session.getHandleId());
        if (secondaryHandle)
            detachPlugin(session.getSessionId(), session.getSecondaryHandleId());
        //clear the publisher's ice candidate cache as user must be leaving the room and therefore unpublishing his media (reattaching the primary handle implies this), if he was in any
        if(primaryHandle)
            janusICECandidateCache.get(userId).clear();

        if(primaryHandle) {
            String handleId = attachPlugin(session.getSessionId());
            session.setHandleId(handleId);
        }

        if(secondaryHandle) {
            String secondaryHandleId = attachPlugin(session.getSessionId());
            session.setSecondaryHandleId(secondaryHandleId);
        }



    }


    public void destroySession(String sessionId) throws GenericException {
        ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + sessionId, Map.of("janus", "destroy", "transaction", UUID.randomUUID().toString()), Map.class);

        if (!isResponseValid(response)) {
            throw new GenericException("Failed to destroy janus session");
        }
    }

    public void detachPlugin(String sessionId, String handleId) throws GenericException {
        ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + sessionId + "/" + handleId, Map.of("janus", "detach", "transaction", UUID.randomUUID().toString()), Map.class);

        if (!isResponseValid(response)) {
            throw new GenericException("Failed to detach janus plugin");
        }
    }

    public void sendKeepAlive(int userId) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId(), Map.of("janus", "keepalive", "transaction", UUID.randomUUID().toString()), Map.class);

            if (!isResponseValid(response)) {
                throw new GenericException("Failed to send keep alive on janus session");
            }
        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server is offline");
        }

    }

    public Integer createRoom(int userId, int chatRoomId) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "message", "transaction", UUID.randomUUID().toString(),
                            "body", Map.of("request", "create", "room", chatRoomId, "description", "none",
                                    "bitrate", 128000, "publishers", 10, "is_private", false, "permanent", false,
                                    "videocodec","av1,vp9,vp8,h264")), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to create janus room");
            }

            Map<String, Object> pluginData = (Map<String, Object>) response.getBody().get("plugindata");
            Map<String, Object> data = (Map<String, Object>) pluginData.get("data");
            int roomId = (int) data.get("room");

//            log.info("janus room created.");

            return roomId;
        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server is offline");
        }
    }

    public void destroyRoom(int userId, int chatRoomId) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "message", "transaction", UUID.randomUUID().toString(),
                            "body", Map.of("request", "destroy", "room", chatRoomId)), Map.class);
            if (!isResponseValid(response)) {
                System.out.println(response.getBody());
                throw new GenericException("Failed to destroy janus room");
            }
//            log.info("janus room destroyed.");

        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server is offline");
        }
    }

    public Map<String, Object> longPoll(int userId, Predicate<Map> predicate, long timeout, String sessionId, String transactionID) throws GenericException {

        ReentrantLock lock = lockMap.computeIfAbsent(userId, k -> new ReentrantLock(true));
        lock.lock();

        try {
            if (!eventCache.containsKey(userId)) {
                throw new GenericException("User janus session invalid");
            }

            //try searching for the cache first
            for (Map eventResponse : eventCache.get(userId)) {

                if (eventResponse.containsKey("transaction") && eventResponse.get("transaction").equals(transactionID)) {
                    if (!isResponseValid(eventResponse) || !predicate.test(eventResponse)) {
                        log.error("error while long polling: " + eventResponse);
                        throw new GenericException("Long poll failed");
                    }
                    eventCache.get(userId).remove(eventResponse);
                    return eventResponse;
                } else if (eventResponse.containsKey("session_id") && eventResponse.get("session_id").equals(sessionId)
                        && eventResponse.containsKey("janus") && eventResponse.get("janus").equals("media")
                        && transactionID.equals("media_" + sessionId)) {
                    if (!isResponseValid(eventResponse) || !predicate.test(eventResponse)) {
                        log.error("error while long polling: " + eventResponse);
                        throw new GenericException("Long poll failed");
                    }
                    eventCache.get(userId).remove(eventResponse);
                    return eventResponse;
                }
            }

            long target = System.currentTimeMillis() + timeout;
            while (System.currentTimeMillis() < target) {
                ResponseEntity<Map> resp = restTemplate.getForEntity(JANUS_ENDPOINT + "/" + sessionId, Map.class);
                Map<String, Object> eventResponse = resp.getBody();
                log.info("long polling :: " + eventResponse);
                if (eventResponse != null && eventResponse.containsKey("transaction")) {
                    if (eventResponse.get("transaction").equals(transactionID)) {
                        //found the event response!
                        if (!predicate.test(eventResponse) || !isResponseValid(eventResponse)) {
                            log.error("error while long polling: " + eventResponse);
                            throw new GenericException("Long poll failed");
                        }
                        return eventResponse;
                    } else if (!eventResponse.get("transaction").equals(transactionID)) {
                        //different event response, potentially might need it later. save it
                        eventCache.get(userId).offer(eventResponse);
                    }
                } else if (eventResponse != null && eventResponse.containsKey("session_id") &&
                        eventResponse.get("session_id").toString().equals(sessionId) &&
                        eventResponse.containsKey("janus") && eventResponse.get("janus").equals("media")
                ) {


                    if (transactionID.equals("media_" + sessionId)) {
                        if (!predicate.test(eventResponse) || !isResponseValid(eventResponse)) {
                            log.error("error while long polling: " + eventResponse);
                            throw new GenericException("Long poll failed");
                        }
                        return eventResponse;
                    } else if (!transactionID.equals("media_" + sessionId)) {
                        eventCache.get(userId).offer(eventResponse);
                    }

                }


                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new GenericException("Long poll failed");
                }
            }


            throw new GenericException("Long poll failed");
        } finally {
            lock.unlock();
            lockMap.computeIfPresent(userId, (k, existing) -> existing.hasQueuedThreads() ? existing : null);
        }
    }

//    public void unpublishMedia(int userId) throws GenericException {
//        try {
//            JanusSession session = getUserSession(userId);
//            String transactionID = UUID.randomUUID().toString();
//            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
//                    Map.of("janus", "message", "body", Map.of("request", "unpublish"),"transaction",transactionID), Map.class);
//            if(!isResponseValid(response)) {
//                throw new GenericException("Failed to unpublish media");
//            }
//
//
//            longPoll(userId, (eventResponse) -> eventResponse.get("plugindata") instanceof Map && ((Map<?, ?>) eventResponse.get("plugindata")).get("data") instanceof Map
//                    && ((Map) ((Map<?, ?>) eventResponse.get("plugindata")).get("data")).get("unpublished").equals("ok"),10000,session.getSessionId(),transactionID);
//        }
//        catch(ResourceAccessException e) {
//            throw new GenericException("Janus server is offline");
//        }
//    }

    public String publishMedia(int userId, String sdpOffer) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            String transactionID = UUID.randomUUID().toString();
            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "message", "body", Map.of("request", "publish", "audio", true, "video", false, "bitrate", 128000
                                    ),
                            "jsep", Map.of("type", "offer", "sdp", sdpOffer), "transaction", transactionID), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to publish media");
            }
            Map<String, Object> eventResponse = longPoll(userId, (resp) -> {
                if (!(resp.get("jsep") instanceof Map)) {
                    return false;
                }
                Map<String, Object> jsep = (Map<String, Object>) resp.get("jsep");
                return jsep.containsKey("type") && jsep.get("type").equals("answer") && jsep.containsKey("sdp");
            }, 10000, session.getSessionId(), transactionID);

            return ((Map<String, Object>) eventResponse.get("jsep")).get("sdp").toString();
        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }

    public void publishIceCandidate(int userId, int roomId, List<ICECandidate> iceCandidate) throws GenericException {
        if (!janusICECandidateCache.containsKey(userId)) {
            throw new GenericException("ICE Session does not exist");
        }
        try {
            JanusSession session = getUserSession(userId);
            List<Map> iceCandidatesAsMaps = iceCandidate.stream().map(ice -> mapper.convertValue(ice, Map.class)).collect(Collectors.toCollection(ArrayList::new));
            String transactionID = UUID.randomUUID().toString();
//            log.info("publishing ice candidate: " + iceCandidatesAsMaps);
            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "trickle", "transaction", transactionID, "candidates",
                            iceCandidatesAsMaps
                    ), Map.class);
            if (!isResponseValid(response)) {

                throw new GenericException("Failed to publish ice candidate");
            }

            transactionID = UUID.randomUUID().toString();
//            log.info("finalizing ice candidate publication.");
            response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "trickle", "transaction", transactionID, "candidate",
                            Map.of("completed", true)
                    ), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to publish ice candidate");
            } else {
//                log.info(response.toString());
            }

            longPoll(userId, (eventResponse) -> {
                Boolean receiving = (Boolean) eventResponse.get("receiving");

                return receiving != null && receiving;

            }, 10000, session.getSessionId(), "media_" + session.getSessionId());


            for (ICECandidate ice : iceCandidate)
                janusICECandidateCache.get(userId).offer(ice);

        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }


//    public String subscribeMedia(int userId, int chatRoomId, List<Integer> publisherIds) throws GenericException {
//        try {
//            JanusSession session = getUserSession(userId);
//            String transactionID = UUID.randomUUID().toString();
//            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
//                    Map.of("janus", "message", "transaction", transactionID, "body",
//                            Map.of("request", "subscribe", "feed", publisherIds.stream().map((id) -> Map.of("feed", id)).collect(Collectors.toCollection(ArrayList::new)))), Map.class);
//            if (!isResponseValid(response)) {
//                throw new GenericException("Failed to subscribe media");
//            } else {
//                log.info("subcribing media: " + response.getBody());
//            }
//
//            Map<String, Object> result = longPoll(userId, (eventResponse) -> {
//                Map<String, Object> pluginData = (Map<String, Object>) eventResponse.get("plugindata");
//                if (pluginData == null) {
//                    return false;
//                }
//                Map<String, Object> data = (Map<String, Object>) pluginData.get("data");
//                if (data == null) {
//                    return false;
//                }
//                if (!data.containsKey("videoroom")) {
//                    return false;
//                }
//
//                String command = data.get("videoroom").toString();
//                if (!command.equals("updated")) {
//                    return false;
//                }
//
//                if (!(eventResponse.get("jsep") instanceof Map)) {
//                    return false;
//                }
//                Map<String, Object> jsep = (Map<String, Object>) eventResponse.get("jsep");
//                return jsep.containsKey("type") && jsep.get("type").equals("offer") && jsep.containsKey("sdp");
//            }, 10000, session.getSessionId(), transactionID);
//
//            log.info("successfully joined room as subscriber: " + result);
//
//            return ((Map<String, Object>) result.get("jsep")).get("sdp").toString();
//        } catch (ResourceAccessException e) {
//            throw new GenericException("Janus server offline");
//        }
//    }

    public List<ICECandidate> getIceCandidates(int userId) throws GenericException {
        if (!janusICECandidateCache.containsKey(userId)) {
            throw new GenericException("No session found");
        }

        return new ArrayList<>(janusICECandidateCache.get(userId));
    }

    public void finalizeSubscription(int userId, String sdpAnswer) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            String transactionID = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getSecondaryHandleId(),
                    Map.of("janus", "message", "transaction", transactionID,
                            "body", Map.of("request", "start"), "jsep", Map.of("type", "answer", "sdp", sdpAnswer)), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to join janus room as a subscriber");
            }

            longPoll(userId, (eventResponse) -> {
                Map<String, Object> pluginData = (Map<String, Object>) eventResponse.get("plugindata");
                if (pluginData == null) {
                    return false;
                }
                Map<String, Object> data = (Map<String, Object>) pluginData.get("data");
                if (data == null) {
                    return false;
                }
                if (!data.containsKey("videoroom")) {
                    return false;
                }

                String command = data.get("videoroom").toString();
                if (!command.equals("event")) {
                    return false;
                }

                return data.containsKey("started") && data.get("started").equals("ok");
            }, 10000, session.getSessionId(), transactionID);

        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }

    public String joinRoomAsSubscriber(int userId, int chatRoomId, List<Integer> publisherIds) throws GenericException {
        try {
            JanusSession session = getUserSession(userId);
            String transactionID = UUID.randomUUID().toString();

            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getSecondaryHandleId(),
                    Map.of("janus", "message", "transaction", transactionID,
                            "body", Map.of("request", "join", "room", chatRoomId, "ptype", "subscriber","use_msid",true,
                                    "autoupdate", false, "streams", publisherIds.stream().map((id) -> Map.of("feed", id)).collect(Collectors.toCollection(ArrayList::new)))), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to join janus room as a subscriber");
            }

            log.info("joining room as subscriber...: ");

            Map<String, Object> result = longPoll(userId, (eventResponse) -> {
                Map<String, Object> pluginData = (Map<String, Object>) eventResponse.get("plugindata");
                if (pluginData == null) {
                    return false;
                }
                Map<String, Object> data = (Map<String, Object>) pluginData.get("data");
                if (data == null) {
                    return false;
                }
                if (!data.containsKey("videoroom")) {
                    return false;
                }

                String command = data.get("videoroom").toString();
                if (!command.equals("attached")) {
                    return false;
                }

                if (!(eventResponse.get("jsep") instanceof Map)) {
                    return false;
                }
                Map<String, Object> jsep = (Map<String, Object>) eventResponse.get("jsep");
                return jsep.containsKey("type") && jsep.get("type").equals("offer") && jsep.containsKey("sdp");
            }, 10000, session.getSessionId(), transactionID);

            log.info("successfully joined room as subscriber: " + result);

            return ((Map<String, Object>) result.get("jsep")).get("sdp").toString();

        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server offline");
        }
    }


    public void joinRoomAsPublisher(int userId, int chatRoomId) throws GenericException {
        try {
            //first, join the room
            JanusSession session = getUserSession(userId);
            String transactionID = UUID.randomUUID().toString();


            ResponseEntity<Map> response = restTemplate.postForEntity(JANUS_ENDPOINT + "/" + session.getSessionId() + "/" + session.getHandleId(),
                    Map.of("janus", "message", "transaction", transactionID,
                            "body", Map.of("request", "join", "room", chatRoomId, "ptype", "publisher", "id", userId
                            )), Map.class);
            if (!isResponseValid(response)) {
                throw new GenericException("Failed to join janus room as a publisher");
            }

//            log.info("attempting to join room: " + chatRoomId);

            Map<String, Object> result = longPoll(userId, (body) -> {
                Map<String, Object> pluginData = (Map<String, Object>) body.get("plugindata");
                if (pluginData == null) {
                    return false;
                }
                Map<String, Object> data = (Map<String, Object>) pluginData.get("data");
                if (data == null) {
                    return false;
                }

                if (!data.containsKey("videoroom")) {
                    return false;
                }

                String command = data.get("videoroom").toString();
                if (!command.equals("joined")) {
                    return false;
                }
                return true;
            }, 10000, session.getSessionId(), transactionID);

            Map<String, Object> pluginData = (Map<String, Object>) result.get("plugindata");
            Map<String, Object> data = (Map<String, Object>) pluginData.get("data");

//            log.info("janus room joined.");

        } catch (ResourceAccessException e) {
            throw new GenericException("Janus server is offline");
        }
    }
}
