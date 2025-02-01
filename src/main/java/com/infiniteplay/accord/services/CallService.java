package com.infiniteplay.accord.services;

import com.infiniteplay.accord.annotations.EnsureConsistency;
import com.infiniteplay.accord.entities.*;
import com.infiniteplay.accord.models.ICECandidate;
import com.infiniteplay.accord.models.JanusSession;
import com.infiniteplay.accord.models.MusicSyncEvent;
import com.infiniteplay.accord.models.SystemMessageDetails;
import com.infiniteplay.accord.repositories.*;
import com.infiniteplay.accord.utils.ChatException;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.ICECandidates;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class CallService {
    @Autowired
    SimpMessagingTemplate broker;
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private UserService userService;
    @Autowired
    private CallRepository callRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private SchedulerService schedulerService;
    @Autowired
    private CallAsyncService callAsyncService;
    @Autowired
    private JanusService janusService;
    @PersistenceContext
    EntityManager entityManager;
    @Value("${chatroom.defaultsounds}")
    String defaultSounds;
    @Autowired
    private SoundRepository soundRepository;

    @Value("${filestorage.path}")
    String fileStoragePath;
    @Autowired
    private ChatRoomRoleSettingsRepository chatRoomRoleSettingsRepository;

    @Transactional(readOnly = true)
    public void uploadStreamPreview(String usernameWithId, String chatRoomId, MultipartFile file, String type) throws GenericException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024 || (!type.equals("video") && !type.equals("screen"))) {
            throw new GenericException("Invalid file");
        }
        int userId = userService.extractId(usernameWithId);
        //validate chatroom
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
        try {


            try (InputStream is = file.getInputStream()) {
                Files.copy(is, Paths.get(fileStoragePath, "streamPreview_" + type + "_" + userId + "_" + chatRoom.getId() + ".webp"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new GenericException("Stream preview upload failed");
        }
    }

    public void cleanStreamPreview(int userId, String chatRoomId) throws GenericException {
        try {
            Files.delete(Paths.get(fileStoragePath, "streamPreview_video_" + userId + "_" + chatRoomId + ".webp"));
            Files.delete(Paths.get(fileStoragePath, "streamPreview_screen_" + userId + "_" + chatRoomId + ".webp"));
        } catch (IOException ignored) {

        }
    }


    @Transactional
    public void abortCall(String usernameWithId, String chatRoomId) throws GenericException {
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
        User user = userService.findByUsernameWithId(usernameWithId);

        if (chatRoom.getCallInstance() == null) {
            throw new GenericException("There is no ongoing call in this chatroom");
        }

        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new GenericException("Cannot abort call in a DM");
        }

        ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
        if (roleSettings == null) {
            throw new GenericException("Chat room settings not found");
        }

        chatRoomService.checkRoomPermission(chatRoom, user.getId(), null, roleSettings.getRoleAllowAbortCall());


        Call call = chatRoom.getCallInstance();

        //first, remove all call associations
        chatRoom.setCallInstance(null);
        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);
        call.setChatRoom(null);
        for (User active : call.getActiveParticipants()) {
            active.setActiveCallInstance(null);
            userRepository.save(active);
        }

        for (User pending : call.getPendingParticipants()) {
            pending.getPendingCallInstances().remove(call);
            userRepository.save(pending);
        }

        List<User> activeParticipants = new ArrayList<>(call.getActiveParticipants());
        Set<User> chatRoomParticipants = chatRoom.getParticipants();
        Set<User> pendingParticipantsSnapshot = new HashSet<>(call.getPendingParticipants());

        call.getActiveParticipants().clear();
        call.getPendingParticipants().clear();

        //delete call
        callRepository.delete(call);

        LocalDateTime now = LocalDateTime.now();
        long durationInMillis =

                System.currentTimeMillis() - call.getCreatedAt();

        chatRoomService.announceSystemMessagesBatch(chatRoom.getId().toString(), List.of(new SystemMessageDetails("endCall", user, null, String.valueOf(durationInMillis))), chatRoomParticipants);


        entityManager.flush();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void beforeCommit(boolean readOnly) {
                //destroy janus room
                janusService.destroyRoom(user.getId(), chatRoom.getId());

                //reattach plugin for all users who were participating in the call
                for (User activeParticipant : activeParticipants)
                    janusService.refreshPlugin(activeParticipant.getId(), true, true);
            }

            @Override
            public void afterCommit() {
                //broadcast the call to its participants
                final int chatRoomId = chatRoom.getId();
                schedulerService.runImmediately(chatRoomId, 500, new Object[]{pendingParticipantsSnapshot});

                schedulerService.scheduleTask(() -> {

                    for (User participant : chatRoomParticipants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onCallAbort/" + chatRoomId, "");
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onCallKicked", Map.of("chatRoomId", chatRoom.getId(), "userId", participant.getId(),
                                    "callEnded", true, "callAborted", true));
                        }
                    }
                }, Instant.now().plusMillis(500));


            }
        });


    }

    @Transactional
    public void kickUserFromCall(String usernameWithId, String chatRoomId, String targetUsernameWithId) throws GenericException {
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
        ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
        if (roleSettings == null) {
            throw new GenericException("Chat room settings not found");
        }


        int userId = userService.extractId(usernameWithId);
        if (!chatRoom.getOwnerId().equals(userId)) {
            throw new GenericException("User is not the owner of the chatroom");
        }

        User targetUser = userService.findByUsernameWithId(targetUsernameWithId);

        if (targetUser.getId().equals(userId)) {
            throw new GenericException("You cannot kick yourself out of the call");
        }

        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new GenericException("Cannot kick others from call in DM");
        }
        //check permission
        chatRoomService.checkRoomPermission(chatRoom, userId, List.of(targetUser.getId()), roleSettings.getRoleAllowKickUser());

        boolean callDeleted = leaveCallLogic(targetUser, "kick");


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                //notify the target user that the user has been kicked from the call

                schedulerService.scheduleTask(() -> {

                    broker.convertAndSendToUser(targetUser.getUsername() + "@" + targetUser.getId(), "/general/onCallKicked", Map.of("chatRoomId", chatRoom.getId(), "userId", targetUser.getId(),
                            "callEnded", callDeleted, "callAborted", false));

                }, Instant.now().plusMillis(700));

            }
        });
    }

    @Transactional
    public boolean leaveCallLogic(User user, String from) {

        Call call = user.getActiveCallInstance();


        if (call == null) {
            throw new GenericException("There is no call ongoing for the user");
        }


        if (call.getActiveParticipants().stream().noneMatch(activeParticipant -> activeParticipant.getId().equals(user.getId()))) {
            throw new GenericException("User not participating in the call");
        }

        ChatRoom chatRoom = call.getChatRoom();
        Set<User> participants = chatRoom.getParticipants();


        call.setActiveParticipants(call.getActiveParticipants().stream().filter(activeParticipant -> !activeParticipant.getId().equals(user.getId())).collect(Collectors.toCollection(ArrayList::new)));
        user.setActiveCallInstance(null);

        call.getPendingParticipants().remove(user);
        user.getPendingCallInstances().remove(call);

        final Set<User> pendingParticipantsSnapshot = new HashSet<>(call.getPendingParticipants());

        boolean callDeleted = false;
        if (call.getActiveParticipants().isEmpty()) {
            chatRoom.setCallInstance(null);
            for (User pendingParticipant : call.getPendingParticipants()) {
                pendingParticipant.getPendingCallInstances().remove(call);
            }
            call.setPendingParticipants(new HashSet<>());

            chatRoomRepository.save(chatRoom);
            userRepository.save(user);

            callRepository.delete(call);
            callDeleted = true;

        } else {
            call.incrementVersion();
            callRepository.save(call);
            chatRoomRepository.save(chatRoom);
            userRepository.save(user);
        }

        boolean finalCallDeleted = callDeleted;
        final Set<User> finalParticipants = new HashSet<>(participants);
        LocalDateTime now = LocalDateTime.now();
        long durationInMillis =
                System.currentTimeMillis() - call.getCreatedAt();

        if (callDeleted)
            chatRoomService.announceSystemMessagesBatch(chatRoom.getId().toString(), List.of(new SystemMessageDetails("endCall", user, null, String.valueOf(durationInMillis))), participants);


        entityManager.flush();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void beforeCommit(boolean readOnly) {
                //if the room was deleted, destroy janus room

                if (finalCallDeleted) janusService.destroyRoom(user.getId(), chatRoom.getId());


                //reattach plugin (which should effectively unpublish the media)
                janusService.refreshPlugin(user.getId(), true, true);


            }

            @Override
            public void afterCommit() {
                //broadcast the call to its participants
                final int chatRoomId = chatRoom.getId();
                if (finalCallDeleted) {
                    schedulerService.runImmediately(chatRoomId, 500, new Object[]{pendingParticipantsSnapshot});
                }

                schedulerService.scheduleTask(() -> {

                    for (User participant : finalParticipants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onLeaveCall/" + chatRoomId, user);
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onLeaveCall", Map.of("chatRoomId", chatRoomId, "callEnded", finalCallDeleted,
                                    "userId", user.getId()));
                        }
                    }
                }, Instant.now().plusMillis(500));

                cleanStreamPreview(user.getId(), String.valueOf(chatRoomId));


            }
        });

        return callDeleted;
    }

    @Transactional
    @EnsureConsistency
    public void leaveCall(String usernameWithId) throws GenericException {

        User user = userService.findByUsernameWithId(usernameWithId);

        leaveCallLogic(user, "leave");

    }


    @Transactional
    public void rejectCall(String usernameWithId, String chatRoomId) throws GenericException {
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
        Set<User> participants = chatRoom.getParticipants();
        User user = userService.findByUsernameWithId(usernameWithId);
        Call call = chatRoom.getCallInstance();
        if (call == null) {
            throw new GenericException("There is no call ongoing in this chatroom");
        }
        if (!call.getPendingParticipants().contains(user)) {
            throw new GenericException("User is not receiving this call");
        }

        call.getPendingParticipants().remove(user);
        user.getPendingCallInstances().remove(call);

        final int remainingPendings = call.getPendingParticipants().size();

        final Map<String, Integer> payload = Map.of("remainingPendings", remainingPendings,
                "chatRoomId", chatRoom.getId());

        call.incrementVersion();
        callRepository.save(call);
        userRepository.save(user);


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //broadcast the call rejection to its participants
                for (User participant : participants) {
                    if (!participant.getId().equals(user.getId())) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onRejectCall/" + chatRoom.getId(), user.getId());
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onRejectCall", payload);
                    }
                }
            }
        });

    }

    @Transactional
    public void joinCall(String usernameWithId, String chatRoomId, ICECandidates ices) throws GenericException {
        try {
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
            Set<User> participants = chatRoom.getParticipants();
            User user = userService.findByUsernameWithId(usernameWithId);
            Call call = chatRoom.getCallInstance();
            if (call == null) {
                throw new GenericException("There is no call ongoing in this chatroom");
            }

            if (call.getActiveParticipants().size() > 10) {
                throw new GenericException("Too many participants in the call");
            }

            if (call.getActiveParticipants().stream().anyMatch(activeParticipant -> activeParticipant.getId().equals(user.getId()))) {
                throw new GenericException("User already participating in the call");
            }

            if (user.getActiveCallInstance() != null) {
                throw new GenericException("User already in call");
            }


            call.getPendingParticipants().remove(user);
            user.setActiveCallInstance(call);
            user.getPendingCallInstances().remove(call);
            final int headId = call.getActiveParticipants().stream().map(User::getId).filter((id) -> !Objects.equals(id, user.getId())).
                    max(Integer::compareTo).orElse(-1);
            call.incrementVersion();
            callRepository.save(call);
            userRepository.save(user);
            entityManager.flush();


            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {


                @Override
                public void beforeCommit(boolean readOnly) {
                    //do janus service related works
                    try {
                        //publish ice candidates
                        janusService.publishIceCandidate(user.getId(), chatRoom.getId(), ices.getIceCandidates());
                    } catch (Exception e) {
                        //if publishing ice candidate fails
                        //reattach thde plugin, effectively leaving the room
                        janusService.refreshPlugin(user.getId(), true, true);
                        throw e;
                    }
                }

                @Override
                public void afterCommit() {
                    //broadcast the call to its participants
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("chatRoomId", chatRoom.getId());
                    payload.put("joinTime", System.currentTimeMillis());
                    payload.put("headId", headId);


                    if (user.getEntranceSound().contains("@")) {
                        String file = user.getEntranceSound().split("@")[1];
                        if (!Files.exists(Paths.get(fileStoragePath, file))) {
                            payload.put("joinSoundFile", "default");
                        } else {
                            payload.put("joinSoundFile", user.getEntranceSound().split("@")[1]);
                        }


                    } else
                        payload.put("joinSoundFile", user.getEntranceSound());
                    for (User participant : participants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onJoinCall/" + chatRoom.getId(), user);
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onJoinCall", payload);
                        }
                    }
                }
            });
        } catch (Exception e) {
            janusService.refreshPlugin(userService.extractId(usernameWithId), true, true);
            throw e;
        }

    }


    @Transactional(readOnly = true)
    public String prepareStartCall(String usernameWithId, String chatRoomId, String sdpOffer) throws GenericException {
        try {
            int userId = userService.extractId(usernameWithId);
            User user = userService.findByUsernameWithId(usernameWithId);


            int roomId = Integer.parseInt(chatRoomId);
            //validate user has the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);

            //respect user's "Allow non-friends DM" settings
            if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
                User otherUser = chatRoom.getParticipants().stream().filter(e -> !e.getId().equals(userId)).findFirst().orElse(null);
                if (otherUser == null) {
                    throw new ChatException("Unexpected error");
                }
                if (!otherUser.isAllowNonFriendsDM() && !userRepository.isFriend(userId, otherUser.getId())) {
                    throw new ChatException("You must be friends with this user to call!");
                }
            }

            Set<User> blocked = userService.findAllBlockedAssociationsOf(user);

            Set<User> participantsCopy = new HashSet<>(chatRoom.getParticipants());
            participantsCopy.remove(user);

            if (participantsCopy.size() == 1 &&
                    !blocked.stream().filter((b) -> b.getId().equals(participantsCopy.stream().findFirst().get().getId())).collect(Collectors.toSet()).isEmpty()) {
                return "cannotcall";
            }

            //create the room
            janusService.createRoom(userId, roomId);

            try {
                //join the room
                janusService.joinRoomAsPublisher(userId, roomId);
                //publish media
                String sdpAnswer = janusService.publishMedia(userId, sdpOffer);
                return sdpAnswer;
            } catch (Exception e) {
                janusService.destroyRoom(userId, roomId);
                janusService.refreshPlugin(userId, true, true);
                throw new GenericException("Error while preparing to start call");
            }

        } catch (NumberFormatException e) {
            throw new GenericException("Invalid chatroom id");
        }

    }


    @Transactional(readOnly = true)
    public String prepareJoinCall(String usernameWithId, String chatRoomId, String sdpOffer) throws GenericException {
        try {
            int userId = userService.extractId(usernameWithId);
            int roomId = Integer.parseInt(chatRoomId);
            //validate user has the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
            Call call = chatRoom.getCallInstance();
            if (call == null) {
                throw new GenericException("No call in this room");
            }
            //join the room as publisher
            //this should naturally throw error if the user already joined the room
            janusService.joinRoomAsPublisher(userId, roomId);
            try {
                //publish media
                String sdpAnswer = janusService.publishMedia(userId, sdpOffer);
                return sdpAnswer;
            } catch (Exception e) {
                janusService.refreshPlugin(userId, true, true);
                throw new GenericException("Error while preparing to join call");
            }
        } catch (NumberFormatException e) {
            throw new GenericException("Invalid chatroom id");
        }

    }


    @Transactional(readOnly = true)
    public ICECandidates finalizeSubscription(String usernameWithId, String chatRoomId, String sdpAnswer) throws GenericException {
        int userId = userService.extractId(usernameWithId);
        try {
            //validate the user is in the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
            Call call = chatRoom.getCallInstance();
            if (call == null) {
                throw new GenericException("No call in this room");
            }
            //verify the user has truly joined the call
            Set<Integer> activeParticipantIds = call.getActiveParticipants().stream().map(User::getId).collect(Collectors.toCollection(HashSet::new));
            if (!activeParticipantIds.contains(userId)) {
                throw new GenericException("User not participating in the call");
            }
            activeParticipantIds.remove(userId);
            janusService.finalizeSubscription(userId, sdpAnswer);

            //get all the ice candidates in the room corresponding to the new users
            List<ICECandidate> allIces = new ArrayList<>();
            for (int id : activeParticipantIds) {
                List<ICECandidate> ices = janusService.getIceCandidates(id);
                allIces.addAll(ices);
            }
            return new ICECandidates(allIces);
        } catch (Exception e) {
            //if establishing the subscription failed, roll back previous steps
            janusService.refreshPlugin(userId, false, true);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public String prepareSubscription(String usernameWithId, String chatRoomId) throws GenericException {
        try {

            User user = userService.findByUsernameWithId(usernameWithId);
            int userId = user.getId();
            int roomId = Integer.parseInt(chatRoomId);
            //validate the user is in the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
            Call call = chatRoom.getCallInstance();
            if (call == null) {
                throw new GenericException("No call in this room");
            }
            //verify the user has truly joined the call
            Set<Integer> activeParticipantIds = call.getActiveParticipants().stream().map(User::getId).collect(Collectors.toCollection(HashSet::new));
            if (!activeParticipantIds.contains(userId)) {
                throw new GenericException("User not participating in the call");
            }
            //essentially rejoin the room
            janusService.refreshPlugin(userId, false, true);
            // no subscribing to self
            activeParticipantIds.remove(userId);
            //among active participants, exclude the users with blocked associations
            Set<User> blockedAssociations = userService.findAllBlockedAssociationsOf(user);
            for (User blocked : blockedAssociations) {
                activeParticipantIds.remove(blocked.getId());
            }
            if (activeParticipantIds.size() > 0) {
                String sdpOffer = janusService.joinRoomAsSubscriber(userId, roomId, new ArrayList<>(activeParticipantIds));

//            else {
//                sdpOffer = janusService.subscribeMedia(userId, roomId, newParticipantIds);
//            }

                if (sdpOffer == null) {
                    throw new GenericException("Failed to subscribe to new mediastreams");
                }
                return sdpOffer;
            } else {

                return "placeholder";
            }


        } catch (NumberFormatException e) {
            throw new GenericException("Invalid chatroom id");
        }
    }


    @Transactional
    public Call startCall(String usernameWithId, String chatRoomId, ICECandidates ices) throws GenericException {
        try {
            //get valid reference to the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatRoomId);
            if (chatRoom.getCallInstance() != null) {
                throw new GenericException("This Chatroom already has an ongoing call");
            }
            Set<User> participants = chatRoom.getParticipants();
            User user = userService.findByUsernameWithId(usernameWithId);

            //if user has an existing ongoing call..
            if (user.getActiveCallInstance() != null) {
                throw new GenericException("User already in call");
            }

            //create new call instance (since starting a new call)


            Call call = new Call();
            call.setChatRoom(chatRoom);
            chatRoom.setCallInstance(call);

            call.getActiveParticipants().add(user);
            user.setActiveCallInstance(call);
            call.setCreatedAt(System.currentTimeMillis());

            Set<User> pendingParticipants = new LinkedHashSet<>();
            //fill in all pending participants
            for (User participant : participants) {
                if (!participant.getId().equals(user.getId())) {
                    pendingParticipants.add(participant);
                    participant.getPendingCallInstances().add(call);
                }
            }
            call.setPendingParticipants(pendingParticipants);

            call = callRepository.save(call);
            userRepository.save(user);
            chatRoom.incrementVersion();
            chatRoomRepository.save(chatRoom);

            //auto-reject call after timeout
            final Instant now = Instant.now();
            final long callCreationMillis = call.getCreatedAt();
            final int callId = call.getId();
            final int chatRoomId_ = chatRoom.getId();
            final int callerId = user.getId();
            Set<User> participantsCopy = new HashSet<>(participants);
            participantsCopy.remove(user);


            schedulerService.scheduleFlexibleTask((args) -> {

                callAsyncService.rejectCallAfterTimeout(args);
                return null;
            }, now.plusSeconds(20), chatRoomId_, new Object[]{participantsCopy, callId, chatRoomId_, callerId, callCreationMillis});

            chatRoomService.announceSystemMessagesBatch(chatRoomId, List.of(new SystemMessageDetails("startCall", user, null, null)), participants);


            entityManager.flush();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void beforeCommit(boolean readOnly) {
                    //do janus service related works
                    try {
                        //publish ice candidates
                        janusService.publishIceCandidate(user.getId(), chatRoom.getId(), ices.getIceCandidates());
                    } catch (Exception e) {
                        //if publishing ice candidate fails, delete the room

                        janusService.destroyRoom(user.getId(), chatRoom.getId());

                        //reattach the plugin
                        janusService.refreshPlugin(user.getId(), true, true);
                        throw e;
                    }


                }

                @Override
                public void afterCommit() {

                    //broadcast the call to other chatroom participants

                    schedulerService.scheduleTask(() -> {
                        for (User participant : participants) {
                            if (!participant.getId().equals(user.getId())) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onCall", Map.of("chatRoom", chatRoom, "starterId", user.getId()));
                            }
                        }

                    }, Instant.now().plusMillis(500));


                }


            });

            return call;

        } catch (Exception e) {

            //rollback logic, just in case something goes awry..

            if (e instanceof NumberFormatException) {
                throw new GenericException("Invalid id");
            }

            int userId = userService.extractId(usernameWithId);
            janusService.destroyRoom(userId, Integer.parseInt(chatRoomId));
            //reattach the plugin
            janusService.refreshPlugin(userId, true, true);


            throw e;
        }

    }

    @Transactional(readOnly = true)
    public void expressEmoji(String usernameWithId, String shortCodes) throws GenericException {
        User user = userService.findByUsernameWithId(usernameWithId);
        final int userId = user.getId();
        Call call = user.getActiveCallInstance();
        if (call == null) {
            throw new GenericException("No active call");
        }

        List<User> activeParticipants = call.getActiveParticipants();


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User p : activeParticipants) {
                    if (!user.getId().equals(p.getId())) {
                        broker.convertAndSendToUser(p.getUsername() + "@" + p.getId(), "/general/onCallEmoji", Map.of("userId", userId, "shortCodes", shortCodes));
                    }
                }
            }
        });
    }


    @Transactional(readOnly = true)
    public void shareSound(String usernameWithId, String name) throws GenericException {
        User user = userService.findByUsernameWithId(usernameWithId);
        final int userId = user.getId();
        Call call = user.getActiveCallInstance();
        if (call == null) {
            throw new GenericException("No active call");
        }
        Sound sound = null;


        if (Arrays.stream(defaultSounds.split(",")).toList().contains(name)) {
            sound = soundRepository.findDefaultSounds().stream().filter(e -> e.getName().equals(name)).findFirst().get();
        } else {
            ChatRoom chatRoom = call.getChatRoom();

            Optional<Sound> soundOptional = chatRoom.getSounds().stream().filter(s -> s.getName().equals(name)).findFirst();

            if (soundOptional.isEmpty()) {
                throw new GenericException("Sound does not exist");
            }
            sound = soundOptional.get();
        }


        List<User> activeParticipants = call.getActiveParticipants();

        for (User p : activeParticipants) {
            if (p.getId() != userId) {
                broker.convertAndSendToUser(p.getUsername() + "@" + p.getId(), "/general/onCallSound", Map.of("userId", userId, "sound", sound));
            }
        }
    }


    @Transactional
    public void synchronizeMusic(String usernameWithId, MusicSyncEvent event) throws GenericException {
        String eventType = event.getType();
        User user = userService.findByUsernameWithId(usernameWithId);
        final int userId = user.getId();
        Call call = user.getActiveCallInstance();
        if (call == null) {
            throw new GenericException("No active call");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", eventType);
        payload.put("clientTimestamp", event.getTimestamp());
        payload.put("src", event.getSrc());
        payload.put("time", event.getTime());


        Set<Integer> activeParticipantIds = new HashSet<>();

        for (User p : call.getActiveParticipants()) {

            if (p.getId() != userId) {
                activeParticipantIds.add(p.getId());
                payload.put("serverTimestamp", System.currentTimeMillis());
                broker.convertAndSendToUser(p.getUsername() + "@" + p.getId(), "/general/onCallMusicSync", payload);
            }

        }


        if (eventType.equals("PLAY") || eventType.equals("STOP")) {
            ChatRoom chatRoom = call.getChatRoom();
            List<User> excludedParticipants = new ArrayList<>();
            for (User p : chatRoom.getParticipants()) {
                if (!activeParticipantIds.contains(p.getId())) {
                    excludedParticipants.add(p);
                }
            }

            call.setHasMusic(eventType.equals("PLAY"));

            callRepository.save(call);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (User p : excludedParticipants) {
                        if (p.getId() != userId) {
                            if (eventType.equals("PLAY"))
                                broker.convertAndSendToUser(p.getUsername() + "@" + p.getId(), "/general/onCallMusicState/" + chatRoom.getId(),
                                        Map.of("playing", true));
                            else
                                broker.convertAndSendToUser(p.getUsername() + "@" + p.getId(), "/general/onCallMusicState/" + chatRoom.getId(),
                                        Map.of("playing", false));
                        }
                    }
                }
            });


        }

    }
}
