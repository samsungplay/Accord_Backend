package com.infiniteplay.accord.services;

import ai.djl.inference.Predictor;
import ai.djl.translate.TranslateException;
import com.infiniteplay.accord.entities.*;
import com.infiniteplay.accord.models.*;
import com.infiniteplay.accord.repositories.*;
import com.infiniteplay.accord.utils.ChatException;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.RegexConstants;
import com.infiniteplay.accord.utils.TimeUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate broker;

    @Autowired
    private AIService aiService;

    @Autowired
    private ChatRecordRepository chatRecordRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatReactionRepository chatReactionRepository;
    @Autowired
    private ChatNotificationCountRepository chatNotificationCountRepository;
    @Value("${filestorage.path}")
    String fileStoragePath;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PollRepository pollRepository;
    @Autowired
    private VoteRepository voteRepository;
    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private ChatRoomRoleSettingsRepository chatRoomRoleSettingsRepository;
    @Autowired
    private PushNotificationService pushNotificationService;


    @Transactional
    public void hideMessageEmbed(String usernameWithId, String chatroomId, String chatRecordId) throws GenericException {

        try {

            //find valid owned chat record
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            int userId = userService.extractId(usernameWithId);
            ChatRecord chatRecord = chatRecordRepository.findByIdAndSenderId(Integer.parseInt(chatRecordId), userId);
            if (chatRecord == null) {
                throw new ChatException("Chat record does not exist");
            }
            //hide embed
            chatRecord.setHideEmbed(true);
            chatRecordRepository.save(chatRecord);
            Set<User> participants = chatRoom.getParticipants();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast embed hide
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageHideEmbed/" + chatRoom.getId(), chatRecordId);
                            if (chatRecord.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageHideEmbed/-1", chatRecordId);
                            }
                        }
                    }
                }
            });
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id");
        }
    }

    @Transactional
    public String deleteAttachment(String usernameWithId, String chatroomId, String chatRecordId, UUID attachmentId) throws GenericException {
        int userId = userService.extractId(usernameWithId);
        try {
            //validate the chat record was sent by the user
            ChatRecord chatRecord = chatRecordRepository.findByIdAndSenderId(Integer.parseInt(chatRecordId), userId);
            if (chatRecord == null) {
                throw new ChatException("Chat record does not exist");
            }

            String attachmentsCode = chatRecord.getAttachments();
            String attachmentsMetadata = chatRecord.getAttachmentsMetadata();

            if (attachmentsCode == null || attachmentsCode.isEmpty() || attachmentsMetadata == null || attachmentsMetadata.isEmpty()) {
                throw new ChatException("Chat does not have attachments");
            }

            String[] files = attachmentsCode.split(",");
            String[] metadata = attachmentsMetadata.split(" ");

            if (files.length != metadata.length) {
                throw new ChatException("Invalid attachments metadata");
            }

            StringBuilder attachmentsCodeBuilder = new StringBuilder();
            StringBuilder attachmentsMetadataBuilder = new StringBuilder();

            String deleteTarget = null;
            int i = 0;
            for (String file : files) {
                String[] data = file.split(";");
                UUID uuid = UUID.fromString(data[0]);

                String fileName = data[2];
                String fullFileName = uuid + "_" + fileName;
                if (uuid.equals(attachmentId)) {
                    deleteTarget = fullFileName;
                    i++;
                    continue;
                }
                attachmentsCodeBuilder.append(file).append(",");
                attachmentsMetadataBuilder.append(metadata[i]).append(" ");
                i++;
            }

            if (deleteTarget == null) {
                throw new ChatException("Attachment not found");
            }

            attachmentsCode = attachmentsCodeBuilder.toString();
            attachmentsMetadata = attachmentsMetadataBuilder.toString().trim();

            chatRecord.setAttachments(!attachmentsCode.isEmpty() ? attachmentsCode : null);
            chatRecord.setAttachmentsMetadata(!attachmentsMetadata.isEmpty() ? attachmentsMetadata : null);

            boolean deleted = (attachmentsCode.isEmpty() && attachmentsMetadata.isEmpty()) && chatRecord.getMessage().isEmpty();


            if (attachmentsCode.isEmpty() && chatRecord.getMessage().isEmpty()) {
                deleted = true;
                //if the message is completely empty, delete it
                chatRecordRepository.updateReplyTargetMessage("This message has been deleted.", chatRecord.getId());
                //delete chat reactions
                chatReactionRepository.deleteChatReactionsByChatRecordId(chatRecord.getId());
                //delete chat record message
                chatRecordRepository.delete(chatRecord);
            } else {
                chatRecordRepository.save(chatRecord);
            }


            //send event to others
            ChatRoom chatRoom = chatRoomRepository.findById(Integer.parseInt(chatroomId)).orElse(null);

            if (chatRoom == null) {
                throw new ChatException("Chatroom does not exist");
            }

            Map<String, String> information = new HashMap<>();
            information.put("attachmentsCode", attachmentsCode);
            information.put("recordId", chatRecordId);
            information.put("attachmentsMetadata", attachmentsMetadata);

            Set<User> participants = chatRoom.getParticipants();

            boolean finalDeleted = deleted;
            String finalDeleteTarget = deleteTarget;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //actually delete the file
                    try {
                        Files.delete(Paths.get(fileStoragePath, finalDeleteTarget));
                    } catch (IOException e) {
                        throw new ChatException("Failed to delete attachments");
                    }
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            if (!finalDeleted)
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDeleteAttachment/" + chatRoom.getId(), information);
                            else
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDelete/" + chatRoom.getId(), chatRecord.getId());

                            if (chatRecord.getSpam()) {
                                if (!finalDeleted)
                                    broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDeleteAttachment/-1", information);
                                else
                                    broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDelete/-1", chatRecord.getId());
                            }
                        }
                    }
                }
            });


            return attachmentsCode;


        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chatroom id");
        }

    }


    @Transactional
    public void deleteMessage(String usernameWithId, String chatroomId, String chatRecordId) throws GenericException {
        int userId = userService.extractId(usernameWithId);
        try {
            ChatRecord chatRecord = chatRecordRepository.findById(Integer.parseInt(chatRecordId)).orElse(null);

            if (chatRecord == null) {
                throw new ChatException("Chat record does not exist");
            }
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);

            //check permission
            User sender = chatRecord.getSender();


            //only need to check when the sender is not the same as deleter
            if (sender.getId() != userId) {
                if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {
                    throw new GenericException("Cannot delete other's messages in DM");
                }
                ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
                if (roleSettings == null) {
                    throw new GenericException("Chatroom role settings does not exist");
                }
                chatRoomService.checkRoomPermission(chatRoom, userId, List.of(sender.getId()), roleSettings.getRoleAllowDeleteMessage());
            }


            String attachmentsCode = chatRecord.getAttachments();

            chatRecordRepository.updateReplyTargetMessage("This message has been deleted.", chatRecord.getId());
            //delete chat reactions
            chatReactionRepository.deleteChatReactionsByChatRecordId(chatRecord.getId());
            //delete chat record message
            chatRecordRepository.delete(chatRecord);


            Set<User> participants = chatRoom.getParticipants();

            final ChatRoom finalChatRoom = chatRoom;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //delete attachments if there were any

                    if (attachmentsCode != null && !attachmentsCode.isEmpty()) {
                        List<String> paths = new ArrayList<>();
                        String[] files = attachmentsCode.split(",");

                        for (String file : files) {
                            String[] data = file.split(";");
                            UUID uuid = UUID.fromString(data[0]);
                            String fileName = data[2];
                            String fullFileName = uuid + "_" + fileName;
                            paths.add(fullFileName);
                        }


                        deleteAttachments(paths);
                    }

                    //broadcast message deletion to its participants
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDelete/" + finalChatRoom.getId(), chatRecordId);
                            if (chatRecord.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDelete/-1", chatRecordId);
                            }
                        }
                    }
                }
            });
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }
    }


    @Transactional
    public void editMessage(String usernameWithId, String chatroomId, String chatRecordId, ChatMessage message) throws GenericException {

        int userId = userService.extractId(usernameWithId);

        //find the chat record
        try {

            //validate & find the chat record that was sent by the user
            ChatRecord chatRecord = chatRecordRepository.findByIdAndSenderId(Integer.parseInt(chatRecordId), userId);
            if (chatRecord == null) {
                throw new ChatException("Chat record does not exist");
            }
            //update chat record message
            chatRecord.setMessage(message.getPayload());

            //reapply content filters

            boolean isTextNSFW = aiService.detectNSFW(chatRecord.getMessage());


            chatRecord.setNsfw(isTextNSFW);

            boolean wasSpam = chatRecord.getSpam();

            boolean isSpam = aiService.detectSpam(chatRecord.getMessage());
            chatRecord.setSpam(isSpam);

            chatRecord.setEdited(true);
            chatRecordRepository.save(chatRecord);
            chatRecordRepository.updateReplyTargetMessage(message.getPayload(), chatRecord.getId());


            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);

            Set<User> participants = chatRoom.getParticipants();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast message update to its participants
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/" + chatRoom.getId(), chatRecord);
                            if (chatRecord.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/-1", chatRecord);
                            }
                            if (wasSpam && !isSpam) {

                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageDelete/-1", chatRecord.getId());

                            } else if (!wasSpam && isSpam) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/-1", Map.of("chatRecord", chatRecord, "chatRoom", chatRoom));
                            }
                        }
                    }
                }
            });

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }


    }

    public boolean isValidFilename(String filename) {
        return Pattern.compile(RegexConstants.FILENAME_REGEX, Pattern.CASE_INSENSITIVE)
                .matcher(filename)
                .matches();
    }

    public String[] validateAttachments(MultipartFile[] attachments, String attachmentsMetadata) {
        StringBuilder attachmentCodeBuilder = new StringBuilder();
        StringBuilder actualPathBuilder = new StringBuilder();
        //validate attachments
        if (attachmentsMetadata == null || attachmentsMetadata.isEmpty()) {
            throw new ChatException("Attachments without metadata");
        }
        if (attachments.length > 10) {
            throw new ChatException("A message cannot have more than 10 attachments");
        }
        for (MultipartFile attachment : attachments) {
            if (attachment.isEmpty()) {
                throw new ChatException("Invalid attachment");
            }
            float fileSizeinMB = attachment.getSize() / 1000000.0f;
            if (fileSizeinMB > 8) {
                throw new ChatException("Cannot send attachments over 8MB! : " + fileSizeinMB);
            }
            String completeFileName = attachment.getResource().getFilename();

            if (completeFileName.length() > 35) {
                throw new ChatException("Name of the attachment is too long!");
            }

            if (!isValidFilename(completeFileName)) {
                throw new ChatException("Invalid attachment name!");
            }
            String[] split = completeFileName.split("\\.");
            String extension = "";
            if (split.length > 1) {
                extension = split[split.length - 1];
            }
            UUID uuid = UUID.randomUUID();
            String path = uuid + ";" + BigDecimal.valueOf(fileSizeinMB).setScale(2, RoundingMode.HALF_UP) + ";" + completeFileName;
            String actualPath = uuid + "_" + completeFileName;
            attachmentCodeBuilder.append(path).append(",");
            actualPathBuilder.append(actualPath).append(",");
        }
        return new String[]{attachmentCodeBuilder.toString(), actualPathBuilder.toString()};
    }

    public void deleteAttachments(List<String> paths) {
        for (String path : paths) {
            try {
                Files.delete(Paths.get(fileStoragePath, path));
            } catch (IOException e) {
                throw new ChatException("Attachment upload failed");
            }
        }
    }

    public void saveAttachments(MultipartFile[] attachments, String actualPaths) {
        String[] paths = actualPaths.split(",");
        ;

        for (int i = 0; i < attachments.length; i++) {
            MultipartFile attachment = attachments[i];
            String path = paths[i];
            try {
                try (InputStream is = attachment.getInputStream()) {
                    Files.copy(is, Paths.get(fileStoragePath, path), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new ChatException("Attachment upload failed");
            }
        }
    }

    @Transactional
    public void votePoll(String usernameWithId, String chatroomId, String pollId, List<Integer> answerIndices) throws GenericException {
        try {
            //validate user belongs to the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            User user = userService.findByUsernameWithId(usernameWithId);
            //find poll
            Poll poll = pollRepository.findById(Integer.parseInt(pollId)).orElse(null);
            if (poll == null) {
                throw new ChatException("Poll not found");
            }
            //validate poll belongs to the chatroom
            if (!poll.getRecord().getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Poll does not belong to the chatroom");
            }
            if (TimeUtils.getCurrentKST().isAfter(poll.getExpiration())) {
                throw new ChatException("Poll expired");
            }
            if (answerIndices.isEmpty() || (!poll.getAllowMultiple() && answerIndices.size() > 1)
                    || answerIndices.size() > 8) {
                throw new ChatException("Invalid poll answers");
            }

            //max 100 votes
            if (poll.getRecord().getPollVotes().size() + answerIndices.size() > 100) {
                throw new ChatException("At most 100 votes allowed!");
            }

            //create vote
            ChatRecord record = poll.getRecord();

            for (Vote vote_ : record.getPollVotes()) {
                if (vote_.getVoter().getId().equals(user.getId())) {
                    for (int answerIndex : answerIndices) {
                        if (vote_.getAnswerIndex().equals(answerIndex)) {
                            throw new ChatException("User already voted");
                        }
                    }

                }
            }

            for (int answerIndex : answerIndices) {
                Vote vote = new Vote();
                vote.setVoter(user);
                vote.setRecord(record);
                vote.setAnswerIndex(answerIndex);
                record.getPollVotes().add(vote);
                voteRepository.save(vote);
            }

            pollRepository.save(poll);

            record.incrementVersion();
            chatRecordRepository.save(record);

            Set<User> participants = chatRoom.getParticipants();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast message to its participants
                    for (User participant : participants) {
                        if (participant.getId() != user.getId()) {

                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/" + chatRoom.getId(), poll.getRecord());
                            if (record.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/-1", poll.getRecord());
                            }

                        }
                    }
                }
            });

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id format");
        }
    }

    @Transactional
    public void computeNotificationData(ChatRecord chatRecord) {



    }



    @Transactional
    public void unVotePoll(String usernameWithId, String chatroomId, String pollId) throws GenericException {
        try {
            //validate user belongs to the chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            User user = userService.findByUsernameWithId(usernameWithId);
            //find poll
            Poll poll = pollRepository.findById(Integer.parseInt(pollId)).orElse(null);
            if (poll == null) {
                throw new ChatException("Poll not found");
            }
            //validate poll belongs to the chatroom
            if (!poll.getRecord().getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Poll does not belong to the chatroom");
            }
            if (TimeUtils.getCurrentKST().isAfter(poll.getExpiration())) {
                throw new ChatException("Poll expired");
            }

            List<Vote> filtered = poll.getRecord().getPollVotes().stream()
                    .filter(vote_ -> !vote_.getVoter().getId().equals(user.getId())).toList();

            List<Vote> deleteds = poll.getRecord().getPollVotes().stream()
                    .filter(vote_ -> vote_.getVoter().getId().equals(user.getId())).toList();

            if (deleteds.isEmpty()) {
                throw new ChatException("User never voted");
            }
            poll.getRecord().setPollVotes(filtered);

            pollRepository.save(poll);
            poll.getRecord().incrementVersion();
            chatRecordRepository.save(poll.getRecord());
            voteRepository.bulkDeleteByIds(deleteds.stream().map(Vote::getId).toList());

            Set<User> participants = chatRoom.getParticipants();

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", poll.getRecord().getId());
            payload.put("votes", poll.getRecord().getPollVotes());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast message to its participants
                    for (User participant : participants) {
                        if (participant.getId() != user.getId()) {

                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/" + chatRoom.getId(), poll.getRecord());
                            if (poll.getRecord().getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageEdit/-1", poll.getRecord());
                            }


                        }
                    }
                }
            });

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id format");
        }
    }


    public void dispatchTypingEvent(String usernamewithId, int chatRoomId, List<String> participantUsernameWithIds) throws GenericException {

        System.out.println("dispatch typing event invoked:" +  participantUsernameWithIds + " and " + usernamewithId);
        for(String user : participantUsernameWithIds) {
            if(!user.equals(usernamewithId))
                broker.convertAndSendToUser(user, "/general/onUserType/" + chatRoomId, userService.extractId(usernamewithId));
        }
    }

    @Transactional
    public ChatRecord sendPoll(String usernameWithId, String chatroomId, PollMessage pollMessage) throws GenericException {
        //basic validation
        if (pollMessage.getQuestion().isEmpty() || pollMessage.getQuestion().length() > 300) {
            throw new ChatException("Invalid poll question length");
        }
        if (pollMessage.getAnswers().isEmpty() || pollMessage.getAnswers().size() > 8) {
            throw new ChatException("Invalid poll answers length");
        }
        if (pollMessage.getDuration() > 1000 * 60 * 60 * 24 * 14 || pollMessage.getDuration() < 1000 * 60 * 60) {
            throw new ChatException("Invalid poll duration");
        }
        StringBuilder answersBuilder = new StringBuilder();
        int i = 0;
        for (String answer : pollMessage.getAnswers()) {
            if (answer.contains(";") || answer.length() > 55 || answer.isEmpty()) {
                throw new ChatException("Invalid poll answer");
            }
            if (!answer.contains("::")) {
                throw new ChatException("Invalid poll answer");
            } else {
                String content = answer.substring(answer.indexOf("::") + 2);
                if (content.contains("::")) {
                    throw new ChatException("Invalid poll answer");
                }
            }

            if (i == pollMessage.getAnswers().size() - 1) {
                answersBuilder.append(answer);
            } else {
                answersBuilder.append(answer).append(";");
            }
            i++;
        }
        //validate chatroom belongs to the user
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
        User user = userService.findByUsernameWithId(usernameWithId);
        int userId = user.getId();

        //respect user's "Allow non-friends DM" settings
        if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {

            Optional<Integer> otherUserId = Arrays.stream(chatRoom.getDirect1to1Identifier().split("@")).map(Integer::parseInt).filter((e) -> e != userId).findFirst();


            if (otherUserId.isEmpty()) {
                throw new ChatException("Unexpected error");
            }
            User otherUser = userRepository.findById(otherUserId.get()).orElse(null);
            if (otherUser == null) {
                throw new ChatException("Unexpected error");
            }
            if (!otherUser.isAllowNonFriendsDM() && !userRepository.isFriend(userId, otherUser.getId())) {
                throw new ChatException("You must be friends with this user to send direct messages!");
            }
        }

        ZonedDateTime now = TimeUtils.getCurrentKST();

        //instantiate new chat record
        ChatRecord chatRecord = new ChatRecord();
        chatRecord.setType("poll");
        chatRecord.setSender(user);
        chatRecord.setDate(now);
        chatRecord.setChatRoom(chatRoom);
        chatRecord.setMessage("");
        //save poll data
        Poll poll = new Poll();
        poll.setQuestion(pollMessage.getQuestion());
        poll.setAnswers(answersBuilder.toString());
        ZonedDateTime later = now.plus(pollMessage.getDuration(), ChronoUnit.MILLIS);
        poll.setExpiration(later);
        poll.setAllowMultiple(pollMessage.isAllowMultiple());
        pollRepository.save(poll);
        chatRecord.setPoll(poll);
        chatRoom.getChatRecords().add(chatRecord);
        ChatRecord recorded = chatRecordRepository.save(chatRecord);
        chatRoomRepository.save(chatRoom);

        Set<User> participants = chatRoom.getParticipants();

        //update notifications for participants
        for (User participant : participants) {
            if (participant.getId() != userId) {
                //if the participant has blocked this user, do not update the participant's notification count
                if (userRepository.isBlocked(participant.getId(), userId)) {
                    continue;
                }
                ChatNotificationCount cnt = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatroomId), participant.getId());
                if(cnt == null) {
                    throw new GenericException("Unexpected error while updating chat notification data");
                }
                if (cnt.getLatestMessageId() == null) {
                    //latest message as in oldest unread message
                    cnt.setLatestMessageId(recorded.getId());
                    cnt.setFirstUnreadTimestamp(recorded.getDate().toInstant().toEpochMilli());
                    cnt.setCount(1);
                }
                else
                    cnt.setCount(cnt.getCount() + 1);
                chatNotificationCountRepository.save(cnt);
            }
        }


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //schedule poll expiration system message

                schedulerService.scheduleTask(() -> {
                    chatRoomService.announcePollExpiration(chatroomId, recorded.getId(), user.getId());
                }, later.toInstant());

                Map<String, Object> payload = new HashMap<>();
                payload.put("chatRoom", chatRoom);
                payload.put("chatRecord", chatRecord);

                //send push notifications to offline users
                try {
                    pushNotificationService.sendChatNotifications(participants, List.of(chatRecord), chatRoom);
                }
                catch(Exception e) {
                    log.error("error while sending push notification: " + e.getMessage());
                }

                //broadcast message to its participants
                for (User participant : participants) {
                    if (participant.getId() != userId) {

                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/" + chatRoom.getId(), Map.of("chatRecord", chatRecord));
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage", payload);

                        if (chatRecord.getSpam()) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/-1", Map.of("chatRecord", chatRecord, "chatRoom", chatRoom));
                        }

                    }
                }
            }
        });

        return recorded;
    }


    @Transactional
    public ChatRecord sendMessage(String usernameWithId, String chatroomId, ChatMessage message, MultipartFile[] attachments,
                                  String attachmentsMetadata) {
        return sendMessage(usernameWithId, chatroomId, message, attachments, attachmentsMetadata, null);
    }


    @Transactional
    public ChatRecord sendMessage(String usernameWithId, String chatroomId, ChatMessage message, MultipartFile[] attachments,
                                  String attachmentsMetadata, ZonedDateTime customDate) throws GenericException {


        String attachmentCode = null, actualPaths = null;
        if (attachments != null) {
            String[] paths = validateAttachments(attachments, attachmentsMetadata);
            attachmentCode = paths[0];
            actualPaths = paths[1];

        }
        //find chatroom this user belongs to
        User user = userService.findByUsernameWithId(usernameWithId);
        int userId = user.getId();

        try {
            if (message.getReplyTargetSenderId() != null && !message.getReplyTargetSenderId().isEmpty() && Integer.parseInt(message.getReplyTargetSenderId()) == userId) {
                throw new ChatException("Cannot reply to yourself");
            }

            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            //respect user's "Allow non-friends DM" settings
            if (chatRoom.getDirect1to1Identifier() != null && !chatRoom.getDirect1to1Identifier().isEmpty()) {

                Optional<Integer> otherUserId = Arrays.stream(chatRoom.getDirect1to1Identifier().split("@")).map(Integer::parseInt).filter((e) -> e != userId).findFirst();


                if (otherUserId.isEmpty()) {
                    throw new ChatException("Unexpected error");
                }
                User otherUser = userRepository.findById(otherUserId.get()).orElse(null);
                if (otherUser == null) {
                    throw new ChatException("Unexpected error");
                }
                if (!otherUser.isAllowNonFriendsDM() && !userRepository.isFriend(userId, otherUser.getId())) {
                    throw new ChatException("You must be friends with this user to send direct messages!");
                }
            }
            //instantiate new chatrecord
            ZonedDateTime now = TimeUtils.getCurrentKST();
            if (message.getPayload().isEmpty() && attachmentCode == null) {
                throw new ChatException("Cannot send empty message");
            }
            if (message.getPayload().length() > 255) {
                throw new ChatException("Message is too long");
            }
            ChatRecord chatRecord = new ChatRecord(null, "text", message.getPayload(), customDate != null ? customDate : now);
            chatRecord.setSender(user);
            chatRecord.setChatRoom(chatRoom);
            chatRecord.setChatRoomIdReference(chatRoom.getId());
            if (attachmentCode != null) {
                chatRecord.setAttachments(attachmentCode);
                chatRecord.setAttachmentsMetadata(attachmentsMetadata);
            }
            chatRoom.getChatRecords().add(chatRecord);
            chatRoom.setRecentMessageDate(now);
            if (message.getReplyTarget() != null && message.getReplyTargetSenderId() != null && message.getReplyTargetMessage() != null
                    && !message.getReplyTarget().isEmpty() && !message.getReplyTargetSenderId().isEmpty() && !message.getReplyTargetMessage().isEmpty()) {
                chatRecord.setReplyTargetMessage(message.getReplyTargetMessage());
                chatRecord.setReplyTargetId(Integer.parseInt(message.getReplyTarget()));
            }


            if (message.getReplyTarget() != null && message.getReplyTargetSenderId() != null && message.getReplyTargetMessage() != null
                    && !message.getReplyTarget().isEmpty() && !message.getReplyTargetSenderId().isEmpty() && !message.getReplyTargetMessage().isEmpty()) {
//                chatRecordRepository.updateReplyTargetSender(Integer.parseInt(message.getReplyTargetSenderId()), recorded.getId());
                User replyTargetSender = userRepository.findById(Integer.parseInt(message.getReplyTargetSenderId())).orElse(null);
                if (replyTargetSender == null) {
                    throw new ChatException("Invalid reply target sender");
                }
                chatRecord.setReplyTargetSender(replyTargetSender);
            }
            chatRoomRepository.save(chatRoom);

            //content filters

            boolean isTextNSFW = aiService.detectNSFW(chatRecord.getMessage());

            if (!isTextNSFW && chatRecord.getReplyTargetMessage() != null) {
                isTextNSFW = aiService.detectNSFW(chatRecord.getReplyTargetMessage());
            }
            boolean isImageNSFW = false;
            if (attachments != null && attachments.length > 0) {
                for (MultipartFile attachment : attachments) {
                    if (aiService.detectNSFW(attachment)) {
                        isImageNSFW = true;
                        break;
                    }
                }
            }

            chatRecord.setNsfw(isTextNSFW || isImageNSFW);


            boolean isSpam = aiService.detectSpam(chatRecord.getMessage());
            chatRecord.setSpam(isSpam);

            ChatRecord recorded = chatRecordRepository.save(chatRecord);



            //update notifications for participants
            for (User participant : chatRoom.getParticipants()) {


                if (participant.getId() != userId) {
                    boolean isGroupChat = chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty();
                    boolean isFriends = userRepository.isFriend(userId, participant.getId());
                    //if the participant has blocked this user, do not update the participant's notification count
                    if (userRepository.isBlocked(participant.getId(), userId)) {
                        continue;
                    }

                    //if this record is a spam message, register notification on the spam mailbox
                    if (recorded.getSpam()) {

                        boolean shouldUpdateSpamNotification = true;

                        if (participant.getSpamFilterMode().equals("Groups") && !isGroupChat) {
                            shouldUpdateSpamNotification = false;
                        } else if (participant.getSpamFilterMode().equals("Friends") && (!isFriends || isGroupChat)) {
                            shouldUpdateSpamNotification = false;
                        } else if (participant.getSpamFilterMode().equals("Others") && (isFriends || isGroupChat)) {
                            shouldUpdateSpamNotification = false;
                        } else if (participant.getSpamFilterMode().equals("None")) {
                            shouldUpdateSpamNotification = false;
                        }

                        if (recorded.getNsfw()) {
                            if (isGroupChat && participant.getNsfwGroups().equals("Block")) {
                                shouldUpdateSpamNotification = false;
                            }
                            if (!isGroupChat && isFriends && participant.getNsfwDmFriends().equals("Block")) {
                                shouldUpdateSpamNotification = false;
                            }
                            if (!isGroupChat && !isFriends && participant.getNsfwDmOthers().equals("Block")) {
                                shouldUpdateSpamNotification = false;
                            }
                        }

                        if (shouldUpdateSpamNotification) {
                            ChatNotificationCount cnt = chatNotificationCountRepository.findByChatRoomIdAndUserId(-1, participant.getId());
                            if(cnt == null) {
                                throw new GenericException("Unexpected error while updating chat notification data");
                            }
                            if (cnt.getLatestMessageId() == null) {
                                //latest message as in oldest unread message
                                cnt.setLatestMessageId(recorded.getId());
                                cnt.setFirstUnreadTimestamp(recorded.getDate().toInstant().toEpochMilli());
                                cnt.setCount(1);
                            }
                            else
                                cnt.setCount(cnt.getCount() + 1);
                            chatNotificationCountRepository.save(cnt);
                        }

                    }

                    boolean shouldUpdateNotification = true;

                    //conditionally update notification depending on user settings

                    if (recorded.getSpam()) {
                        if (participant.getSpamFilterMode().equals("Groups") && isGroupChat) {
                            shouldUpdateNotification = false;
                        } else if (participant.getSpamFilterMode().equals("Friends") && isFriends && !isGroupChat) {
                            shouldUpdateNotification = false;
                        } else if (participant.getSpamFilterMode().equals("Others") && !isFriends && !isGroupChat) {
                            shouldUpdateNotification = false;
                        } else if (participant.getSpamFilterMode().equals("All")) {
                            shouldUpdateNotification = false;
                        }
                    }


                    if (recorded.getNsfw()) {
                        if (isGroupChat && participant.getNsfwGroups().equals("Block")) {
                            shouldUpdateNotification = false;
                        }
                        if (!isGroupChat && isFriends && participant.getNsfwDmFriends().equals("Block")) {
                            shouldUpdateNotification = false;
                        }
                        if (!isGroupChat && !isFriends && participant.getNsfwDmOthers().equals("Block")) {
                            shouldUpdateNotification = false;
                        }
                    }


                    if (shouldUpdateNotification) {
                        ChatNotificationCount cnt = chatNotificationCountRepository.findByChatRoomIdAndUserId(Integer.parseInt(chatroomId), participant.getId());
                        if(cnt == null) {
                            throw new GenericException("Unexpected error while updating chat notification data");
                        }
                        if (cnt.getLatestMessageId() == null) {
                            //latest message as in oldest unread message
                            cnt.setLatestMessageId(recorded.getId());
                            cnt.setFirstUnreadTimestamp(recorded.getDate().toInstant().toEpochMilli());

                            cnt.setCount(1);
                        }
                        else
                            cnt.setCount(cnt.getCount() + 1);

                        chatNotificationCountRepository.save(cnt);
                    }

                }
            }


            Set<User> participants = chatRoom.getParticipants();

            String finalActualPaths = actualPaths;

            Hibernate.initialize(chatRoom.getParticipants());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //actually save the attachments
                    if (attachments != null && finalActualPaths != null)
                        saveAttachments(attachments, finalActualPaths);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("chatRoom", chatRoom);
                    payload.put("chatRecord", chatRecord);




                    //send push notifications to offline users
                    try {
                        pushNotificationService.sendChatNotifications(participants, List.of(chatRecord), chatRoom);
                    }
                    catch(Exception e) {
                        log.error("Error while sending push notification: " + e.getMessage());
                        e.printStackTrace();
                    }


                    //broadcast message to its participants
                    for (User participant : participants) {
                        if (participant.getId() != userId) {

                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/" + chatRoom.getId(), Map.of("chatRecord", chatRecord));
                            if (recorded.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage/-1", Map.of("chatRecord", chatRecord, "chatRoom", chatRoom));
                            }
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessage", payload);

                        }



                    }
                }
            });

            return recorded;
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }


    }


    @Transactional
    public int unReactMessage(String usernameWithId, String chatroomId, String chatRecordId, String code) throws GenericException {

        if (!code.startsWith(":") || !code.endsWith(":")) {
            throw new ChatException("Invalid reaction code");
        }
        int userId = userService.extractId(usernameWithId);
        //unreact message
        try {
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            Optional<ChatRecord> chatRecordOptional = chatRecordRepository.findById(Integer.parseInt(chatRecordId));
            if (!chatRecordOptional.isPresent()) {
                throw new ChatException("Chat message not found");
            }
            if (!chatRecordOptional.get().getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Chat record does not belong to the chatroom");
            }
            ChatRecord chatRecord = chatRecordOptional.get();
            List<ChatReaction> reactions = chatRecord.getChatReactions();
            ChatReaction existingReaction = null;
            int i = 0;
            int removeI = 0;

            for (ChatReaction reaction : reactions) {
                if (reaction.getReactorId() == userId && reaction.getCode().equals(code)) {
                    existingReaction = reaction;
                    removeI = i;
                    break;
                }
                i++;
            }

            if (existingReaction == null) {
                throw new ChatException("This chat reaction does not exist");
            }


            chatReactionRepository.delete(existingReaction);
            chatRecord.getChatReactions().remove(removeI);


            chatRecord.incrementVersion();
            chatRecordRepository.save(chatRecord);

            Set<User> participants = chatRoom.getParticipants();

            ChatReaction finalExistingReaction = existingReaction;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast to its participants
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageUnreact/" + chatRoom.getId(), finalExistingReaction);
                            if (chatRecord.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageUnreact/-1", finalExistingReaction);
                            }
                        }
                    }
                }
            });


            return existingReaction.getId();
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }
    }

    @Transactional
    public ChatReaction reactMessage(String usernameWithId, String chatroomId, String chatRecordId, String code) throws GenericException {

        if (!code.startsWith(":") || !code.endsWith(":")) {
            throw new ChatException("Invalid reaction code");
        }
        User user = userService.findByUsernameWithId(usernameWithId);
        int userId = user.getId();
        //react message
        try {
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            Optional<ChatRecord> chatRecordOptional = chatRecordRepository.findById(Integer.parseInt(chatRecordId));
            if (!chatRecordOptional.isPresent()) {
                throw new ChatException("No chat message found");
            }
            if (!chatRecordOptional.get().getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Chat record does not belong to the chatroom");
            }
            ChatRecord chatRecord = chatRecordOptional.get();
            List<ChatReaction> reactions = chatRecord.getChatReactions();
            ChatReaction existingReaction = null;

            if (reactions.size() > 50) {
                throw new ChatException("This chat message received enough types of reactions!");
            }

            for (ChatReaction reaction : reactions) {
                if (reaction.getReactorId() == userId && reaction.getCode().equals(code)) {
                    existingReaction = reaction;
                    break;
                }
            }


            if (existingReaction != null) {
                throw new ChatException("This reaction is already present in this message!");
            } else {
                ChatReaction chatReaction = new ChatReaction(null, code, userId, chatRecord.getId(), Integer.parseInt(chatroomId), chatRecord, (user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername()), usernameWithId.replace("@", "#"));
                chatRecord.getChatReactions().add(chatReaction);

                existingReaction = chatReaction;
                chatReactionRepository.save(chatReaction);
            }

            chatRecord.incrementVersion();
            chatRecordRepository.save(chatRecord);


            Set<User> participants = chatRoom.getParticipants();
            ChatReaction finalExistingReaction = existingReaction;


            boolean isGroupChat = chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty();

            if (chatRecord.getSender().getNotifyReaction().equals("all") || (!isGroupChat && chatRecord.getSender().getNotifyReaction().equals("dm")))
                chatRoomService.announceSystemMessagesBatchPrivate(chatroomId, List.of(new SystemMessageDetails(
                        "private_" + chatRecord.getSender().getId() + "_react", user, null, code + "@" + chatRecord.getId()

                )), List.of(chatRecord.getSender()), true);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //broadcast to its participants
                    for (User participant : participants) {
                        if (participant.getId() != userId) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageReact/" + chatRoom.getId(), finalExistingReaction);


                            if (chatRecord.getSpam()) {
                                broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onChatMessageReact/-1", finalExistingReaction);
                            }
                        }
                    }
                }
            });


            return existingReaction;
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }
    }

    private List<String> cleanTag(List<String> rawTags) throws GenericException {
        if (rawTags.size() > 10) {
            //more than 10 tags are not supported
            throw new ChatException("Too many filters set - try simplifying your search filters!");
        }
        //remove duplicates
        return rawTags.stream().distinct().toList();
    }

    @Transactional(readOnly = true)
    public List<ChatRecord> searchChatRecord(String usernameWithId, String chatroomId, Integer cursorId, boolean previous, SearchOrder searchOrder, String content, List<String> tags, String localTimezone,
                                             ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag, boolean querySpam) throws GenericException {
        try {

            if (content.length() > 255) {
                throw new ChatException("Search query too long");
            }
            //verify chatroom belongs to user

            ChatRoom chatRoom = querySpam ? null : chatRoomService.findChatRoomById(usernameWithId, chatroomId);

            User thisUser = userService.findByUsernameWithId(usernameWithId);
            Set<ChatRoom> userChatRooms = querySpam ? thisUser.getChatRooms() : null;
            List<ChatRecordSearchFilter> searchFilters = new ArrayList<>();
            tags = cleanTag(tags);
            for (String tag : tags) {
                //from:user tag
                if (tag.startsWith("from:") || tag.startsWith("mentions:")) {
                    String targetUsername = tag.substring(tag.indexOf(":") + 1);
                    if (targetUsername.isEmpty()) {
                        throw new ChatException("Invalid search tag");
                    }
                    User user = null;
                    if (!tag.endsWith("everyone")) {
                        if (targetUsername.contains("#") || targetUsername.contains("@")) {
                            targetUsername = targetUsername.replace("#", "@");
                            user = userService.findByUsernameWithId(targetUsername);
                        } else {
                            user = userRepository.findFirstByNicknameOrUsername(targetUsername, targetUsername);
                        }
                    } else {
                        //dummy user to represent "everyone"
                        user = new User();
                        user.setId(-100);
                    }
                    if (user == null) {
                        throw new ChatException("Invalid user");
                    }

                    searchFilters.add(tag.startsWith("from:") ? ChatRecordSearchFilter.createFromUserFilter(user) :
                            ChatRecordSearchFilter.createMentionsUserFilter(user));

                } else if (tag.startsWith("has:")) {

                    String target = tag.substring(tag.indexOf(":") + 1);
                    if (target.isEmpty()) {
                        throw new ChatException("Invalid has target");
                    }
                    if (target.equals("link")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasLinkFilter());
                    } else if (target.equals("embed")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasEmbedFilter());
                    } else if (target.equals("file")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasFileFilter());
                    } else if (target.equals("image")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasImageFilter());
                    } else if (target.equals("poll")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasPollFilter());
                    } else if (target.equals("video")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasVideoFilter());
                    } else if (target.equals("sound")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasSoundFilter());
                    } else if (target.equals("reply")) {
                        searchFilters.add(ChatRecordSearchFilter.createHasReplyFilter());
                    }
                } else if (tag.startsWith("pinned:")) {
                    String target = tag.substring(tag.indexOf(":") + 1);
                    if (target.isEmpty()) {
                        throw new ChatException("pinned target");
                    }
                    if (target.equals("true")) {
                        searchFilters.add(ChatRecordSearchFilter.createPinnedFilter(true));
                    } else if (target.equals("false")) {
                        searchFilters.add(ChatRecordSearchFilter.createPinnedFilter(false));
                    }
                } else if (tag.startsWith("before:")) {
                    long dashCount = tag.chars().filter(ch -> ch == '-').count();
                    String tagContent = tag.substring(tag.indexOf(":") + 1);
                    if (dashCount == 0) {
                        //could be plain year, or month name, or "week"
                        if (tagContent.matches("^20\\d{2}$")) {
                            tagContent += "-01-01";
                        } else if (tagContent.equalsIgnoreCase("january") || tagContent.equalsIgnoreCase("february")
                                || tagContent.equalsIgnoreCase("march") || tagContent.equalsIgnoreCase("april")
                                || tagContent.equalsIgnoreCase("may") || tagContent.equalsIgnoreCase("june") ||
                                tagContent.equalsIgnoreCase("july") || tagContent.equalsIgnoreCase("august")
                                || tagContent.equalsIgnoreCase("september") || tagContent.equalsIgnoreCase("october") ||
                                tagContent.equalsIgnoreCase("november") || tagContent.equalsIgnoreCase("december")) {
                            int currentYear = ZonedDateTime.now(ZoneId.of(localTimezone)).getYear();
                            tagContent = currentYear + "-" + (Month.valueOf(tagContent.toUpperCase()).getValue()) + "-01";
                        } else if (tagContent.equals("week")) {
                            //get this week's monday
                            LocalDate now = ZonedDateTime.now(ZoneId.of(localTimezone)).toLocalDate();
                            LocalDate monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                            tagContent = monday.format(TimeUtils.LOCAL_FORMAT);
                        }

                    } else if (dashCount == 1) {
                        //yyyy-mm format
                        tagContent += "-01";
                    }

                    try {
                        LocalDate date = LocalDate.parse(tagContent, TimeUtils.LOCAL_FORMAT);
                        searchFilters.add(ChatRecordSearchFilter.createBeforeDateFilter(date, localTimezone));
                    } catch (DateTimeParseException e) {
                        throw new ChatException("Invalid date filter");
                    }

                } else if (tag.startsWith("after:")) {
                    long dashCount = tag.chars().filter(ch -> ch == '-').count();
                    String tagContent = tag.substring(tag.indexOf(":") + 1);
                    if (dashCount == 0) {
                        //could be plain year, or month name, or week
                        if (tagContent.matches("^20\\d{2}$")) {
                            tagContent += "-12-31";
                        } else if (tagContent.equalsIgnoreCase("january") || tagContent.equalsIgnoreCase("february")
                                || tagContent.equalsIgnoreCase("march") || tagContent.equalsIgnoreCase("april")
                                || tagContent.equalsIgnoreCase("may") || tagContent.equalsIgnoreCase("june") ||
                                tagContent.equalsIgnoreCase("july") || tagContent.equalsIgnoreCase("august")
                                || tagContent.equalsIgnoreCase("september") || tagContent.equalsIgnoreCase("october") ||
                                tagContent.equalsIgnoreCase("november") || tagContent.equalsIgnoreCase("december")) {
                            int currentYear = ZonedDateTime.now(ZoneId.of(localTimezone)).getYear();
                            YearMonth yearMonth = YearMonth.of(currentYear, Month.valueOf(tagContent.toUpperCase()).getValue());
                            LocalDate endMonthLocalDate = yearMonth.atEndOfMonth();
                            tagContent = endMonthLocalDate.format(TimeUtils.LOCAL_FORMAT);
                        } else if (tagContent.equals("week")) {
                            //future is unset
                            return List.of();
                        }

                    } else if (dashCount == 1) {
                        //yyyy-mm format
                        if (!tagContent.matches("^20\\d{2}-(0?[1-9]|1[0-2])$")) {
                            throw new ChatException("Invalid date filter");
                        }
                        String[] split = tagContent.split("-");
                        int year = Integer.parseInt(split[0]);
                        int month = Integer.parseInt(split[1]);
                        YearMonth yearMonth = YearMonth.of(year, month);
                        LocalDate endMonthLocalDate = yearMonth.atEndOfMonth();

                        tagContent = endMonthLocalDate.format(TimeUtils.LOCAL_FORMAT);
                    }

                    try {
                        LocalDate date = LocalDate.parse(tagContent, TimeUtils.LOCAL_FORMAT);
                        searchFilters.add(ChatRecordSearchFilter.createAfterDateFilter(date, localTimezone));
                    } catch (DateTimeParseException e) {
                        throw new ChatException("Invalid date filter");
                    }

                } else if (tag.startsWith("during:")) {
                    long dashCount = tag.chars().filter(ch -> ch == '-').count();
                    String tagContent = tag.substring(tag.indexOf(":") + 1);
                    String startDateString = "";
                    String endDateString = "";
                    if (dashCount == 0) {
                        //could be plain year, or month name or week
                        if (tagContent.matches("^20\\d{2}$")) {
                            int year = Integer.parseInt(tagContent);
                            startDateString = year + "-01-01";
                            endDateString = year + "-12-31";
                        } else if (tagContent.equalsIgnoreCase("january") || tagContent.equalsIgnoreCase("february")
                                || tagContent.equalsIgnoreCase("march") || tagContent.equalsIgnoreCase("april")
                                || tagContent.equalsIgnoreCase("may") || tagContent.equalsIgnoreCase("june") ||
                                tagContent.equalsIgnoreCase("july") || tagContent.equalsIgnoreCase("august")
                                || tagContent.equalsIgnoreCase("september") || tagContent.equalsIgnoreCase("october") ||
                                tagContent.equalsIgnoreCase("november") || tagContent.equalsIgnoreCase("december")) {
                            int currentYear = ZonedDateTime.now(ZoneId.of(localTimezone)).getYear();
                            int month = Month.valueOf(tagContent.toUpperCase()).getValue();
                            YearMonth yearMonth = YearMonth.of(currentYear, month);
                            LocalDate endMonthLocalDate = yearMonth.atEndOfMonth();
                            startDateString = currentYear + "-" + month + "-01";
                            endDateString = endMonthLocalDate.format(TimeUtils.LOCAL_FORMAT);
                        } else if (tagContent.equals("week")) {
                            LocalDate now = ZonedDateTime.now(ZoneId.of(localTimezone)).toLocalDate();
                            LocalDate monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                            LocalDate sunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                            startDateString = monday.format(TimeUtils.LOCAL_FORMAT);
                            endDateString = sunday.format(TimeUtils.LOCAL_FORMAT);
                        }
                    } else if (dashCount == 1) {
                        //yyyy-mm format
                        if (!tagContent.matches("^20\\d{2}-(0?[1-9]|1[0-2])$")) {
                            throw new ChatException("Invalid date filter");
                        }
                        String[] split = tagContent.split("-");
                        int year = Integer.parseInt(split[0]);
                        int month = Integer.parseInt(split[1]);
                        YearMonth yearMonth = YearMonth.of(year, month);
                        LocalDate endMonthLocalDate = yearMonth.atEndOfMonth();
                        startDateString = year + "-" + month + "-01";
                        endDateString = endMonthLocalDate.format(TimeUtils.LOCAL_FORMAT);
                    } else if (dashCount == 2) {
                        //yyyy-mm-dd format
                        startDateString = tagContent;
                        endDateString = tagContent;

                    }

                    try {
                        LocalDate startDate = LocalDate.parse(startDateString, TimeUtils.LOCAL_FORMAT);
                        LocalDate endDate = LocalDate.parse(endDateString, TimeUtils.LOCAL_FORMAT);
                        searchFilters.add(ChatRecordSearchFilter.createAfterDateFilterInclusive(startDate, localTimezone));
                        searchFilters.add(ChatRecordSearchFilter.createBeforeDateFilterInclusive(endDate, localTimezone));
                    } catch (DateTimeParseException e) {
                        throw new ChatException("Invalid date filter");
                    }


                }
            }

            if (!content.isEmpty()) {
                searchFilters.add(ChatRecordSearchFilter.createContentFilter(content));
            }

            if (searchFilters.isEmpty()) {
                return List.of();
            }

            Set<User> blockeds = userService.findAllBlockedAssociationsOf(thisUser);
            Set<User> userFilter = null;
            String spamFilterMode = thisUser.getSpamFilterMode();
            if (querySpam) {
                blockeds.add(thisUser);
                if (spamFilterMode.equals("None")) {
                    return List.of();
                } else if (spamFilterMode.equals("Friends")) {
                    userFilter = thisUser.getFriends();
                    userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
                } else if (spamFilterMode.equals("Others")) {
                    blockeds.addAll(thisUser.getFriends());
                    userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
                } else if (spamFilterMode.equals("Groups")) {
                    userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() == null || e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
                }
            }


            return chatRecordRepository.searchChatRecord(chatRoom, blockeds, searchFilters, cursorId, searchOrder, previous, userService.extractId(usernameWithId), nsfwFlag, spamFlag, querySpam, userChatRooms, userFilter);
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id format");
        }
    }


    @Transactional(readOnly = true)
    public boolean verifyCheckRecordExistsById(String usernameWithId, String chatroomId, String id, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) throws GenericException {
        try {
            User user = userService.findByUsernameWithId(usernameWithId);
            Set<User> blocked = userService.findAllBlockedAssociationsOf(user);

            //validate this user belongs to this chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);

            if (chatRecordRepository.hasChatRecordById(chatRoom, blocked, Integer.parseInt(id), user.getId(), nsfwFlag, spamFlag)) {
                return true;
            } else {
                throw new ChatException("Chat message does not exist");
            }
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id format");
        }
    }

    @Transactional(readOnly = true)
    public boolean verifySpamCheckRecordExistsById(String usernameWithId, String id, ContentFilterFlag nsfwFlag) throws GenericException {
        try {
            User user = userService.findByUsernameWithId(usernameWithId);
            Set<User> blocked = userService.findAllBlockedAssociationsOf(user);
            blocked.add(user);

            Set<ChatRoom> userChatRooms = user.getChatRooms();
            Set<User> userFilter = null;

            String filterMode = user.getSpamFilterMode();

            if (filterMode.equals("None")) {
                return false;
            } else if (filterMode.equals("Friends")) {
                userFilter = user.getFriends();
                userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
            } else if (filterMode.equals("Others")) {
                blocked.addAll(user.getFriends());
                userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());

            } else if (filterMode.equals("Groups")) {
                userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() == null || e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
            }


            if (chatRecordRepository.hasSpamChatRecordById(userChatRooms, blocked, Integer.parseInt(id), nsfwFlag, userFilter)) {
                return true;
            } else {
                throw new ChatException("Chat message does not exist");
            }
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid id format");
        }
    }


    @Transactional
    public void pinMessage(String usernameWithId, String chatroomId, String chatRecordId) throws GenericException {
        if (chatroomId.equals("-1")) {
            throw new GenericException("Pinning messages not supported in spam mailbox");
        }
        try {
            //validate user belongs to this chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);


            User user = userService.findByUsernameWithId(usernameWithId);

            if (chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
                ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
                if (roleSettings == null) {
                    throw new GenericException("Unexpected error while pinning messages");
                }
                chatRoomService.checkRoomPermission(chatRoom, user.getId(), null, roleSettings.getRoleAllowPinMessage());
            }

            //get pin chats count (50 limit per chatroom)
            List<ChatRecord> pinned = chatRecordRepository.getAllPinnedByChatRoomId(chatRoom, ContentFilterFlag.ANY, ContentFilterFlag.ANY);
            if (pinned.size() >= 50) {
                throw new ChatException("Cannot pin more than 50 messages");
            }
            ChatRecord chatRecord = chatRecordRepository.findById(Integer.parseInt(chatRecordId)).orElse(null);
            if (chatRecord == null) {
                throw new ChatException("Chat record not found");
            }
            if (!chatRecord.getType().equals("text")) {
                throw new ChatException("Cannot pin system messages");
            }
            if (!chatRecord.getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Chat record does not belong to the chatroom");
            }

            if (chatRecord.getPinned()) {
                throw new ChatException("Chat message already pinned");
            }


            chatRecord.setPinned(true);
            chatRecord.setPinnedDate(TimeUtils.getCurrentKST());
            chatRecordRepository.save(chatRecord);

            List<SystemMessageDetails> systemMessageDetails = List.of(new SystemMessageDetails("pin_" + chatRecordId, user, null, null));

            chatRoomService.announceSystemMessagesBatch(chatroomId, systemMessageDetails, chatRoom.getParticipants());

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }
    }

    @Transactional
    public void unpinMessage(String usernameWithId, String chatroomId, String chatRecordId) throws GenericException {
        if (chatroomId.equals("-1")) {
            throw new GenericException("Unpinning messages not supported in spam mailbox");
        }
        try {
            //validate user belongs to this chatroom
            ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);
            if(chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty()) {
                ChatRoomRoleSettings roleSettings = chatRoomRoleSettingsRepository.findByChatRoomId(chatRoom.getId());
                if(roleSettings == null) {
                    throw new GenericException("Unexpected error while unpinning messages");
                }
                chatRoomService.checkRoomPermission(chatRoom, userService.extractId(usernameWithId), null,roleSettings.getRoleAllowPinMessage() );
            }

            ChatRecord chatRecord = chatRecordRepository.findById(Integer.parseInt(chatRecordId)).orElse(null);
            if (chatRecord == null) {
                throw new ChatException("Chat record not found");
            }
            if (!chatRecord.getChatRoom().getId().equals(chatRoom.getId())) {
                throw new ChatException("Chat record does not belong to the chatroom");
            }
            if (!chatRecord.getPinned()) {
                throw new ChatException("Chat message already unpinned");
            }
            chatRecord.setPinned(false);
            chatRecordRepository.save(chatRecord);

        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chat record id");
        }
    }

    @Transactional
    public List<ChatRecord> getPinnedChatRecords(String usernameWithId, String chatroomId, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) {
        if (chatroomId.equals("-1")) {
            throw new GenericException("Pin message feature unsupported in spam mailbox");
        }
        int userId = userService.extractId(usernameWithId);
        User user = userService.findByUsernameWithId(usernameWithId);

        int chatroomId_ = 0;
        try {
            chatroomId_ = Integer.parseInt(chatroomId);
        } catch (NumberFormatException e) {
            throw new ChatException("Invalid chatroom");
        }

        //validate this user belongs to this chatroom
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);

        List<ChatRecord> chatRecords = chatRecordRepository.getAllPinnedByChatRoomIdBlockFiltered(chatRoom, userService.findAllBlockedAssociationsOf(user), nsfwFlag, spamFlag);

        return chatRecords;
    }


    @Transactional
    public List<ChatRecord> getChatRecords(String usernameWithId, String chatroomId, Integer keyId, boolean previous, ContentFilterFlag nsfwFlag, ContentFilterFlag spamFlag) throws GenericException {
        User user = userService.findByUsernameWithId(usernameWithId);
        Set<User> blocked = userService.findAllBlockedAssociationsOf(user);

        //validate this user belongs to this chatroom
        ChatRoom chatRoom = chatRoomService.findChatRoomById(usernameWithId, chatroomId);

        return previous ? chatRecordRepository.getPrevPageByChatRoomIdBlockFiltered(chatRoom, blocked, -keyId, user.getId(), nsfwFlag, spamFlag) :
                chatRecordRepository.getNextPageByChatRoomIdBlockFiltered(chatRoom, blocked, keyId, user.getId(), nsfwFlag, spamFlag);

    }

    @Transactional
    public List<ChatRecord> getSpamChatRecords(String usernameWithId, Integer keyId, boolean previous, ContentFilterFlag nsfwFlag) throws GenericException {
        User user = userService.findByUsernameWithId(usernameWithId);
        Set<User> blocked = userService.findAllBlockedAssociationsOf(user);
        blocked.add(user);
        Set<ChatRoom> userChatRooms = user.getChatRooms();
        Set<User> userFilter = null;

        String filterMode = user.getSpamFilterMode();

        if (filterMode.equals("None")) {
            //do not filter any spams = there are always no spams
            return List.of();
        } else if (filterMode.equals("Friends")) {
            //query only the spams from friends
            userFilter = user.getFriends();

            //query only the direct messaging chatrooms
            userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
        } else if (filterMode.equals("Others")) {
            //query only the spams from non-friends (not in friends)
            blocked.addAll(user.getFriends());
            //query only the direct messaging chatrooms
            userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() != null && !e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());

        } else if (filterMode.equals("Groups")) {
            //query only those from the group chatrooms
            userChatRooms = userChatRooms.stream().filter(e -> e.getDirect1to1Identifier() == null || e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
        }


        return previous ? chatRecordRepository.getPrevPageSpam(userChatRooms, -keyId, blocked, nsfwFlag, userFilter) :
                chatRecordRepository.getNextPageSpam(userChatRooms, keyId, blocked, nsfwFlag, userFilter);

    }


}
