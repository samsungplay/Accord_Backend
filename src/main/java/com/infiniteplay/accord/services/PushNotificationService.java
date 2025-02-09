package com.infiniteplay.accord.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteplay.accord.entities.ChatRecord;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.PushSubscription;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.PushSubscriptionDTO;
import com.infiniteplay.accord.models.UserStatus;
import com.infiniteplay.accord.repositories.PushSubscriptionRepository;
import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.utils.GenericException;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserService userService;
    @Value("${notification.vapid.publicKey}")
    private String vapidPublicKey;
    @Value("${notification.vapid.privateKey}")
    private String vapidPrivateKey;
    @Value("${server.url}")
    private String serverUrl;
    @Value("${client.url}")
    private String clientUrl;
    @Autowired
    private JWTHandler jwtHandler;


    private PushService pushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushNotificationService(PushSubscriptionRepository pushSubscriptionRepository, UserService userService) throws GeneralSecurityException {

        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.userService = userService;
    }


    @PostConstruct
    public void init() throws GeneralSecurityException {
        pushService = new PushService(vapidPublicKey, vapidPrivateKey);
    }


    @Transactional
    public void subscribe(String usernameWithId, PushSubscriptionDTO pushSubscriptionDTO, String loginSessionToken) throws GenericException {
        //may override subscription if it exists
        if(loginSessionToken == null || loginSessionToken.isEmpty()) {
            throw new GenericException("Invalid login session token while subscribing to push notifications");
        }
        int userId = userService.extractId(usernameWithId);
        PushSubscription existing = pushSubscriptionRepository.findByUserId(userId);

        if (existing != null) {
            existing.setAuth(pushSubscriptionDTO.getAuth());
            existing.setP256dh(pushSubscriptionDTO.getP256dh());
            existing.setLoginSessionToken(loginSessionToken);
            existing.setUserId(userId);
            existing.setEndpoint(pushSubscriptionDTO.getEndpoint());
            pushSubscriptionRepository.save(existing);
            return;
        }
        PushSubscription pushSubscription = new PushSubscription();
        pushSubscription.setAuth(pushSubscriptionDTO.getAuth());
        pushSubscription.setP256dh(pushSubscriptionDTO.getP256dh());
        pushSubscription.setLoginSessionToken(loginSessionToken);
        pushSubscription.setUserId(userId);
        pushSubscription.setEndpoint(pushSubscriptionDTO.getEndpoint());

        pushSubscriptionRepository.save(pushSubscription);


    }

    @Transactional
    public void unsubscribe(String usernameWithId) throws GenericException {
        PushSubscription pushSubscription = pushSubscriptionRepository.findByUserId(userService.extractId(usernameWithId));
        if (pushSubscription == null) {
            return;
        }
        pushSubscriptionRepository.delete(pushSubscription);
    }

    public void sendPushNotification(String title, String body, String icon, String url,
                                     PushSubscription subscription) {
        PushSubscriptionDTO pushSubscriptionDTO = new PushSubscriptionDTO();
        pushSubscriptionDTO.setAuth(subscription.getAuth());
        pushSubscriptionDTO.setP256dh(subscription.getP256dh());
        pushSubscriptionDTO.setEndpoint(subscription.getEndpoint());

        try {
            jwtHandler.readRefreshToken(subscription.getLoginSessionToken());
        } catch (Exception e) {
            //error while verifying jwt token
            log.info("push notification subscription expired; skipping sending notification");
            return;
        }
        try {
            dispatchNotificationInternal(pushSubscriptionDTO, objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "icon", icon,
                    "url", url
            )));
        } catch (Exception e) {
            //we can just log the error because failure to send push notification is not a critical error
            log.error("error while sending push notification:" + e.getMessage());
        }

    }


    @Transactional(readOnly = true)
    public void sendChatNotifications(Collection<User> users, List<ChatRecord> chatRecords, ChatRoom chatRoom) {
        List<Integer> userIds = users.stream().map(User::getId).collect(Collectors.toCollection(ArrayList::new));
        List<PushSubscription> pushSubscriptions = pushSubscriptionRepository.findByUserIdIn(userIds);
        Map<Integer, PushSubscription> pushNotificationLookup = new HashMap<>();
        for (PushSubscription pushSubscription : pushSubscriptions) {
            pushNotificationLookup.put(pushSubscription.getUserId(), pushSubscription);
        }


        boolean isGroupChat = chatRoom.getDirect1to1Identifier() == null || chatRoom.getDirect1to1Identifier().isEmpty();
        List<Integer> dmIds = !isGroupChat ? Arrays.stream(chatRoom.getDirect1to1Identifier().split("@")).map(Integer::parseInt).collect(Collectors.toCollection(ArrayList::new)) : List.of();


        Map<String, String> participantUsernameLookup = new HashMap<>();

        for (User participant : chatRoom.getParticipants()) {
            participantUsernameLookup.put(participant.getId().toString(), userService.getUserDisplayName(participant));
        }

        participantUsernameLookup.put("-100", "everyone");

        for (User user : users) {

            //if the user prefers not receiving the push notification, skip this user
            if (!user.getDoNotification()) {
                continue;
            }
            //do not send notification if the user status is do not disturb
            if (user.getStatus().equals(UserStatus.DO_NOT_DISTURB)) {
                continue;
            }
            //do not send notification if the user status is not offline, delegate the logic to client
            if (!user.getStatus().equals(UserStatus.OFFLINE)) {
                continue;
            }
            //no subscription found
            if(pushNotificationLookup.get(user.getId()) == null) {
                continue;
            }

            Set<User> blockedAssociations = userService.findAllBlockedAssociationsOf(user);
            Set<User> friends = user.getFriends();


            for (ChatRecord chatRecord : chatRecords) {
                Pattern mentionPattern = Pattern.compile("\\[m][^\\n\\r\\s\\[m\\]]+[^\\n\\r\\[m\\]]*[^\\n\\r\\s\\[m\\]]*\\[m]");

                Matcher matcher = mentionPattern.matcher(chatRecord.getMessage());
                List<String> mentionedUserIds = new ArrayList<>();
                StringBuffer buffer = new StringBuffer();
                //replace the mention tags with @username
                while (matcher.find()) {
                    String match = matcher.group();
                    String mentionedId = match.substring(4, match.length() - 3);
                    mentionedUserIds.add(mentionedId);
                    String replacement = "@" + participantUsernameLookup.getOrDefault(mentionedId, "Unknown user");
                    matcher.appendReplacement(buffer, replacement);

                }
                matcher.appendTail(buffer);

                String message = buffer.toString();

                //replace the spoiler tags with [SPOILER] depending on user preference

                if (user.getDisplaySpoiler().equals("click") || (user.getDisplaySpoiler().equals("owned") && isGroupChat &&
                        chatRoom.getOwnerId().equals(user.getId()))) {
                    Pattern spoilerPattern = Pattern.compile("\\|\\|[^\\n\\r|]+\\|\\|");

                    matcher = spoilerPattern.matcher(message);
                    buffer = new StringBuffer();
                    while (matcher.find()) {
                        String replacement = "[SPOILER]";
                        matcher.appendReplacement(buffer, replacement);
                    }
                    matcher.appendTail(buffer);

                    message = buffer.toString();

                }


                List<Integer> mutedChatRoomIds = user.getMutedChatRoomIds() != null ? new ArrayList<>(Arrays.asList(user.getMutedChatRoomIds())) : List.of();
                boolean hasUserMutedChatRoom = mutedChatRoomIds.contains(chatRoom.getId());

                boolean isUserMentioned = mentionedUserIds.stream().anyMatch(id -> id.equals(user.getId().toString()));

                //if the user has muted this chatroom, and the message does not explicitly mention the user, do not send the notification
                if (hasUserMutedChatRoom && !isUserMentioned) {
                    continue;
                }


                String nsfwFilterFlag = "ANY";
                String spamFilterFlag = "ANY";

                int dmTargetId = isGroupChat ? -1 : dmIds.stream().filter(id -> !Objects.equals(id, user.getId())).findFirst().orElse(-1);

                boolean isDmTargetFriends = !isGroupChat && friends.stream().anyMatch(friend -> friend.getId().equals(dmTargetId));
                if (!isGroupChat) {

                    if (isDmTargetFriends) {
                        nsfwFilterFlag = user.getNsfwDmFriends().equals("Block") ? "EXCLUDE" : "ANY";
                        spamFilterFlag = user.getSpamFilterMode().equals("All") || user.getSpamFilterMode().equals("Friends") ? "EXCLUDE" : "ANY";
                    } else {
                        nsfwFilterFlag = user.getNsfwDmOthers().equals("Block") ? "EXCLUDE" : "ANY";
                        spamFilterFlag = user.getSpamFilterMode().equals("All") || user.getSpamFilterMode().equals("Others") ? "EXCLUDE" : "ANY";
                    }
                } else {
                    nsfwFilterFlag = user.getNsfwGroups().equals("Block") ? "EXCLUDE" : "ANY";
                    spamFilterFlag = user.getSpamFilterMode().equals("All") || user.getSpamFilterMode().equals("Groups") ? "EXCLUDE" : "ANY";
                }

                //do not send notification if the user blocked nsfw/spam messages
                if (chatRecord.getType().equals("text") && chatRecord.getNsfw() && nsfwFilterFlag.equals("EXCLUDE")) {
                    continue;
                }
                if (chatRecord.getType().equals("text") && chatRecord.getSpam() && spamFilterFlag.equals("EXCLUDE")) {
                    continue;
                }

                //do not send notification if the sender of the chat record has either blocked or was blocked by this user
                if (blockedAssociations.stream().anyMatch(blocked -> blocked.getId().equals(chatRecord.getSender().getId()))) {
                    continue;
                }

                //do not send notification if the user prefers message requests, and the chat comes from non-friends user in a DM
                if (!isGroupChat && !isDmTargetFriends && user.getMessageRequests()) {
                    continue;
                }


                String title = "";
                String body = "";
                String icon = "";

                //compute chatroom name
                String chatRoomName = chatRoom.getName();
                if (!isGroupChat) {
                    if (chatRoom.getParticipants().size() <= 1) {
                        chatRoomName = "Invalid DM";
                    } else {
                        User otherUser = chatRoom.getParticipants().stream().filter(p -> !p.getId().equals(user.getId())).findFirst().orElse(null);
                        assert otherUser != null;
                        chatRoomName = "@" + userService.getUserDisplayName(otherUser);
                    }
                }


                //determine the title of the notification
                if ((chatRecord.getType().equals("text") ||
                        chatRecord.getType().equals("poll")) && chatRecord.getSender() != null) {
                    if (isGroupChat) {
                        title = userService.getUserDisplayName(chatRecord.getSender()) + " @ " + chatRoomName;
                    } else {
                        title = chatRoomName;
                    }
                } else if (chatRecord.getType().startsWith("system_")) {
                    title = isGroupChat ? "@" + chatRoomName : chatRoomName;
                }

                //determine the body of the notification
                if (chatRecord.getType().equals("text") && chatRecord.getNsfw()) {
                    if (!isGroupChat) {
                        if (isDmTargetFriends) {
                            if (!user.getNsfwDmFriends().equals("Show")) {
                                body = "This message has been marked as sensitive.";
                            } else {
                                body = message;
                            }
                        } else {
                            if (!user.getNsfwDmOthers().equals("Show")) {
                                body = "This message has been marked as sensitive";
                            } else {
                                body = message;
                            }
                        }

                    } else {
                        if (!user.getNsfwGroups().equals("Show")) {
                            body = "This message has been marked as sensitive.";
                        } else {
                            body = message;
                        }
                    }
                } else if (chatRecord.getType().equals("text") && (chatRecord.getAttachments() == null || chatRecord.getAttachments().isEmpty())) {
                    body = message;
                } else if (chatRecord.getType().equals("text")) {
                    boolean containsImage = chatRecord.getAttachments().contains(".jpeg") || chatRecord.getAttachments().contains(".png")
                            || chatRecord.getAttachments().contains(".jpg") ||
                            chatRecord.getAttachments().contains(".webp") ||
                            chatRecord.getAttachments().contains(".gif");

                    boolean containsAudio = chatRecord.getAttachments().contains(".mp3") || chatRecord.getAttachments().contains(".wav")
                            || chatRecord.getAttachments().contains(".ogg");

                    boolean containsVideo = chatRecord.getAttachments().contains(".mp4") || chatRecord.getAttachments().contains(".webm");

                    boolean containsText = chatRecord.getAttachments().contains(".txt") || chatRecord.getAttachments().contains(".java") ||
                            chatRecord.getAttachments().contains(".cpp");

                    if (containsImage) {
                        body = "Click to see image.";
                    } else if (containsAudio) {
                        body = "Click to see audio.";
                    } else if (containsVideo) {
                        body = "Click to see video.";
                    } else if (containsText) {
                        body = "Click to see text.";
                    } else {
                        body = "Click to see attachment.";
                    }


                } else if (chatRecord.getType().equals("poll") && chatRecord.getSender() != null &&
                        chatRecord.getPoll() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + "'s poll: " + chatRecord.getPoll().getQuestion();
                } else if (chatRecord.getType().startsWith("system_poll_expired") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + "'s poll expired: " +
                            chatRecord.getMessage().split("@")[0];
                } else if (chatRecord.getType().startsWith("system_join") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " joins the meditation.";
                } else if (chatRecord.getType().startsWith("system_grant_moderator") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " has been granted a moderator status.";
                } else if (chatRecord.getType().startsWith("system_revoke_moderator") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + "'s moderator status has been revoked.";
                } else if ((chatRecord.getType().startsWith("system_ownerleave") || chatRecord.getType().startsWith("system_leave")) &&
                        chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " left in search for peace.";
                } else if (chatRecord.getType().startsWith("system_kick") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " has been kicked into the wilderness.";
                } else if (chatRecord.getType().startsWith("system_private") && chatRecord.getType().contains("_missedCall")) {
                    body = "You missed a call.";
                } else if (chatRecord.getType().startsWith("system_private") && chatRecord.getType().contains("_react") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " reacteed with " + chatRecord.getMessage().split("@")[0] + " on your message.";
                } else if (chatRecord.getType().startsWith("system_endCall")) {
                    body = "A call came to an end.";
                } else if (chatRecord.getType().startsWith("system_startCall") && chatRecord.getSender() != null) {
                    body = userService.getUserDisplayName(chatRecord.getSender()) + " started a call.";
                }

                //determine the icon of the notification
                if ((chatRecord.getType().equals("text") || chatRecord.getType().equals("poll")) && chatRecord.getSender() != null) {
                    if (chatRecord.getSender().getProfileImageUrl() != null) {
                        icon = serverUrl + "/content/" + chatRecord.getSender().getProfileImageUrl();
                    } else {
                        icon = "https://api.dicebear.com/9.x/icons/svg?seed=accord&icon=tree&size=24&radius=50&backgroundColor=" + chatRecord.getSender().getProfileColor().substring(1);
                    }
                } else {
                    icon = serverUrl + "/content/accord_logo.png";
                }

                sendPushNotification(title, body, icon, clientUrl + "/dashboard/chatroom/" + chatRoom.getId(), pushNotificationLookup.get(user.getId()));
            }

        }


    }

    private void dispatchNotificationInternal(PushSubscriptionDTO subscription, String message) throws GeneralSecurityException, JoseException, IOException, ExecutionException, InterruptedException {
        byte[] payload = message.getBytes();

        Notification notification = new Notification(subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload
        );



        pushService.send(notification);
        System.out.println("sent push notification.");
    }


}

