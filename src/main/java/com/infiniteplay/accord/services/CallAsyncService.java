package com.infiniteplay.accord.services;

import com.infiniteplay.accord.annotations.EnsureConsistency;
import com.infiniteplay.accord.entities.Call;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.SystemMessageDetails;
import com.infiniteplay.accord.repositories.CallRepository;
import com.infiniteplay.accord.repositories.ChatRoomRepository;
import com.infiniteplay.accord.repositories.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CallAsyncService {
    private final ChatRoomRepository chatRoomRepository;
    private final CallRepository callRepository;
    private final UserRepository userRepository;
    @Autowired
    SimpMessagingTemplate broker;
    @Autowired
    private ChatRoomService chatRoomService;

    public CallAsyncService(ChatRoomRepository chatRoomRepository, CallRepository callRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.callRepository = callRepository;
        this.userRepository = userRepository;
    }

    @Async
    @Transactional
    @EnsureConsistency
    public void rejectCallAfterTimeout(Object[] args) {

            Set<User> potentialRejecters = (Set<User>) args[0];

            int callId = (int) args[1];
            int chatRoomId = (int) args[2];
            int callerId = (int) args[3];
            long callCreationMillis = (long) args[4];
            Set<User> pendingParticipantsSnapshot = null;
            if (args.length > 5) {
                pendingParticipantsSnapshot = (Set<User>) args[5];
            }

            System.out.println("rejectCallAfterTimeout() invoked for  :" + chatRoomId);
            if (potentialRejecters.isEmpty()) {
                return;
            }
            ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
            Call call = callRepository.findById(callId).orElse(null);
            User caller = userRepository.findById(callerId).orElse(null);

            //refetch to keep up to date with database version matching
            potentialRejecters = userRepository.findUsersByIds(potentialRejecters.stream().map(User::getId).collect(Collectors.toCollection(ArrayList::new)));
            if (pendingParticipantsSnapshot != null)
                pendingParticipantsSnapshot = userRepository.findUsersByIds(pendingParticipantsSnapshot.stream().map(User::getId).collect(Collectors.toCollection(ArrayList::new)));


            if (chatRoom == null || call == null) {

                if (chatRoom != null) {
                    List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();
                    List<User> participantsList = new ArrayList<>();
                    for (User user : potentialRejecters) {
                        if (pendingParticipantsSnapshot != null && !pendingParticipantsSnapshot.contains(user)) {
                            //user already rejected the call
                            continue;
                        }
                        systemMessageDetails.add(new SystemMessageDetails("private_" + user.getId() + "_missedCall", caller, null, String.valueOf(System.currentTimeMillis() - callCreationMillis)));
                        participantsList.add(user);
                    }
                    chatRoomService.announceSystemMessagesBatchPrivate(chatRoom.getId().toString(), systemMessageDetails, participantsList, true);

                }
                //chatroom was deleted, or call has prematurely ended.
                Set<User> finalPotentialRejecters = potentialRejecters;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (User user : finalPotentialRejecters) {
                            //notify the rejecters of the auto reject call timeout
                            broker.convertAndSendToUser(user.getUsername() + "@" + user.getId(), "/general/onRejectCallTimeout", chatRoomId);
                        }
                    }
                });

                return;

            }

            Hibernate.initialize(call.getActiveParticipants());
            Hibernate.initialize(call.getPendingParticipants());


            Set<Integer> activeParticipantsIds = call.getActiveParticipants().stream().map((user) -> user.getId()).collect(Collectors.toSet());
            Set<Integer> pendingParticipantsIds = call.getPendingParticipants().stream().map((user) -> user.getId()).collect(Collectors.toSet());
            System.out.println("pending Participant Ids: " + pendingParticipantsIds);
            List<User> rejecters = new ArrayList<>();

            for (User potentialRejecter : potentialRejecters) {
                if (activeParticipantsIds.contains(potentialRejecter.getId())) {
                    //user have accepted the call
                    continue;
                }
                if (!pendingParticipantsIds.contains(potentialRejecter.getId())) {
                    //user already rejected the call
                    System.out.println("user already rejected call : " + potentialRejecter);
                    continue;
                }
                rejecters.add(potentialRejecter);
                if (!chatRoomRepository.isChatRoomOf(potentialRejecter.getId(), chatRoomId)) {
                    //user left the chatroom in the meantime?

                    continue;
                }

                call.getPendingParticipants().remove(potentialRejecter);
                Set<Call> pendingCallInstances = new HashSet<>(potentialRejecter.getPendingCallInstances());
                pendingCallInstances.remove(call);
                potentialRejecter.setPendingCallInstances(pendingCallInstances);

                userRepository.save(potentialRejecter);


            }

            call.incrementVersion();
            callRepository.save(call);

            Set<User> currentParticipants = chatRoom.getParticipants();

            List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();
            List<User> participantsList = new ArrayList<>();
            for (User user : rejecters) {
                systemMessageDetails.add(new SystemMessageDetails("private_" + user.getId() + "_missedCall", caller, null, null));
                participantsList.add(user);
            }
            chatRoomService.announceSystemMessagesBatchPrivate(chatRoom.getId().toString(), systemMessageDetails, participantsList, true);

            final int remainingPendings = call.getPendingParticipants().size();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast the call rejection to the chatroom participants
                    for (User participant : currentParticipants) {
                        for (User user : rejecters) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onRejectCall/" + chatRoom.getId(), user.getId());
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onRejectCall", Map.of("remainingPendings", remainingPendings, "chatRoomId", chatRoom.getId()));
                        }

                    }
                    for (User user : rejecters) {
                        //notify the rejecters of the auto reject call timeout
                        broker.convertAndSendToUser(user.getUsername() + "@" + user.getId(), "/general/onRejectCallTimeout", chatRoomId);
                    }
                }
            });



    }
}
