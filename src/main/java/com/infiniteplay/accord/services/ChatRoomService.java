package com.infiniteplay.accord.services;


import com.infiniteplay.accord.annotations.EnsureConsistency;
import com.infiniteplay.accord.entities.*;
import com.infiniteplay.accord.models.*;
import com.infiniteplay.accord.repositories.*;
import com.infiniteplay.accord.utils.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserService userService;
    private final SimpMessagingTemplate broker;
    private final ChatRecordRepository chatRecordRepository;
    private final ChatReactionRepository chatReactionRepository;
    private final ChatNotificationCountRepository chatNotificationCountRepository;
    private final SoundRepository soundRepository;
    private final BackgroundRepository backgroundRepository;
    private final ChatRoomRoleSettingsRepository chatRoomRoleSettingsRepository;
    private final ChatRoomInvitationRepository chatRoomInvitationRepository;
    private final PushNotificationService pushNotificationService;
    @Value("${filestorage.path}")
    String fileStoragePath;
    @Value("${chatroom.defaultsounds}")
    String defaultSounds;

    @Transactional
    public void setPublic(String usernameWithId, int chatRoomId, boolean isPublic) throws GenericException {


        ChatRoom chatRoom = findChatRoomById(usernameWithId, String.valueOf(chatRoomId));

        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new GenericException("DMs cannot be set public");
        }

        User user = userService.findByUsernameWithId(usernameWithId);

        if (!chatRoom.getOwnerId().equals(user.getId())) {
            throw new GenericException("Insufficient permission");
        }

        if (chatRoom.getPublic() != isPublic) {
            chatRoom.setPublic(isPublic);
        }

        chatRoomRepository.save(chatRoom);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : chatRoom.getParticipants())
                    if (!participant.getId().equals(user.getId())) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onSetPublic/" + chatRoom.getId(), isPublic);
                    }
            }
        });


    }

    @Transactional
    public void announceSystemMessagesBatch(String chatroomId, List<SystemMessageDetails> systemMessageDetails, Set<User> participants) throws ChatException {
        announceSystemMessagesBatch(chatroomId, systemMessageDetails, participants, true);
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> searchChatroom(String query) throws ChatRoomException {

        if (query.length() < 2) {
            throw new ChatRoomException("Invalid query");
        }

        int id = 0;
        if (query.contains("#")) {
            String idString = query.split("#")[1];
            try {
                id = Integer.parseInt(idString);
            } catch (NumberFormatException ignored) {

            }
        }

        List<ChatRoom> chatRooms = chatRoomRepository.searchChatRoomByName(query, id);

        for (ChatRoom chatRoom : chatRooms) {
            Hibernate.initialize(chatRoom.getParticipants());
        }
        return chatRooms;

    }

    public String updateRoomImage(ChatRoom chatRoom, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 1048576) {
            throw new UserException("Invalid room image");
        }
        try {
            String[] split = file.getResource().getFilename().split("\\.");
            String extension = split[split.length - 1];
            if (!extension.equals("png") && !extension.equals("jpg")) {
                throw new UserException("Invalid room image");
            }
            String path = "roomImage_" + chatRoom.getId() + "@" + System.currentTimeMillis() + "." + extension;
            chatRoom.setRoomImage(path);

            return path;
        } catch (IndexOutOfBoundsException e) {
            throw new UserException("Image has no file extension");
        }
    }


    public static String generateShortCode() {
        final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public ChatRoom getChatRoomByInvitationCode(String invitationCode) {
        ChatRoomInvitation invitation = chatRoomInvitationRepository.findByShortCode(invitationCode);

        if (invitation == null) {
            return null;
        }

        if (!invitation.isPermanent() && System.currentTimeMillis() > invitation.getExpiration().getTime()) {
            return null;
        }

        ChatRoom chatRoom = findChatRoomByIdOnly(invitation.getChatRoomId());

        Hibernate.initialize(chatRoom.getParticipants());

        return chatRoom;

    }

    @Transactional
    public void joinPublicRoom(String usernameWithId, int chatRoomId) throws GenericException {
        ChatRoom chatRoom = findChatRoomByIdOnly(chatRoomId);

        if (!chatRoom.getPublic()) {
            throw new GenericException("This chatroom is not open to public");
        }
        List<String> currentParticipantNames = new ArrayList<>(chatRoom.getParticipants().stream().map(e -> e.getUsername() + "@" + e.getId()).collect(Collectors.toCollection(ArrayList::new)));

        if (currentParticipantNames.contains(usernameWithId)) {
            throw new ChatRoomException("User already in this chatroom");
        }
        currentParticipantNames.add(usernameWithId);

        updateChatRoom("user@" + chatRoom.getOwnerId(), chatRoom,
                new ChatRoomDetails(chatRoomId, chatRoom.getName(), currentParticipantNames, false), false, null);

    }

    @Transactional
    public void acceptInvitationCode(String usernameWithId, String invitationCode) throws GenericException {

        ChatRoomInvitation invitation = chatRoomInvitationRepository.findByShortCode(invitationCode);

        if (invitation == null) {
            throw new ChatRoomException("Invitation code does not exist.");
        }

        if (!invitation.isPermanent() && invitation.getExpiration() != null &&
                invitation.getExpiration().getTime() < System.currentTimeMillis()) {
            throw new ChatRoomException("Invitation code has expired.");
        }
        ChatRoom chatRoom = findChatRoomByIdOnly(invitation.getChatRoomId());

        List<String> currentParticipantNames = new ArrayList<>(chatRoom.getParticipants().stream().map(e -> e.getUsername() + "@" + e.getId()).collect(Collectors.toCollection(ArrayList::new)));

        if (currentParticipantNames.contains(usernameWithId)) {
            throw new ChatRoomException("User already in this chatroom");
        }
        currentParticipantNames.add(usernameWithId);
        //simulate the owner of chatRoom directly inviting this user
        updateChatRoom("user@" + chatRoom.getOwnerId(), chatRoom,
                new ChatRoomDetails(chatRoom.getId(), chatRoom.getName(),
                        currentParticipantNames, false), false, null);


    }

    @Transactional
    public void invalidateAllInvitationCodes(String usernameWithId, String chatRoomId) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);
        int userId = userService.extractId(usernameWithId);
        boolean isOwner = chatRoom.getOwnerId().equals(userId);

        if (!isOwner) {
            throw new ChatRoomException("Insufficient permission");
        }
        chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(chatRoom.getId(), true);
        chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(chatRoom.getId(), false);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : chatRoom.getParticipants())
                    if (!participant.getId().equals(userId)) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onGenerateInvitationCode/" + chatRoom.getId(), Map.of("code", "", "permanent", true));
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onGenerateInvitationCode/" + chatRoom.getId(), Map.of("code", "", "permanent", false));
                    }
            }
        });
    }

    @Transactional(readOnly = true)
    public String getInvitationCode(String usernameWithId, int chatRoomId, boolean permanent) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, String.valueOf(chatRoomId));

        int userId = userService.extractId(usernameWithId);
        boolean isOwner = chatRoom.getOwnerId().equals(userId);
        boolean isModerator = chatRoom.getModIds() != null && Arrays.asList(chatRoom.getModIds()).contains(userId);
        ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
        if (roleSettings == null) {
            throw new GenericException("Unexpected error while getting invitation code");
        }

        if (roleSettings.getRoleAllowPublicInvite().equals("owner") && !isOwner) {
            throw new GenericException("Insufficient permission");
        }
        if (roleSettings.getRoleAllowPublicInvite().equals("mod") && !isOwner && !isModerator) {
            throw new GenericException("Insufficient permission");
        }

        if (permanent && !isOwner) {
            throw new GenericException("Insufficient permission");
        }


        ChatRoomInvitation invitation = chatRoomInvitationRepository.findByChatRoomIdAndPermanent(chatRoomId, permanent);
        if (invitation == null) {
            return "empty";
        }

        return invitation.isPermanent() ? invitation.getShortCode() : invitation.getShortCode() + "@" + invitation.getExpiration().getTime();
    }

    @Transactional
    public String generateInvitationCode(String usernameWithId, String chatRoomId, boolean permanent) throws GenericException {

        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);
        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new GenericException("Cannot generate invitation code for DM");
        }
        int userId = userService.extractId(usernameWithId);
        boolean isOwner = chatRoom.getOwnerId().equals(userId);
        boolean isModerator = chatRoom.getModIds() != null && Arrays.asList(chatRoom.getModIds()).contains(userId);
        ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
        if (roleSettings == null) {
            throw new GenericException("Unexpected error while generating invitation code");
        }

        if (roleSettings.getRoleAllowPublicInvite().equals("owner") && !isOwner) {
            throw new GenericException("Insufficient permission");
        }
        if (roleSettings.getRoleAllowPublicInvite().equals("mod") && !isOwner && !isModerator) {
            throw new GenericException("Insufficient permission");
        }

        if (permanent && !isOwner) {
            throw new GenericException("Insufficient permission");
        }
        String shortCode;
        //check permission
        ChatRoomInvitation invitation = chatRoomInvitationRepository.findByChatRoomIdAndPermanent(chatRoom.getId(), permanent);
        if (invitation == null) {
            invitation = new ChatRoomInvitation();
            invitation.setChatRoomId(chatRoom.getId());
            invitation.setPermanent(permanent);
            if (!permanent) {
                //1 hour duration
                invitation.setExpiration(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()));
            }
            do {
                shortCode = generateShortCode();
            } while (chatRoomInvitationRepository.existsByShortCode(shortCode));

            invitation.setShortCode(shortCode);

            chatRoomInvitationRepository.save(invitation);
        } else {
            //invitation code already exists; check for cooldown time for non-owners

            boolean expired = !invitation.isPermanent() && invitation.getExpiration() != null && System.currentTimeMillis() > invitation.getExpiration().getTime();
            if (!expired && !isOwner) {
                throw new GenericException("You must wait to generate another invitation code");
            }
            if (!permanent) {
                //1 hour duration
                invitation.setExpiration(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()));
            }
            do {
                shortCode = generateShortCode();
            } while (chatRoomInvitationRepository.existsByShortCode(shortCode));

            invitation.setShortCode(shortCode);
            chatRoomInvitationRepository.save(invitation);
        }


        String finalShortCode = permanent ? shortCode : shortCode + "@" + invitation.getExpiration().getTime();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : chatRoom.getParticipants())
                    if (!participant.getId().equals(userId)) {
                        boolean isUserOwner = chatRoom.getOwnerId().equals(participant.getId());
                        boolean isUserModerator = chatRoom.getModIds() != null && Arrays.asList(chatRoom.getModIds()).contains(participant.getId());
                        if (roleSettings.getRoleAllowPublicInvite().equals("owner") && !isUserOwner) {
                            continue;
                        }
                        if (roleSettings.getRoleAllowPublicInvite().equals("mod") && !isUserOwner && !isUserModerator) {
                            continue;
                        }

                        if (permanent && !isUserOwner) {
                            continue;
                        }
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onGenerateInvitationCode/" + chatRoom.getId(), Map.of("code", finalShortCode, "permanent", permanent));
                    }
            }
        });


        return finalShortCode;

    }

    @Transactional
    public Integer[] grantModeratorRole(String usernameWithId, String chatRoomId, int targetUserId) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);
        int userId = userService.extractId(usernameWithId);

        if (!chatRoom.getOwnerId().equals(userId)) {
            throw new ChatRoomException("Insufficient permission");
        }

        if (userId == targetUserId || chatRoom.getParticipants().stream().noneMatch(e -> e.getId().equals(targetUserId))) {
            throw new ChatRoomException("Invalid target user");
        }

        if (chatRoom.getModIds() == null) {
            chatRoom.setModIds(new Integer[]{targetUserId});
        } else {
            List<Integer> modIds = new ArrayList<>(Arrays.asList(chatRoom.getModIds()));

            if (modIds.contains(targetUserId)) {
                throw new ChatRoomException("Target user is already a moderator");
            }

            modIds.add(targetUserId);

            chatRoom.setModIds(modIds.toArray(new Integer[0]));
        }

        chatRoomRepository.save(chatRoom);

        User targetUser = userRepository.findById(targetUserId).orElse(null);

        if (targetUser == null) {
            throw new ChatRoomException("Invalid target user");
        }

        announceSystemMessagesBatch(chatRoomId, List.of(new SystemMessageDetails("grant_moderator", targetUser, null, null)), chatRoom.getParticipants());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : chatRoom.getParticipants())
                    if (!participant.getId().equals(userId))
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUpdateModeratorRole/" + chatRoom.getId(), chatRoom.getModIds() == null ? "empty" : chatRoom.getModIds());
            }
        });


        return chatRoom.getModIds();

    }


    @Transactional
    public Integer[] revokeModeratorRole(int userId, ChatRoom chatRoom, int targetUserId) {
        return revokeModeratorRole(userId, chatRoom, targetUserId, false);
    }

    @Transactional
    public Integer[] revokeModeratorRole(int userId, ChatRoom chatRoom, int targetUserId, boolean skipValidation) throws GenericException {

        if (!chatRoom.getOwnerId().equals(userId)) {
            throw new ChatRoomException("Insufficient permission");
        }

        if (userId == targetUserId || (!skipValidation && chatRoom.getParticipants().stream().noneMatch(e -> e.getId().equals(targetUserId)))) {
            throw new ChatRoomException("Invalid target user");
        }

        if (chatRoom.getModIds() == null) {
            throw new ChatRoomException("Target user is not a moderator");
        } else {
            List<Integer> modIds = new ArrayList<>(Arrays.asList(chatRoom.getModIds()));

            if (!modIds.contains(targetUserId)) {
                throw new ChatRoomException("Target user is not a moderator");
            }

            modIds = modIds.stream().filter(e -> e != targetUserId).collect(Collectors.toCollection(ArrayList::new));

            if (modIds.isEmpty()) {
                chatRoom.setModIds(null);
            } else {
                chatRoom.setModIds(modIds.toArray(new Integer[0]));
            }


        }

        chatRoomRepository.save(chatRoom);

        User targetUser = userRepository.findById(targetUserId).orElse(null);

        if (targetUser == null) {
            throw new ChatRoomException("Invalid target user");
        }

        announceSystemMessagesBatch(String.valueOf(chatRoom.getId()), List.of(new SystemMessageDetails("revoke_moderator", targetUser, null, null)), chatRoom.getParticipants());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : chatRoom.getParticipants()) {
                    if (!participant.getId().equals(userId))
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUpdateModeratorRole/" + chatRoom.getId(),
                                chatRoom.getModIds() == null ? "empty" : chatRoom.getModIds());
                }
            }
        });

        return chatRoom.getModIds();
    }

    @Transactional
    public Integer[] revokeModeratorRole(String usernameWithId, String chatRoomId, int targetUserId) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);
        return revokeModeratorRole(userService.extractId(usernameWithId), chatRoom, targetUserId);
    }


    @Transactional(readOnly = true)
    public ChatRoomRoleSettings getRoleSettings(String chatRoomId) throws GenericException {
        try {
            ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(Integer.parseInt(chatRoomId));

            if (roleSettings == null) {
                throw new ChatRoomException("Chatroom not found");
            }

            return roleSettings;
        } catch (NumberFormatException e) {
            throw new ChatRoomException("Invalid chatroom id");
        }
    }

    @Transactional
    public void updateRoleSettings(String usernameWithId, ChatRoomRoleSettings settings) throws GenericException {

        int chatRoomId = settings.getChatRoomId();

        ChatRoom chatRoom = findChatRoomById(usernameWithId, String.valueOf(chatRoomId));

        if (!chatRoom.getOwnerId().equals(userService.extractId(usernameWithId))) {
            throw new ChatRoomException("Insufficient permission");
        }

        ChatRoomRoleSettings current = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoomId);

        if (current == null) {
            throw new ChatRoomException("Unexpected error : Role settings not found");
        }

        if (!Objects.equals(settings.getRoleAllowAbortCall(), current.getRoleAllowAbortCall())) {
            current.setRoleAllowAbortCall(settings.getRoleAllowAbortCall());
        }

        if (!Objects.equals(settings.getRoleAllowAddContent(), current.getRoleAllowAddContent())) {
            current.setRoleAllowAddContent(settings.getRoleAllowAddContent());
        }

        if (!Objects.equals(settings.getRoleAllowDeleteContent(), current.getRoleAllowDeleteContent())) {
            current.setRoleAllowDeleteContent(settings.getRoleAllowDeleteContent());
        }

        if (!Objects.equals(settings.getRoleAllowDeleteMessage(), current.getRoleAllowDeleteMessage())) {
            current.setRoleAllowDeleteMessage(settings.getRoleAllowDeleteMessage());
        }

        if (!Objects.equals(settings.getRoleAllowKickUser(), current.getRoleAllowKickUser())) {
            current.setRoleAllowKickUser(settings.getRoleAllowKickUser());
        }

        if (!Objects.equals(settings.getRoleAllowFriendsInvite(), current.getRoleAllowFriendsInvite())) {
            current.setRoleAllowFriendsInvite(settings.getRoleAllowFriendsInvite());
        }

        if (!Objects.equals(settings.getRoleAllowPublicInvite(), current.getRoleAllowPublicInvite())) {
            current.setRoleAllowPublicInvite(settings.getRoleAllowPublicInvite());
        }

        if (!Objects.equals(settings.getRoleAllowPinMessage(), current.getRoleAllowPinMessage())) {
            current.setRoleAllowPinMessage(settings.getRoleAllowPinMessage());
        }


        chatRoomRoleSettingsRepository.save(current);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int userId = userService.extractId(usernameWithId);

                for (User participant : chatRoom.getParticipants()) {
                    if (!participant.getId().equals(userId))
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onRoleSettingsUpdate/" + chatRoom.getId(), current);
                }

            }
        });
    }

    @Transactional
    @EnsureConsistency
    public void announceSystemMessagesBatchPrivate(String chatroomId, List<SystemMessageDetails> systemMessageDetails, List<User> participants, boolean withNotification) throws ChatException {
        //send private system messages in batch; note that systemMessageDetails and participants must correspond in order of what message
        //should be received by which user

        if (systemMessageDetails.isEmpty()) {
            return;
        }
        //find chatroom
        try {

            List<ChatRecord> chatRecords = new ArrayList<>();
            ChatRoom chatRoom = chatRoomRepository.findById(Integer.parseInt(chatroomId)).orElse(null);
            if (chatRoom == null) {
                throw new ChatException("Chat room does not exist");
            }
            Hibernate.initialize(chatRoom.getParticipants());
            for (SystemMessageDetails details : systemMessageDetails) {
                ZonedDateTime now = TimeUtils.getCurrentKST();
                ChatRecord chatRecord = new ChatRecord(null, "system_" + details.getMessageType(), "", now);
                if (details.getAssociatedUser() != null)
                    chatRecord.setSender(details.getAssociatedUser());
                chatRecord.setChatRoom(chatRoom);
                if (details.getSecondaryAssociatedUser() != null)
                    chatRecord.setReplyTargetSender(details.getSecondaryAssociatedUser());
                if (details.getAdditionalMessage() != null)
                    chatRecord.setMessage(details.getAdditionalMessage());
                chatRoom.getChatRecords().add(chatRecord);
                chatRecord = chatRecordRepository.save(chatRecord);
                chatRecords.add(chatRecord);
            }
            chatRoomRepository.save(chatRoom);

            //optionally, update notifications for participants
            if (withNotification) {
                for (int i = 0; i < participants.size(); i++) {
                    User participant = participants.get(i);
                    ChatNotificationCount cnt = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatroomId), participant.getId());
                    if (cnt == null) {
                        throw new GenericException("Unexpected error while updating chat notification data");
                    }
                    if (cnt.getLatestMessageId() == null) {
                        //latest message as in oldest unread message
                        cnt.setLatestMessageId(chatRecords.get(i).getId());
                        cnt.setCount(1);
                    } else
                        cnt.setCount(cnt.getCount() + chatRecords.size());
                    chatNotificationCountRepository.save(cnt);


                }
            }



            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("chatRoom", chatRoom);
                    payload.put("chatRecord", chatRecords.get(chatRecords.size() - 1));

                    //send push notifications to offline users
                    try {
                        pushNotificationService.sendChatNotifications(participants, chatRecords, chatRoom);
                    }
                    catch(Exception e) {
                        log.error("Error while sending push notification: " + e.getMessage());
                    }

                    //broadcast the system message to all the participants, all at once
                    for (int i = 0; i < participants.size(); i++) {
                        User participant = participants.get(i);
                        for (ChatRecord chatRecord : chatRecords)
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/" + chatroomId, Map.of("chatRecord", chatRecord));
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage", payload);
                    }
                }
            });

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat room id");
        }


    }

    @Transactional
    @EnsureConsistency
    public void announceSystemMessagesBatch(String chatroomId, List<SystemMessageDetails> systemMessageDetails, Set<User> participants, boolean withNotification) throws ChatException {
        //send system messages in batch
        if (systemMessageDetails.isEmpty()) {
            return;
        }
        //find chatroom
        try {
            List<ChatRecord> chatRecords = new ArrayList<>();
            ChatRoom chatRoom = chatRoomRepository.findById(Integer.parseInt(chatroomId)).orElse(null);
            if (chatRoom == null) {
                throw new ChatException("Chat room does not exist");
            }
            Hibernate.initialize(chatRoom.getParticipants());
            for (SystemMessageDetails details : systemMessageDetails) {
                ZonedDateTime now = TimeUtils.getCurrentKST();
                ChatRecord chatRecord = new ChatRecord(null, "system_" + details.getMessageType(), "", now);
                if (details.getAssociatedUser() != null)
                    chatRecord.setSender(details.getAssociatedUser());
                chatRecord.setChatRoom(chatRoom);
                if (details.getSecondaryAssociatedUser() != null)
                    chatRecord.setReplyTargetSender(details.getSecondaryAssociatedUser());
                if (details.getAdditionalMessage() != null)
                    chatRecord.setMessage(details.getAdditionalMessage());
                chatRoom.getChatRecords().add(chatRecord);
                chatRecord = chatRecordRepository.save(chatRecord);
                chatRecords.add(chatRecord);
            }
            chatRoomRepository.save(chatRoom);

            //optionally, update notifications for participants
            if (withNotification) {
                for (User participant : participants) {
                    ChatNotificationCount cnt = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatroomId), participant.getId());
                    if (cnt == null) {
                        throw new GenericException("Unexpected error while updating chat notification data");
                    }
                    if (cnt.getLatestMessageId() == null) {
                        //latest message as in oldest unread message
                        cnt.setLatestMessageId(chatRecords.get(0).getId());
                        cnt.setCount(1);
                    } else
                        cnt.setCount(cnt.getCount() + chatRecords.size());

                    chatNotificationCountRepository.save(cnt);


                }
                chatRoom.setRecentMessageDate(TimeUtils.getCurrentKST());
            }


            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("chatRoom", chatRoom);
                    payload.put("chatRecord", chatRecords.get(chatRecords.size() - 1));


                    //send push notifications to offline users
                    try {
                        pushNotificationService.sendChatNotifications(participants, chatRecords, chatRoom);
                    }
                    catch(Exception e) {
                        log.error("Error while sending push notification: " + e.getMessage());
                    }

                    //broadcast the system message to all the participants, all at once
                    for (User participant : participants) {
                        for (ChatRecord chatRecord : chatRecords) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/" + chatroomId, Map.of("chatRecord", chatRecord));
                        }
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage", payload);
                    }
                }
            });

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat room id");
        }

    }

    @Transactional
    public void announcePollExpiration(String chatroomId, int recordId, int senderId) {
        try {
            ChatRoom chatRoom = chatRoomRepository.findById(Integer.parseInt(chatroomId)).orElse(null);
            ChatRecord record = chatRecordRepository.findById(recordId).orElse(null);
            User sender = userRepository.findById(senderId).orElse(null);
            if (chatRoom == null || record == null || sender == null || record.getPoll() == null
            ) {
                throw new GenericException("Announcing poll expiration message failed: null parameters found");
            }

            Set<User> participants = chatRoom.getParticipants();
            if (record.getPollVotes() == null || record.getPollVotes().isEmpty()) {
                announceSystemMessagesBatch(chatroomId, List.of(new SystemMessageDetails(
                        "poll_expired_" + record.getId(),
                        sender,
                        null,
                        record.getPoll().getQuestion() + "@nowinner"
                )), participants);
                return;
            }

            List<String> winners = new ArrayList<>();
            String[] options = record.getPoll().getAnswers().split(";");
            SortedMap<Integer, Integer> voteCount = new TreeMap<>(Comparator.reverseOrder());
            for (Vote vote : record.getPollVotes()) {
                voteCount.putIfAbsent(vote.getAnswerIndex(), 0);
                voteCount.put(vote.getAnswerIndex(), voteCount.get(vote.getAnswerIndex()) + 1);
            }
            int maxValue = voteCount.getOrDefault(voteCount.firstKey(), 0);
            if (maxValue > 0) {
                for (Map.Entry<Integer, Integer> entry : voteCount.entrySet()) {
                    if (entry.getValue() == maxValue) {
                        winners.add(options[entry.getKey()] + " (" + maxValue + " Vote(s))");
                    } else {
                        break;
                    }
                }
            }


            announceSystemMessagesBatch(chatroomId, List.of(new SystemMessageDetails(
                    "poll_expired_" + record.getId(),
                    sender,
                    null,
                    record.getPoll().getQuestion() + "@" + String.join(",", winners)
            )), participants);
        } catch (Exception exc) {
            throw new GenericException("Announcing poll expiration message failed: " + exc.getMessage());
        }

    }

    @Transactional
    public void transferOwnership(String usernameWithId, String chatRoomId, String newOwnerId) throws GenericException {
        try {
            ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);
            User user = userService.findByUsernameWithId(usernameWithId);
            int userId = user.getId();
            List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();
            if (chatRoom.getOwnerId() != userId) {
                throw new ChatRoomException("User is not the owner of the chatroom");
            }
            if (newOwnerId.equals(String.valueOf(userId))) {
                throw new ChatRoomException("User is already the owner of the chatroom");
            }
            User newOwner = userRepository.findById(Integer.parseInt(newOwnerId)).orElse(null);
            if (newOwner == null) {
                throw new UserException("Target user not found");
            }
            chatRoom.setOwnerId(newOwner.getId());
            chatRoomRepository.save(chatRoom);
            systemMessageDetails.add(new SystemMessageDetails("ownershiptransfer", user, newOwner, null));

            Set<User> participants = chatRoom.getParticipants();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (User participant : participants) {
                        if (!participant.getId().equals(user.getId()))
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onTransferOwnership/" + chatRoomId, newOwnerId);
                    }
                }
            });

            announceSystemMessagesBatch(chatRoomId, systemMessageDetails, participants);
        } catch (NumberFormatException e) {
            throw new ChatRoomException("Invalid chatroom id");
        }
    }

    @Transactional
    public long markAllMessagesRead(String usernameWithId, String chatRoomId) throws GenericException {
        try {
            int userId = userService.extractId(usernameWithId);
            ChatNotificationCount chatNotificationCount = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatRoomId), userId);
            if (chatNotificationCount == null) {
                throw new GenericException("Unexpected error while updating chat notification data");
            }
            chatNotificationCount.setLatestMessageId(null);
            chatNotificationCount.setCount(0);
            //update first unread time stamp to current time
            long serverReadTime = System.currentTimeMillis();
            chatNotificationCount.setFirstUnreadTimestamp(serverReadTime);
            chatNotificationCountRepository.save(chatNotificationCount);

            if(chatRoomId.equals("-1")) {
                //spam inbox
                return serverReadTime;
            }
            ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);


            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (User participant : chatRoom.getParticipants()) {

                        if (!participant.getId().equals(userId)) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onReadMessages/" + chatRoomId, Map.of("userId", userId, "firstUnreadTime", serverReadTime));

                        }

                    }

                }
            });

            return serverReadTime;

        } catch (NumberFormatException e) {
            throw new ChatRoomException("Invalid chatroom id");
        }


    }

    @Transactional(readOnly = true)
    public Set<ChatRoom> getAllChatRooms(String usernameWithId) throws UserException {
        User user = userService.findByUsernameWithId(usernameWithId);
        List<ChatNotificationCount> chatNotificationCounts = chatNotificationCountRepository.findAllByUserId(userService.extractId(usernameWithId));
        Map<Integer, ChatNotificationCount> map = new HashMap<>();

        for (ChatNotificationCount chatNotificationCount : chatNotificationCounts) {
            map.put(chatNotificationCount.getChatRoomId(), chatNotificationCount);
        }
        Set<ChatRoom> chatRooms = user.getChatRooms();

        for (ChatRoom chatRoom : chatRooms) {

            if (map.containsKey(chatRoom.getId())) {
                chatRoom.setNotificationCount(map.get(chatRoom.getId()).getCount());
                chatRoom.setLatestMessageId(map.get(chatRoom.getId()).getLatestMessageId());
            }
            Hibernate.initialize(chatRoom.getParticipants());
            Hibernate.initialize(chatRoom.getSounds());
            Hibernate.initialize(chatRoom.getBackgrounds());
            if (chatRoom.getCallInstance() != null) {
                Hibernate.initialize(chatRoom.getCallInstance().getActiveParticipants());
                Hibernate.initialize(chatRoom.getCallInstance().getPendingParticipants());
            }
        }

        return chatRooms;
    }

    @Transactional(readOnly = true)
    public ChatRoom getChatRoom(String usernameWithId, String id_) throws ChatRoomException {


        ChatRoom chatRoom = findChatRoomById(usernameWithId, id_);

        List<ChatNotificationCount> notificationCounts = chatNotificationCountRepository.findByChatRoomIdAndUserIds(chatRoom.getId(), chatRoom.getParticipants().stream().map(User::getId).collect(Collectors.toCollection(ArrayList::new)));
        Map<Integer, User> participantsMap = new HashMap<>();

        for (User participant : chatRoom.getParticipants()) {
            participantsMap.put(participant.getId(), participant);
        }

        for (ChatNotificationCount notificationCount : notificationCounts) {
            //fetch first unread message date
            if (participantsMap.containsKey(notificationCount.getUserId())) {
                participantsMap.get(notificationCount.getUserId()).setFirstUnreadMessageTimestamp(notificationCount.getFirstUnreadTimestamp());
            }
        }


        ChatNotificationCount chatNotificationCount = chatNotificationCountRepository.findByChatRoomIdAndUserId(chatRoom.getId(), userService.extractId(usernameWithId));
        if (chatNotificationCount == null) {
            throw new GenericException("Unexpected error while fetching chat notification data");
        }
        if (chatNotificationCount.getLatestMessageId() != null) {
            chatRoom.setNotificationCount(chatNotificationCount.getCount());
            chatRoom.setLatestMessageId(chatNotificationCount.getLatestMessageId());
        }
        return chatRoom;
    }

    @Transactional(readOnly = true)
    public List<Integer> getNotificationData(String usernameWithId, String chatRoomId) throws ChatRoomException {
        try {


            ChatNotificationCount chatNotificationCount = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatRoomId), userService.extractId(usernameWithId));
            if (chatNotificationCount == null) {
                throw new GenericException("Unexpected error while fetching chat notification data");
            }
            if (chatNotificationCount.getLatestMessageId() != null) {
                return List.of(chatNotificationCount.getCount(), chatNotificationCount.getLatestMessageId());
            }
            return List.of(0, 0);
        } catch (NumberFormatException e) {
            throw new ChatRoomException("Invalid chatroom id");

        }
    }

    @Transactional
    public ChatRoom findChatRoomByIdOnly(int chatRoomId) throws GenericException {
        Optional<ChatRoom> chatRoom = chatRoomRepository.findById(chatRoomId);
        if (chatRoom.isEmpty()) {
            throw new ChatRoomException("Chatroom not found");
        }
        ChatRoom chatRoomInstance = chatRoom.get();

        Hibernate.initialize(chatRoomInstance.getParticipants());
        Hibernate.initialize(chatRoomInstance.getSounds());
        Hibernate.initialize(chatRoomInstance.getBackgrounds());

        if (chatRoomInstance.getCallInstance() != null) {
            Hibernate.initialize(chatRoomInstance.getCallInstance().getActiveParticipants());
            Hibernate.initialize(chatRoomInstance.getCallInstance().getPendingParticipants());
        }

        return chatRoomInstance;

    }

    @Transactional(readOnly = true)
    public ChatRoom findChatRoomById(String usernameWithId, String id_) throws ChatRoomException, UserException {
        Integer id = null;
        try {
            id = Integer.parseInt(id_);
        } catch (NumberFormatException e) {
            throw new ChatRoomException("Invalid chatroom id");
        }
        Optional<ChatRoom> chatRoom = chatRoomRepository.findById(id);

        String[] split = usernameWithId.split("@");

        int userId = 0;
        try {
            userId = Integer.parseInt(split[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ChatRoomException("Invalid user id");
        }

        if (chatRoom.isEmpty() || !chatRoomRepository.isChatRoomOf(userId, id)) {
            throw new ChatRoomException("Chatroom not found");
        }

        ChatRoom chatRoomInstance = chatRoom.get();

        Hibernate.initialize(chatRoomInstance.getParticipants());
        Hibernate.initialize(chatRoomInstance.getSounds());
        Hibernate.initialize(chatRoomInstance.getBackgrounds());

        if (chatRoomInstance.getCallInstance() != null) {
            Hibernate.initialize(chatRoomInstance.getCallInstance().getActiveParticipants());
            Hibernate.initialize(chatRoomInstance.getCallInstance().getPendingParticipants());
        }

        return chatRoomInstance;
    }


    @Transactional
    public int deleteDirectMessagingChatRoom(String usernameWithId, String chatRoomId) throws GenericException {
        System.out.println("delete() start: " + chatRoomId);
        int userId = userService.extractId(usernameWithId);
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new ChatRoomException("Cannot delete a 1 to 1 DM");
        }

        if (!chatRoom.getOwnerId().equals(userId)) {
            throw new ChatRoomException("User is not the owner of this chatroom");
        }

        if (chatRoom.getCallInstance() != null) {
            throw new ChatRoomException("You cannot delete the chatroom because there is a call ongoing! Please kick other users" +
                    " from the call first.");
        }

        Set<User> participants = new HashSet<>(chatRoom.getParticipants());

        //all participants leave the chatroom
        chatRoomRepository.deleteChatRoomAssociation(chatRoom.getId());

        //delete chat reactions
        chatReactionRepository.deleteChatReactionsByChatRoomId(chatRoom.getId());
        //delete the chat records as well
        chatRecordRepository.deleteChatRecords(chatRoom.getId());

        //delete associated sounds
        soundRepository.deleteByChatroomId(chatRoom.getId());

        //delete associated backgrounds
        backgroundRepository.deleteByChatroomId(chatRoom.getId());

        final String roomImagePath = chatRoom.getRoomImage();

        chatRoomRepository.delete(chatRoom);

        //delete associated notification data
        chatNotificationCountRepository.deleteByChatRoomId(Integer.parseInt(chatRoomId));

        //delete associated role settings
        chatRoomRoleSettingsRepository.deleteByChatRoomId(Integer.parseInt(chatRoomId));
        //delete invitations
        chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(Integer.parseInt(chatRoomId), true);
        chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(Integer.parseInt(chatRoomId), false);


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : participants) {
                    if ((participant.getUsername() + "@" + participant.getId()).equals(usernameWithId)) {
                        continue;
                    }
                    broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onDeleteChatRoom", chatRoom.getId());
                }
                //delete room images, if any
                try {
                    Files.delete(Paths.get(fileStoragePath, roomImagePath));
                } catch (IOException ignored) {
                }
            }
        });


        return chatRoom.getId();

    }


    @Transactional
    public void leaveDirectMessagingChatRoom(String usernameWithId, String chatRoomId) throws ChatRoomException, UserException {

        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        User user = userService.findByUsernameWithId(usernameWithId);
        Set<ChatRoom> chatRooms = user.getChatRooms();
        if (chatRooms == null) {
            throw new ChatRoomException("Chatroom not found");
        }
        if (!chatRooms.contains(chatRoom)) {
            throw new ChatRoomException("Chatroom not found for user");
        }

        if (chatRoom.getCallInstance() != null) {
            if (chatRoom.getCallInstance().getActiveParticipants().contains(user)) {
                throw new ChatRoomException("You cannot leave the chatroom while in call. Please leave the call first!");
            } else if (chatRoom.getCallInstance().getPendingParticipants().contains(user)) {
                throw new ChatRoomException("You cannot leave the chatroom while there is a call incoming. Please reject the call first!");
            }

        }
        //leave chatroom
        chatRooms.remove(chatRoom);
        Set<User> participants = chatRoom.getParticipants();
        participants.remove(user);


        user.incrementVersion();
        userRepository.save(user);

        List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();

        AtomicReference<String> roomImagePathToDelete = new AtomicReference<>();

        if (participants.isEmpty()) {
            //chatroom is empty...


            //all participants leave the chatroom
            chatRoomRepository.deleteChatRoomAssociation(chatRoom.getId());

            //delete chat reactions
            chatReactionRepository.deleteChatReactionsByChatRoomId(chatRoom.getId());
            //delete the chat records as well
            chatRecordRepository.deleteChatRecords(chatRoom.getId());

            //delete associated sounds
            soundRepository.deleteByChatroomId(chatRoom.getId());

            //delete associated backgrounds
            backgroundRepository.deleteByChatroomId(chatRoom.getId());

            if (chatRoom.getRoomImage() != null)
                roomImagePathToDelete.set(chatRoom.getRoomImage());

            chatRoomRepository.delete(chatRoom);

            //delete associated notification data
            chatNotificationCountRepository.deleteByChatRoomId(Integer.parseInt(chatRoomId));

            //delete associated role settings
            chatRoomRoleSettingsRepository.deleteByChatRoomId(Integer.parseInt(chatRoomId));

            //delete invitations
            chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(Integer.parseInt(chatRoomId), true);
            chatRoomInvitationRepository.deleteByChatRoomIdAndPermanent(Integer.parseInt(chatRoomId), false);
        } else {
            //the owner left the chatroom, transfer the owner role to the next person that joined the chatroom
            if (user.getId().equals(chatRoom.getOwnerId()) && (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty())) {
                //owner here
                User nextParticipant = participants.stream().findFirst().orElse(null);
                chatRoom.setOwnerId(nextParticipant.getId());
                systemMessageDetails.add(new SystemMessageDetails("ownerleave", user, nextParticipant, null));
            }
            //if a moderator left the room, delete it from modIds column
            if (chatRoom.getModIds() != null && Arrays.stream(chatRoom.getModIds()).anyMatch(e -> Objects.equals(e, user.getId()))) {
                revokeModeratorRole(chatRoom.getOwnerId(), chatRoom, user.getId());
            }
            chatRoom.incrementVersion();
            chatRoomRepository.save(chatRoom);
        }

        chatNotificationCountRepository.deleteByUserIdAndChatRoomId(user.getId(), chatRoom.getId());

        Map<String, String> leaveData = new HashMap<>();

        leaveData.put("leftUser", usernameWithId);
        leaveData.put("chatRoomId", chatRoomId);
        leaveData.put("newOwner", systemMessageDetails.isEmpty() ? "-1" : chatRoom.getOwnerId().toString());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : participants) {
                    broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onLeaveChatRoom", leaveData);
                }
                if (roomImagePathToDelete.get() != null) {
                    try {
                        Files.delete(Paths.get(fileStoragePath, roomImagePathToDelete.get()));
                    } catch (IOException ignored) {
                    }
                }
            }
        });

        if (!participants.isEmpty()) {

            if (systemMessageDetails.isEmpty())
                systemMessageDetails.add(new SystemMessageDetails("leave", user, null, null));
            announceSystemMessagesBatch(chatRoomId, systemMessageDetails, participants);
        }
    }

    @Transactional
    public void checkRoomPermission(ChatRoom chatRoom, int userId, @Nullable List<Integer> targetUserIds, String roleSetting) throws GenericException {

        boolean isOwner = chatRoom.getOwnerId().equals(userId);

        boolean isModerator = chatRoom.getModIds() != null && Arrays.asList(chatRoom.getModIds()).contains(userId);


        if (roleSetting.equals("owner") && !isOwner) {
            throw new ChatException("Insufficient permission");
        }
        if (roleSetting.equals("mod") && !isOwner && !isModerator) {
            throw new ChatException("Insufficient permission");
        }

        if (targetUserIds != null) {

            for (int targetUserId : targetUserIds) {
                boolean isTargetOwner = chatRoom.getOwnerId().equals(targetUserId);

                boolean isTargetModerator = chatRoom.getModIds() != null && Arrays.asList(chatRoom.getModIds()).contains(targetUserId);
                if (isTargetOwner && !isOwner) {
                    throw new ChatException("Insufficient permission");
                }
                if (isTargetModerator && !isModerator && !isOwner) {
                    throw new ChatException("Insufficient permission");
                }
            }

        }


    }


    @Transactional
    public ChatRoom updateChatRoom(String usernameWithId, ChatRoomDetails chatRoomDetails,
                                   @Nullable MultipartFile roomImageFile) throws GenericException {

        int id = chatRoomDetails.getId();
        ChatRoom currentChatRoom = findChatRoomById(usernameWithId, String.valueOf(id));
        return updateChatRoom(usernameWithId, currentChatRoom, chatRoomDetails, true, roomImageFile);
    }

    @Transactional
    public ChatRoom updateChatRoom(String usernameWithId, ChatRoom currentChatRoom, ChatRoomDetails chatRoomDetails, boolean validateFriends,
                                   @Nullable MultipartFile roomImageFile) throws ChatRoomException, UserException {

        if (chatRoomDetails.getName().length() > 30 || chatRoomDetails.getName().length() < 2) {
            throw new ChatRoomException("Invalid chatroom update");
        }

        if (chatRoomDetails.getParticipants().size() > 10) {
            throw new ChatRoomException("This chatroom is already full!");
        }


        int userId = userService.extractId(usernameWithId);


        if (currentChatRoom.getDirect1to1Identifier() != null && !currentChatRoom.getDirect1to1Identifier().isEmpty()) {
            throw new ChatRoomException("Cannot update 1 to 1 DM");
        }


        ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(currentChatRoom.getId());

        if (roleSettings == null) {
            throw new ChatRoomException("Unexpected error while updating Chatroom");
        }

        //update room image
        AtomicReference<String> roomImagePathToDelete = new AtomicReference<>();
        AtomicReference<String> roomImagePathToSave = new AtomicReference<>();
        if (!chatRoomDetails.isDeleteRoomImage() && roomImageFile != null) {
            if (currentChatRoom.getRoomImage() != null) {
                roomImagePathToDelete.set(currentChatRoom.getRoomImage());
            }
            String roomImage = updateRoomImage(currentChatRoom, roomImageFile);
            roomImagePathToSave.set(roomImage);
        } else if (chatRoomDetails.isDeleteRoomImage() && currentChatRoom.getRoomImage() != null) {
            roomImagePathToDelete.set(currentChatRoom.getRoomImage());
            currentChatRoom.setRoomImage(null);
        }
        User user = userService.findByUsernameWithId(usernameWithId);
        List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();

        Set<User> oldParticipants = currentChatRoom.getParticipants();
        Set<User> newParticipants = new HashSet<>();
        newParticipants.add(user);

        //find the difference between old and new
        List<String> invites = new ArrayList<>();
        List<String> kicked = new ArrayList<>();
        List<String> existing = new ArrayList<>();

        //find who has been added
//        System.out.println("UPDATING CHATROOM: " + chatRoomDetails.getParticipants());
        for (String newParticipant : chatRoomDetails.getParticipants()) {
            User newParticipantUser = userService.findByUsernameWithId(newParticipant.replace("#", "@"));

            if (!oldParticipants.contains(newParticipantUser)) {
                //this user has been newly invited to this chatroom
                if (validateFriends && !userRepository.isFriend(user.getId(), newParticipantUser.getId())) {
                    throw new ChatRoomException("Cannot add non-friends to chatroom");
                }
                systemMessageDetails.add(new SystemMessageDetails("join", newParticipantUser, null, null));
                invites.add(newParticipantUser.getUsername() + "@" + newParticipantUser.getId());


                //initialize chat notification data for the newly invited user
                ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
                chatNotificationCount.setChatRoomId(currentChatRoom.getId());
                chatNotificationCount.setUserId(newParticipantUser.getId());
                chatNotificationCount.setCount(0);
                chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
                chatNotificationCountRepository.save(chatNotificationCount);

                if (newParticipantUser.getChatRooms().size() >= 100) {
                    if (validateFriends) {
                        //was a direct invitation
                        throw new ChatRoomException("One of the invitees already belongs to more than 100 chatrooms!");
                    } else {
                        //was an indirect invitation
                        throw new ChatRoomException("Unfortunately, you have hit the limit of 100 chatrooms!");
                    }
                }
                newParticipantUser.incrementVersion();
                newParticipantUser.getChatRooms().add(currentChatRoom);

                userRepository.save(newParticipantUser);
            } else {
                //this user already exists
                existing.add(newParticipantUser.getUsername() + "@" + newParticipantUser.getId());
            }
            newParticipants.add(newParticipantUser);
        }

        if (!chatRoomDetails.getName().equals(currentChatRoom.getName())) {
            systemMessageDetails.add(new SystemMessageDetails("rename", null, null, chatRoomDetails.getName()));
        }

        currentChatRoom.setName(chatRoomDetails.getName());


        currentChatRoom.setParticipants(newParticipants);

        //find who has been kicked
        for (User oldParticipantUser : oldParticipants) {
            if (!newParticipants.contains(oldParticipantUser)) {
                //this user has been kicked from the chatroom
                if (oldParticipantUser.getId() == userId) {
                    throw new ChatRoomException("Cannot kick yourself out of chatroom");
                }

                if (currentChatRoom.getCallInstance() != null && (currentChatRoom.getCallInstance().getActiveParticipants().contains(oldParticipantUser))) {
                    throw new ChatRoomException("You cannot kick a user while the user is in call - please kick the user out of the call first!");
                }

                if (currentChatRoom.getCallInstance() != null && (currentChatRoom.getCallInstance().getPendingParticipants().contains(oldParticipantUser))) {
                    throw new ChatRoomException("You cannot kick a user while the user is receiving the call - please abort the call now or wait for the outgoing call to expire!");
                }
                kicked.add(oldParticipantUser.getUsername() + "@" + oldParticipantUser.getId());
                boolean isUserModerator = currentChatRoom.getModIds() != null && Arrays.stream(currentChatRoom.getModIds()).anyMatch(e -> e.equals(oldParticipantUser.getId()));
                if (isUserModerator) {
                    //the user was moderator, revoke moderator role
                    revokeModeratorRole(currentChatRoom.getOwnerId(), currentChatRoom, oldParticipantUser.getId(), true);
                }
                systemMessageDetails.add(new SystemMessageDetails("kick", oldParticipantUser, user, currentChatRoom.getName()));
                oldParticipantUser.getChatRooms().remove(currentChatRoom);
                userRepository.save(oldParticipantUser);
                //delete associated chat notification data for the kicked user
                chatNotificationCountRepository.deleteByUserIdAndChatRoomId(oldParticipantUser.getId(), currentChatRoom.getId());
            }
        }


        currentChatRoom.incrementVersion();
        chatRoomRepository.save(currentChatRoom);


        if (!invites.isEmpty()) {
            checkRoomPermission(currentChatRoom, userId, null, roleSettings.getRoleAllowFriendsInvite());

        }
        if (!kicked.isEmpty()) {
            checkRoomPermission(currentChatRoom, userId, kicked.stream().map(e -> Integer.parseInt(e.split("@")[1])).collect(Collectors.toCollection(ArrayList::new)), roleSettings.getRoleAllowKickUser());
        }


        //handle sockets
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String invited : invites) {
                    broker.convertAndSendToUser(invited, "/general/onInviteChatRoom", currentChatRoom);
                }

                for (String kick : kicked) {
                    broker.convertAndSendToUser(kick, "/general/onKickChatRoom", currentChatRoom.getId());
                }

                for (String exist : existing) {
                    broker.convertAndSendToUser(exist, "/general/onEditChatRoom", currentChatRoom);

                    for (String kick : kicked) {
                        System.out.println("kicked being sent: " + kick);
                        Map<String, String> leaveData = new HashMap<>();
                        leaveData.put("leftUser", kick);
                        leaveData.put("chatRoomId", currentChatRoom.getId().toString());
                        leaveData.put("newOwner", "-1");
                        broker.convertAndSendToUser(exist, "/general/onLeaveChatRoom", leaveData);
                    }
                }

                if (roomImagePathToSave.get() != null) {
                    saveFile(roomImageFile, roomImagePathToSave.get());
                }
                if (roomImagePathToDelete.get() != null) {
                    try {
                        Files.delete(Paths.get(fileStoragePath, roomImagePathToDelete.get()));
                    } catch (IOException ignored) {
                    }
                }


            }
        });

        //announce appropriate system messages
        announceSystemMessagesBatch(String.valueOf(currentChatRoom.getId()), systemMessageDetails, newParticipants);


        return currentChatRoom;
    }

    @Transactional
    public ChatRoom createDirectMessagingChatroom(String usernameWithId, List<String> inviteesUsernameWithId, String chatroomName, boolean isDM) throws UserException, ChatRoomException {
        if (inviteesUsernameWithId.size() > 9 || chatroomName.length() > 30 || chatroomName.length() < 2 ||
                (isDM && inviteesUsernameWithId.size() != 1)) {
            throw new ChatRoomException("Invalid chatroom creation");
        }


        User user = userService.findByUsernameWithId(usernameWithId);
        Set<User> blocked = userService.findAllBlockedAssociationsOf(user);
        if (user.getChatRooms().size() >= 100) {
            throw new ChatRoomException("Unfortunately, you have hit the limit of 100 chatrooms!");
        }
        if (inviteesUsernameWithId.size() == 1 && !blocked.stream().filter((b) -> b.getId() == userService.extractId(inviteesUsernameWithId.get(0))).collect(Collectors.toSet()).isEmpty()) {
            throw new ChatRoomException("Failed to chat this user.");
        }
        if (isDM) {
            //is 1 to 1 chat - does it already exist??
            String[] split = inviteesUsernameWithId.get(0).split("@");
            int inviteeId = Integer.parseInt(split[1]);
            String direct1to1Identifier = Math.min(user.getId(), inviteeId) + "@" + Math.max(user.getId(), inviteeId);
            ChatRoom chatRoom = chatRoomRepository.findByDirect1to1Identifier(direct1to1Identifier);
            if (user.getChatRooms().contains(chatRoom)) {
                throw new ChatRoomException("Direct chat already exists against this user:" + chatRoom.getId());
            }
            if (chatRoom != null) {
                //DM already exists for this pair of users, auto-invite this user

                user.getChatRooms().add(chatRoom);
                chatRoom.getParticipants().add(user);
                user.incrementVersion();
                userRepository.save(user);
                chatRoomRepository.save(chatRoom);

                //initialize chat notification data for the newly invited user
                ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
                chatNotificationCount.setChatRoomId(chatRoom.getId());
                chatNotificationCount.setUserId(user.getId());
                chatNotificationCount.setCount(0);
                chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
                chatNotificationCountRepository.save(chatNotificationCount);

                List<SystemMessageDetails> systemMessageDetails = new ArrayList<>();

                systemMessageDetails.add(new SystemMessageDetails("join", user, null, null));

                Set<User> participants = new HashSet<>(chatRoom.getParticipants());
                participants.remove(user);
                announceSystemMessagesBatch(String.valueOf(chatRoom.getId()), systemMessageDetails, participants, true);

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (User participant : chatRoom.getParticipants()) {
                            if (!Objects.equals(participant.getId(), user.getId())) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onEditChatRoom", chatRoom);
                            }

                        }
                    }
                });

                return chatRoom;
            }
        }
        int userId = user.getId();
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(chatroomName);
        if (isDM) {
            String[] split = inviteesUsernameWithId.get(0).split("@");
            int friendId = Integer.parseInt(split[1]);
            String direct1to1Identifier = Math.min(user.getId(), friendId) + "@" + Math.max(user.getId(), friendId);
            chatRoom.setDirect1to1Identifier(direct1to1Identifier);
        }
        Set<User> participants = new HashSet<>();
        chatRoom.setParticipants(participants);

        chatRoom = chatRoomRepository.save(chatRoom);


        for (String invitee : inviteesUsernameWithId) {
            if (invitee.equals(usernameWithId)) {
                throw new ChatRoomException("Cannot invite yourself to the chatroom");
            }
            //add chatroom to each invitees as well
            User currentInvitee = userService.findByUsernameWithId(invitee);
            //respect user's "allow non friends from DM" settings
            if (isDM && !currentInvitee.isAllowNonFriendsDM()
                    && !userRepository.isFriend(userId, currentInvitee.getId())) {
                throw new ChatRoomException("You must be friends with this user to send direct messages!");
            } else if (!isDM && !userRepository.isFriend(userId, currentInvitee.getId())) {
                throw new ChatRoomException("You must be friends to directly invite a user upon chatroom creation!");
            }

            currentInvitee.getChatRooms().add(chatRoom);
            participants.add(currentInvitee);
            userRepository.save(currentInvitee);

            //initialize chat notification data
            ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
            chatNotificationCount.setChatRoomId(chatRoom.getId());
            chatNotificationCount.setUserId(userService.extractId(invitee));
            chatNotificationCount.setCount(0);
            chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
            chatNotificationCountRepository.save(chatNotificationCount);
        }

        //add chatroom to current user
        Set<ChatRoom> chatRooms = user.getChatRooms();
        chatRooms.add(chatRoom);
        participants.add(user);
        chatRoom.setOwnerId(userId);

        //initialize chat notification data for current user
        ChatNotificationCount chatNotificationCount = new ChatNotificationCount();
        chatNotificationCount.setChatRoomId(chatRoom.getId());
        chatNotificationCount.setUserId(user.getId());
        chatNotificationCount.setCount(0);
        chatNotificationCount.setFirstUnreadTimestamp(System.currentTimeMillis());
        chatNotificationCountRepository.save(chatNotificationCount);

        userRepository.save(user);
        chatRoomRepository.save(chatRoom);

        //create role settings
        ChatRoomRoleSettings chatRoomRoleSettings = new ChatRoomRoleSettings();
        chatRoomRoleSettings.setChatRoomId(chatRoom.getId());
        chatRoomRoleSettingsRepository.save(chatRoomRoleSettings);

        ChatRoom finalChatRoom = chatRoom;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : participants) {
                    if (!Objects.equals(participant.getId(), user.getId())) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onCreateChatRoom", finalChatRoom);
                    }

                }
            }
        });


        return chatRoom;
    }

    public void saveFile(MultipartFile file, String path) {
        try {
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, Paths.get(fileStoragePath, path), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new GenericException("Attachment upload failed");
        }
    }

    @Transactional
    public void deleteSound(String usernameWithId, String chatRoomId, String soundName, String type) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        if (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
            ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
            if (roleSettings == null) {
                throw new GenericException("Unexpected error while deleting sound");
            }
            checkRoomPermission(chatRoom, userService.extractId(usernameWithId), null, roleSettings.getRoleAllowDeleteContent());
        }

        Sound sound = null;
        int soundIndex = -1;
        int i = 0;

        for (Sound s : chatRoom.getSounds()) {
            if (s.getName().equals(soundName) && s.getType().equals(type)) {
                sound = s;
                soundIndex = i;
            }
            i++;
        }
        if (sound == null) {
            throw new ChatRoomException("Sound does not exist");
        }
        final String soundFileName = sound.getFile();
        chatRoom.getSounds().remove(soundIndex);

        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);
        soundRepository.delete(sound);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //actually save the file
                try {
                    Files.delete(Paths.get(fileStoragePath, soundFileName));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                //broadcast live to others
                for (User participant : chatRoom.getParticipants()) {
                    if (!Objects.equals(participant.getId(), userService.extractId(usernameWithId))) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onDeleteSound/" + chatRoomId, Map.of("name", soundName, "type", type));
                    }
                }
            }
        });


    }

    @Transactional
    public void reorderMusic(String usernameWithId, String chatRoomId, Map<String, Integer> order) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        List<Sound> sounds = chatRoom.getSounds();

        long musicCount = sounds.stream().filter(sound -> sound.getType().equals("music")).count();
        if (order.size() != musicCount) {
            throw new GenericException("Invalid order");
        }

        List<Sound> modifiedSounds = new ArrayList<>();
        for (Sound sound : sounds) {
            if (sound.getType().equals("music")) {
                Integer orderI = order.get(sound.getName());
                if (orderI == null) {
                    throw new GenericException("Invalid order");
                }

                sound.setOrderId(orderI);
                modifiedSounds.add(sound);
            }
        }

        if (!modifiedSounds.isEmpty())
            soundRepository.saveAll(modifiedSounds);
        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //broadcast live to others
                for (User participant : chatRoom.getParticipants()) {
                    if (!Objects.equals(participant.getId(), userService.extractId(usernameWithId))) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onMusicReorder/" + chatRoomId, order);

                    }
                }
            }
        });

    }


    @Transactional
    public void addSound(String usernameWithId, String chatRoomId, SoundData soundData) throws GenericException {

        if (!soundData.getType().equals("sound") && !soundData.getType().equals("music")) {
            throw new GenericException("Invalid request");
        }
        if (soundData.getName().trim().isEmpty() || soundData.getName().length() > 12 || soundData.getName().contains("@")
                || soundData.getName().contains("_")) {
            throw new GenericException("Name:Invalid name");
        }
        if (soundData.getIcon().trim().isEmpty() || soundData.getIcon().chars().filter(e -> e == ':').count() != 2
                || soundData.getIcon().length() > 100) {
            throw new GenericException("Icon:Invalid icon");
        }
        float fileSizeinMB = soundData.getSoundFile().getSize() / 1000000.0f;
        String fileName = soundData.getSoundFile().getResource().getFilename();

        if (fileName == null || (soundData.getType().equals("sound") && fileSizeinMB > 1) ||
                (soundData.getType().equals("music") && fileSizeinMB > 8) ||
                (soundData.getType().equals("sound") && soundData.getDuration() > 5000) ||
                (soundData.getType().equals("music") && soundData.getDuration() > 600000) ||
                (soundData.getType().equals("music") && soundData.getDuration() <= 5000) ||
                soundData.getDuration() < 500
                || !fileName.contains(".")
                || !List.of("mp3", "ogg", "wav").contains(fileName.substring(fileName.lastIndexOf(".") + 1))) {


            throw new GenericException("File:Invalid file");
        }
        if (!Pattern.compile(RegexConstants.FILENAME_REGEX, Pattern.CASE_INSENSITIVE).matcher(fileName).matches()) {
            throw new GenericException("File:Invalid filename");
        }

        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        if (chatRoom.getSounds().size() > 50) {
            throw new GenericException("Cannot add more than 50 sounds or musics per room!");
        }

        if (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
            ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
            if (roleSettings == null) {
                throw new GenericException("Unexpected error while adding sound");
            }
            checkRoomPermission(chatRoom, userService.extractId(usernameWithId), null, roleSettings.getRoleAllowAddContent());
        }


        if (
                (soundData.getType().equals("sound") && Arrays.stream(defaultSounds.split(",")).collect(Collectors.toCollection(ArrayList::new)).contains(soundData.getName())) ||
                        chatRoom.getSounds().stream().anyMatch(e -> e.getName().equals(soundData.getName()) && e.getType().equals(soundData.getType()))
        ) {
            if (soundData.getType().equals("sound"))
                throw new GenericException("Name:Sound with this name already exists");
            else
                throw new GenericException("Name:Music with this name already exists");
        }


        Sound sound = new Sound();
        sound.setType(soundData.getType());
        sound.setChatRoom(chatRoom);
        sound.setName(soundData.getName());
        sound.setIcon(soundData.getIcon());
        sound.setDuration(soundData.getDuration());

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        sound.setFile(soundData.getType() + "_" + chatRoom.getId() + "_" + sound.getName() + "." + extension);

        chatRoom.getSounds().add(sound);

        soundRepository.save(sound);
        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //actually save the file
                saveFile(soundData.getSoundFile(), sound.getFile());
                //broadcast live to others
                for (User participant : chatRoom.getParticipants()) {
                    if (!Objects.equals(participant.getId(), userService.extractId(usernameWithId))) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onAddSound/" + chatRoomId, sound);
                    }
                }
            }
        });


    }

    @Transactional
    public void addBackground(String usernameWithId, String chatRoomId, String name, MultipartFile file) throws GenericException {


        if (name.trim().isEmpty() || name.length() > 25 || name.contains("@")) {
            throw new GenericException("Name:Invalid name");
        }

        float fileSizeinMB = file.getSize() / 1000000.0f;
        String fileName = file.getResource().getFilename();

        if (fileName == null || fileSizeinMB > 2

                || !fileName.contains(".")
                || !List.of("jpg", "jpeg", "png").contains(fileName.substring(fileName.lastIndexOf(".") + 1))) {


            throw new GenericException("File:Invalid file");
        }
        if (!Pattern.compile(RegexConstants.FILENAME_REGEX, Pattern.CASE_INSENSITIVE).matcher(fileName).matches()) {
            throw new GenericException("File:Invalid filename");
        }

        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);

        if (chatRoom.getBackgrounds().size() > 50) {
            throw new GenericException("Cannot add more than 50 backgrounds per room!");
        }


        if (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
            ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
            if (roleSettings == null) {
                throw new GenericException("Unexpected error while adding background");
            }
            checkRoomPermission(chatRoom, userService.extractId(usernameWithId), null, roleSettings.getRoleAllowAddContent());
        }

        List<Background> defaultBackgrounds = backgroundRepository.findDefaultBackgrounds();

        if (
                chatRoom.getBackgrounds().stream().anyMatch(e -> e.getName().equals(name)) ||
                        defaultBackgrounds.stream().anyMatch(e -> e.getName().equals(name))
        ) {

            throw new GenericException("Name:Background with this name already exists");
        }


        Background background = new Background();
        background.setChatRoom(chatRoom);
        background.setName(name);

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        background.setFile("bg_" + chatRoom.getId() + "_" + name + "." + extension);

        chatRoom.getBackgrounds().add(background);

        backgroundRepository.save(background);
        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //actually save the file
                saveFile(file, background.getFile());
                //broadcast live to others
                for (User participant : chatRoom.getParticipants()) {
                    if (!Objects.equals(participant.getId(), userService.extractId(usernameWithId))) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onAddBackground/" + chatRoomId, background);
                    }
                }
            }
        });


    }

    @Transactional
    public void deleteBackground(String usernameWithId, String chatRoomId, String name) throws GenericException {
        ChatRoom chatRoom = findChatRoomById(usernameWithId, chatRoomId);


        if (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
            ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
            if (roleSettings == null) {
                throw new GenericException("Unexpected error while deleting background");
            }
            checkRoomPermission(chatRoom, userService.extractId(usernameWithId), null, roleSettings.getRoleAllowDeleteContent());
        }

        Background background = null;
        int backgroundIndex = -1;
        int i = 0;

        for (Background b : chatRoom.getBackgrounds()) {
            if (b.getName().equals(name)) {
                background = b;
                backgroundIndex = i;
            }
            i++;
        }
        if (background == null) {
            throw new ChatRoomException("Background does not exist");
        }
        final String filename = background.getFile();
        chatRoom.getBackgrounds().remove(backgroundIndex);

        chatRoom.incrementVersion();
        chatRoomRepository.save(chatRoom);
        backgroundRepository.delete(background);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //actually save the file
                try {
                    Files.delete(Paths.get(fileStoragePath, filename));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                //broadcast live to others
                for (User participant : chatRoom.getParticipants()) {
                    if (!Objects.equals(participant.getId(), userService.extractId(usernameWithId))) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onDeleteBackground/" + chatRoomId, Map.of("name", name));
                    }
                }
            }
        });


    }
}
