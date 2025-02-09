package com.infiniteplay.accord.services;

import com.infiniteplay.accord.annotations.EnsureConsistency;
import com.infiniteplay.accord.entities.*;
import com.infiniteplay.accord.models.UserSettingsDTO;
import com.infiniteplay.accord.models.UserStatus;
import com.infiniteplay.accord.repositories.*;
import com.infiniteplay.accord.utils.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.infiniteplay.accord.services.AuthenticationService.computePasswordStrength;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final SimpMessagingTemplate broker;
    private final ChatReactionRepository chatReactionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final BackgroundRepository backgroundRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${filestorage.path}")
    String fileStoragePath;

    @Transactional
    public List<Integer> muteChatRoomNotification(String usernameWithId, int chatRoomId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        Set<Integer> chatRoomIds = user.getChatRooms().stream().map(ChatRoom::getId).collect(Collectors.toSet());


        List<Integer> mutedChatRoomIds = user.getMutedChatRoomIds() != null ? new ArrayList<>(Arrays.asList(user.getMutedChatRoomIds())) : new ArrayList<>();
        //lazily clean up any outdated chatroomIds
        mutedChatRoomIds = mutedChatRoomIds.stream().filter(chatRoomIds::contains).collect(Collectors.toCollection(ArrayList::new));
        if (mutedChatRoomIds.contains(chatRoomId)) {
            throw new UserException("User already muted this chatroom");
        }
        mutedChatRoomIds.add(chatRoomId);
        user.setMutedChatRoomIds(mutedChatRoomIds.toArray(new Integer[0]));
        userRepository.save(user);

        return mutedChatRoomIds;
    }

    @Transactional
    public List<Integer> unmuteChatRoomNotification(String usernameWithId, int chatRoomId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        Set<Integer> chatRoomIds = user.getChatRooms().stream().map(ChatRoom::getId).collect(Collectors.toSet());
        List<Integer> mutedChatRoomIds = user.getMutedChatRoomIds() != null ? new ArrayList<>(Arrays.asList(user.getMutedChatRoomIds())) : new ArrayList<>();
        //lazily clean up any outdated chatroomIds
        mutedChatRoomIds = mutedChatRoomIds.stream().filter(chatRoomIds::contains).collect(Collectors.toCollection(ArrayList::new));
        if (!mutedChatRoomIds.contains(chatRoomId)) {
            throw new UserException("User has not muted this chatroom");
        }
        mutedChatRoomIds = mutedChatRoomIds.stream().filter(e -> e != chatRoomId).collect(Collectors.toCollection(ArrayList::new));
        user.setMutedChatRoomIds(mutedChatRoomIds.toArray(new Integer[0]));
        userRepository.save(user);
        return mutedChatRoomIds;
    }

    private String getDisplayNameOf(User user) {
        return user.getNickname() != null && !user.getNickname().isEmpty() ?
                user.getNickname() : user.getUsername();
    }

    @Transactional(readOnly = true)
    public UserSettingsDTO getUserSettings(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);

        return new UserSettingsDTO(user.getSpamFilterMode(), user.getNsfwDmFriends(), user.getNsfwDmOthers(),
                user.getNsfwGroups(), user.isAllowNonFriendsDM(), user.isAllowFriendRequestEveryone(), user.isAllowFriendRequestFof(), user.isAllowFriendRequestGroup(), user.getEntranceSound(),
                user.isCanPreviewStream(), user.getNotifyReaction(), user.getDoNotification(), user.getMessageRequests(),
                user.getMutedChatRoomIds() != null ? Arrays.stream(user.getMutedChatRoomIds()).collect(Collectors.toCollection(ArrayList::new)) : new ArrayList<>(), user.getDisplaySpoiler());
    }

    @Transactional
    public void updateUserSettings(String usernameWithId, UserSettingsDTO userSettingsDTO) throws UserException {
        User user = findByUsernameWithId(usernameWithId);


        if (!Objects.equals(userSettingsDTO.getSpamFilterMode(), user.getSpamFilterMode())) {
            user.setSpamFilterMode(userSettingsDTO.getSpamFilterMode());
        }
        if (!Objects.equals(userSettingsDTO.getNsfwDmFriends(), user.getNsfwDmFriends())) {
            user.setNsfwDmFriends(userSettingsDTO.getNsfwDmFriends());
        }
        if (!Objects.equals(userSettingsDTO.getNsfwDmOthers(), user.getNsfwDmOthers())) {
            user.setNsfwDmOthers(userSettingsDTO.getNsfwDmOthers());
        }
        if (!Objects.equals(userSettingsDTO.getNsfwGroups(), user.getNsfwGroups())) {
            user.setNsfwGroups(userSettingsDTO.getNsfwGroups());
        }
        if (!Objects.equals(userSettingsDTO.isAllowNonFriendsDM(), user.isAllowNonFriendsDM())) {
            user.setAllowNonFriendsDM(userSettingsDTO.isAllowNonFriendsDM());
        }
        if (!Objects.equals(userSettingsDTO.isAllowFriendRequestEveryone(), user.isAllowFriendRequestEveryone())) {
            user.setAllowFriendRequestEveryone(userSettingsDTO.isAllowFriendRequestEveryone());
        }
        if (!Objects.equals(userSettingsDTO.isAllowFriendRequestFof(), user.isAllowFriendRequestFof())) {
            user.setAllowFriendRequestFof(userSettingsDTO.isAllowFriendRequestFof());
        }
        if (!Objects.equals(userSettingsDTO.isAllowFriendRequestGroup(), user.isAllowFriendRequestGroup())) {
            user.setAllowFriendRequestGroup(userSettingsDTO.isAllowFriendRequestGroup());
        }

        if (!Objects.equals(userSettingsDTO.getEntranceSound(), user.getEntranceSound())) {
            user.setEntranceSound(userSettingsDTO.getEntranceSound());
        }


        if (!Objects.equals(userSettingsDTO.isCanPreviewStream(), user.isCanPreviewStream())) {
            user.setCanPreviewStream(userSettingsDTO.isCanPreviewStream());

            Set<User> participants = new HashSet<>();

            for (ChatRoom chatRoom : user.getChatRooms()) {
                participants.addAll(chatRoom.getParticipants());
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (User participant : participants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserCanPreviewStream", Map.of("userId", user.getId(), "can", userSettingsDTO.isCanPreviewStream()));
                        }
                    }
                }

            });


        }

        if (!Objects.equals(userSettingsDTO.getNotifyReaction(), user.getNotifyReaction())) {
            user.setNotifyReaction(userSettingsDTO.getNotifyReaction());
        }

        if (!Objects.equals(userSettingsDTO.getDoNotification(), user.getDoNotification())) {
            user.setDoNotification(userSettingsDTO.getDoNotification());
        }

        if (!Objects.equals(userSettingsDTO.getMessageRequests(), user.getMessageRequests())) {
            user.setMessageRequests(userSettingsDTO.getMessageRequests());
        }

        if (!Objects.equals(userSettingsDTO.getDisplaySpoiler(), user.getDisplaySpoiler())) {
            user.setDisplaySpoiler(userSettingsDTO.getDisplaySpoiler());
        }


        userRepository.save(user);
    }

    public String getUserDisplayName(User user) {
        return user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
    }

    @Transactional
    public boolean isFriendofFriend(User user, User other) {
        Set<User> userFriends = user.getFriends();
        Set<User> otherFriends = other.getFriends();

        return !Collections.disjoint(userFriends, otherFriends);
    }

    @Transactional
    public boolean isSharingGroup(User user, User other) {
        Set<ChatRoom> userChatrooms = user.getChatRooms().stream().filter(e -> e.getDirect1to1Identifier() == null || e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
        Set<ChatRoom> otherChatrooms = other.getChatRooms().stream().filter(e -> e.getDirect1to1Identifier() == null || e.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());

        return !Collections.disjoint(userChatrooms, otherChatrooms);
    }


    @Transactional
    public void setEnableScreenShare(String usernameWithId, String enabled, String chatRoomId) throws GenericException {
        if (enabled == null) {
            throw new GenericException("Invalid enabled flag");
        }
        if (!enabled.equals("screenonly") && !enabled.equals("withaudio") && !enabled.equals("no")) {
            throw new GenericException("Invalid enabled flag");
        }
        User user = findByUsernameWithId(usernameWithId);
        user.setScreenShareEnabled(enabled);
        userRepository.save(user);

        Integer id = null;
        try {
            id = Integer.parseInt(chatRoomId);
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

        Hibernate.initialize(chatRoomInstance);
        Hibernate.initialize(chatRoomInstance.getParticipants());

        Set<User> participants = chatRoomInstance.getParticipants();


        final int finalChatRoomId = chatRoomInstance.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User participant : participants) {
                    if (!participant.getId().equals(user.getId())) {
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserScreenShareEnable/" + finalChatRoomId, Map.of("userId", user.getId(), "enabled", enabled));
                        broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserScreenShareEnable", Map.of("enabled", enabled, "chatRoomId", finalChatRoomId));
                    }
                }

            }
        });

    }


    @Transactional
    public void setEnableVideo(String usernameWithId, boolean enabled, String chatRoomId) throws GenericException {
        User user = findByUsernameWithId(usernameWithId);
        user.setVideoEnabled(enabled);
        userRepository.save(user);

        Integer id = null;
        try {
            id = Integer.parseInt(chatRoomId);
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

        Hibernate.initialize(chatRoomInstance);

        if (chatRoomInstance.getCallInstance() != null) {
            Hibernate.initialize(chatRoomInstance.getCallInstance().getActiveParticipants());
            Hibernate.initialize(chatRoomInstance.getCallInstance().getPendingParticipants());
        }

        Set<User> callParticipants = chatRoomInstance.getParticipants();

        //from here

        final int finalChatRoomId = chatRoomInstance.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!callParticipants.isEmpty()) {
                    for (User participant : callParticipants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserVideoEnable/" + finalChatRoomId, Map.of("userId", user.getId(), "enabled", enabled));
                        }
                    }
                }
            }
        });

    }

    @Transactional
    public void setDeafened(String usernameWithId, boolean deafened) throws GenericException {
        User user = findByUsernameWithId(usernameWithId);
        user.setDeafened(deafened);
        userRepository.save(user);


        Set<User> callParticipants = new HashSet<>();
        int chatRoomId = 0;

        for (ChatRoom chatRoom : user.getChatRooms()) {
            callParticipants.addAll(chatRoom.getParticipants());
        }
        if (user.getActiveCallInstance() != null) {
            Call call = user.getActiveCallInstance();
            chatRoomId = call.getChatRoom().getId();
        }


        int finalChatRoomId = chatRoomId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!callParticipants.isEmpty()) {
                    for (User participant : callParticipants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserDeafen", Map.of("userId", user.getId(), "deafened", deafened, "chatRoomId", finalChatRoomId));
                        }
                    }
                }
            }
        });

    }


    @Transactional
    public void setMute(String usernameWithId, boolean muted) throws GenericException {
        User user = findByUsernameWithId(usernameWithId);
        user.setCallMuted(muted);
        userRepository.save(user);

        Set<User> callParticipants = new HashSet<>();
        int chatRoomId = 0;

        for (ChatRoom chatRoom : user.getChatRooms()) {
            callParticipants.addAll(chatRoom.getParticipants());
        }
        if (user.getActiveCallInstance() != null) {
            Call call = user.getActiveCallInstance();
            chatRoomId = call.getChatRoom().getId();
        }


        int finalChatRoomId = chatRoomId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!callParticipants.isEmpty()) {
                    for (User participant : callParticipants) {
                        if (!participant.getId().equals(user.getId())) {
                            broker.convertAndSendToUser(participant.getUsername() + "@" + participant.getId(), "/general/onUserMute", Map.of("userId", user.getId(), "muted", muted, "chatRoomId", finalChatRoomId));
                        }
                    }
                }
            }
        });

    }

    @Transactional
    public User updateUserProfile(String usernameWithId, String username, String nickname, String email, String statusMessage, String aboutMe, MultipartFile profileImage)
            throws UserException {


        User user = findByUsernameWithId(usernameWithId);
        updateUsername(user, username);
        updateNickname(user, nickname);
        updateEmail(user, email);
        updateAboutMe(user, aboutMe);
        AtomicReference<String> pathToDelete = new AtomicReference<>();
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            pathToDelete.set(user.getProfileImageUrl());
        }
        String path = updateProfileImage(user, profileImage);
        updateStatusMessage(user, statusMessage);
        userRepository.save(user);
        //send to other users (friends)

        Set<User> friends = new HashSet<>(findAllFriendsOf(usernameWithId));
        //potentially, non-friends might need it (if they are viewing the user live on the chatroom page)
        for (ChatRoom chatRoom : user.getChatRooms()) {
            friends.addAll(chatRoom.getParticipants());
        }

        chatReactionRepository.updateChatReactionReactorNameByReactorId(getDisplayNameOf(user), user.getUsername() + "#" + user.getId(), user.getId());


        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                saveImage(profileImage, path);

                if (pathToDelete.get() != null) {
                    try {
                        Files.delete(Paths.get(fileStoragePath, pathToDelete.get()));
                    } catch (IOException ignored) {
                    }
                }

                for (User friend : friends) {
                    broker.convertAndSendToUser(friend.getUsername() + "@" + friend.getId(), "/general/onEditProfile", user);
                }
            }
        });


        return user;
    }

    @Transactional
    public void changePassword(String usernameWithId, String oldPassword, String newPassword) throws UserException {
        User user = findByUsernameWithId(usernameWithId);

        if (user.getAccountType() != AccountType.ACCORD) {
            throw new UserException("Cannot change password for non-accord account");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UserException("Incorrect password");
        }

        if (newPassword.trim().isEmpty() || newPassword.length() < 3 || newPassword.length() > 25 || computePasswordStrength(newPassword) < 0.7
        ) {
            throw new UserException("Invalid password");
        }


        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);

    }

    @Transactional
    public User updateUserProfile(String usernameWithId, String username, String nickname, String email, String statusMessage, String aboutMe, String profileColor)
            throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        updateUsername(user, username);
        updateNickname(user, nickname);
        updateEmail(user, email);
        updateAboutMe(user, aboutMe);

        deleteProfileImage(user);
        updateStatusMessage(user, statusMessage);
        updateProfileColor(user, profileColor);
        userRepository.save(user);
        chatReactionRepository.updateChatReactionReactorNameByReactorId(getDisplayNameOf(user), user.getUsername() + "#" + user.getId(), user.getId());
        //send to other users (friends)
        Set<User> friends = new HashSet<>(findAllFriendsOf(usernameWithId));
        //potentially, non-friends might need it (if they are viewing the user live on the chatroom page)
        for (ChatRoom chatRoom : user.getChatRooms()) {
            friends.addAll(chatRoom.getParticipants());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User friend : friends) {
                    broker.convertAndSendToUser(friend.getUsername() + "@" + friend.getId(), "/general/onEditProfile", user);
                }
            }
        });


        return user;
    }

    public void updateProfileColor(User user, String profileColor) throws UserException {
        if (!profileColor.startsWith("#") || profileColor.length() != 7) {
            throw new UserException("Invalid profile color");
        }
        user.setProfileColor(profileColor);
    }

    public void updateUsername(User user, String username) throws UserException {
        if (username.length() < 2 || username.length() > 30 || !Pattern.compile(RegexConstants.USER_REGEX).matcher(username).matches()) {
            throw new UserException("Invalid username");
        }

        user.setUsername(username);
    }

    public void updateNickname(User user, String nickname) throws UserException {
//        User user = findByUsernameWithId(usernameWithId);

        if (nickname.length() > 30) {
            throw new UserException("Invalid nickname length");
        }

        user.setNickname(nickname);

//        userRepository.save(user);
    }

    public void deleteProfileImage(User user) {
//        User user = findByUsernameWithId(usernameWithId);

        String profileImageUrl = user.getProfileImageUrl();

        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            try {
                Files.delete(Paths.get(fileStoragePath, profileImageUrl));
            } catch (IOException ignored) {
            }
        }

        user.setProfileImageUrl(null);

//        userRepository.save(user);

    }

    public String updateProfileImage(User user, MultipartFile file) {
        if (file.isEmpty() | file.getSize() > 1048576) {
            throw new UserException("Invalid profile image");
        }
        try {
            String[] split = file.getResource().getFilename().split("\\.");
            String extension = split[split.length - 1];
            if (!extension.equals("png") && !extension.equals("jpg")) {
                throw new UserException("Invalid profile image");
            }
            String path = "profileImage_" + user.getId() + "@" + System.currentTimeMillis() + "." + extension;
            user.setProfileImageUrl(path);

            return path;
        } catch (IndexOutOfBoundsException e) {
            throw new UserException("Image has no file extension");
        }
    }

    public void saveImage(MultipartFile file, String path) {
        if (file.isEmpty()) {
            throw new UserException("Invalid image");
        }
        try {
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, Paths.get(fileStoragePath, path), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UserException("Image upload failed");
        }
    }

    public void updateAboutMe(User user, String aboutMe) throws UserException {
        if (aboutMe.length() > 255) {
            throw new UserException("Invalid about me length");
        }
        user.setAboutMe(aboutMe);
    }

    public void updateEmail(User user, String email) throws UserException {
//        User user = findByUsernameWithId(usernameWithId);
        if (!AuthenticationService.isValidEmail(email)) {
            throw new UserException("Invalid email format");
        }

        User userWithEmail = userRepository.findByEmail(email);

        if (userWithEmail != null && !(userWithEmail.getUsername() + "@" + userWithEmail.getId()).equals(user.getUsername() + "@" + user.getId())) {
            throw new UserException("Email already in use");
        }

        user.setEmail(email);

//        userRepository.save(user);
    }

    public void updateStatusMessage(User user, String statusMessage) throws UserException {
//        User user = findByUsernameWithId(usernameWithId);

        if (statusMessage.length() > 50) {
            throw new UserException("Status message too long");
        }

        user.setStatusMessage(statusMessage);

//        userRepository.save(user);
    }


    @Transactional(readOnly = true)
    public String getAboutMe(String usernameWithId, int targetUserId) throws UserException {

        User user = findByUsernameWithId("user@" + targetUserId);

        int userId = extractId(usernameWithId);

        if (userId == user.getId()) {
            throw new UserException("Invalid about me request");
        }

        //if the user profile is public, get about me without restriction
        if (user.isAllowNonFriendsDM()) {
            return user.getAboutMe();
        }

        //otherwise, check if user belongs to the same chatroom, or are friends
        if (userRepository.isFriend(userId, targetUserId)) {
            return user.getAboutMe();
        }
        Set<ChatRoom> userChatRooms = findByUsernameWithId(usernameWithId).getChatRooms();
        Set<ChatRoom> targetChatRooms = user.getChatRooms();

        userChatRooms.retainAll(targetChatRooms);

        if (!userChatRooms.isEmpty()) {
            return user.getAboutMe();
        }

        return "Hidden";
    }

    @Transactional(readOnly = true)
    public String getAboutMe(String usernameWithId) throws UserException {

        User user = findByUsernameWithId(usernameWithId);

        return user.getAboutMe();
    }

    @Transactional
    @EnsureConsistency
    public void updateStatus(String usernameWithId, UserStatus status) throws UserException {

        User user = findByUsernameWithId(usernameWithId);

        user.setStatus(status);

        userRepository.save(user);

        Map<String, String> data = new HashMap<>();
        data.put("targetUser", usernameWithId);
        data.put("status", status.toString());

        //send to other users (friends)
        Set<User> friends = new HashSet<>(findAllFriendsOf(usernameWithId));
        //potentially, a non-friend user looking at user status info live on their chatroom page might need it
        for (ChatRoom chatRoom : user.getChatRooms()) {
            friends.addAll(chatRoom.getParticipants());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (User friend : friends) {
//            System.out.println("sending status update to other users!!!!: " + friend.getUsername() + "@" + friend.getId());
                    broker.convertAndSendToUser(friend.getUsername() + "@" + friend.getId(), "/general/onUserStatusUpdate", data);
                }
            }
        });


    }

    public int extractId(String usernameWithId) throws UserException {
        String[] split = usernameWithId.split("@");
        try {
            return Integer.parseInt(split[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new UserException("Invalid username");
        }
    }

    @Transactional
    public User findByUsernameWithId(String usernameWithId) throws UserException {
        String[] split = usernameWithId.split("@");
        try {
            int id = Integer.parseInt(split[1]);
            Optional<User> user = userRepository.findById(id);
            if (!user.isPresent()) {
                throw new UserException("User not found");
            }
            return user.get();
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new UserException("User not found");
        }

    }

    @Transactional
    public void cancelFriendRequest(String usernameWithId, String friendNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        User friend = findByUsernameWithId(friendNameWithId);
        if (friend == null) {
            throw new UserException("User not found");
        }

        if (Objects.equals(user.getId(), friend.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> outgoingPendings = user.getPendingOutgoing();
        if (!outgoingPendings.contains(friend)) {
            throw new UserException("Friend request not found");
        }

        Set<User> incomingPendings = friend.getPendingIncoming();

        outgoingPendings.remove(friend);
        incomingPendings.remove(user);

        user.incrementVersion();
        userRepository.save(user);
        userRepository.save(friend);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(friendNameWithId, "/general/onCancelFriendRequest", usernameWithId);
            }
        });


    }

    public Set<User> findOutgoingPendingsOf(String usernameWIthId) throws UserException {
        User user = findByUsernameWithId(usernameWIthId);
        if (user == null) {
            throw new UserException("User not found");
        }

        return user.getPendingOutgoing();
    }

    public Set<User> findIncomingPendingsOf(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }

        return user.getPendingIncoming();
    }

    @Transactional(readOnly = true)
    public List<Background> findAllBackgroundsOf(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        return user.getBackgrounds();
    }

    @Transactional(readOnly = true)
    public List<User> searchUser(String query) throws UserException {
        if (query.length() < 2) {
            throw new UserException("Invalid query");
        }
        int id = 0;
        if (query.contains("#")) {
            String idString = query.split("#")[1];
            try {
                id = Integer.parseInt(idString);
            } catch (NumberFormatException ignored) {

            }
        }
        List<User> users = userRepository.searchUser(query, id);


        return users;

    }


    @Transactional(readOnly = true)
    public List<User> getYouMayKnow(String usernameWithId, int targetSize) throws UserException {

        Set<User> youMayKnow = new HashSet<>();
        User user = findByUsernameWithId(usernameWithId);
        Set<User> friendsSet = user.getFriends();
        List<User> friends = new ArrayList<>(friendsSet);
        Collections.shuffle(friends);

        for (User randomFriend : friends) {
            List<User> friendsOfFriend = new ArrayList<>(randomFriend.getFriends());
            Collections.shuffle(friendsOfFriend);
            for (User friendofFriend : friendsOfFriend) {
                if (!friendofFriend.getId().equals(user.getId()) && !friendsSet.contains(friendofFriend) && friendofFriend.isAllowNonFriendsDM()) {
                    if (!youMayKnow.contains(friendofFriend)) {
                        youMayKnow.add(friendofFriend);
                        if (youMayKnow.size() >= targetSize) {
                            return new ArrayList<>(youMayKnow);
                        }
                    }

                }
            }
        }

        return new ArrayList<>(youMayKnow);

    }

    @Transactional
    public void addBackground(String usernameWithId, String name, MultipartFile file) throws GenericException {


        if (name.trim().isEmpty() || name.length() > 25 || name.contains("@")) {
            throw new GenericException("Name:Invalid Name");
        }

        float fileSizeinMB = file.getSize() / 1000000.0f;
        String fileName = file.getResource().getFilename();

        if (fileName == null || fileSizeinMB > 2

                || !fileName.contains(".")
                || !List.of("jpg", "jpeg", "png").contains(fileName.substring(fileName.lastIndexOf(".") + 1))) {


            throw new GenericException("File:Invalid File");
        }
        if (!Pattern.compile(RegexConstants.FILENAME_REGEX, Pattern.CASE_INSENSITIVE).matcher(fileName).matches()) {
            throw new GenericException("File:Invalid Filename");
        }

        User user = findByUsernameWithId(usernameWithId);

        if (user.getBackgrounds().size() > 50) {
            throw new GenericException("A chatroom cannot exceed 50 background images!");
        }

        if (
                user.getBackgrounds().stream().anyMatch(e -> e.getName().equals(name))
        ) {

            throw new GenericException("Name:Background with this name already exists!");
        }


        Background background = new Background();
        background.setUser(user);
        background.setName(name);

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        background.setFile("bgpersonal_" + user.getId() + "_" + name + "." + extension);

        user.getBackgrounds().add(background);

        backgroundRepository.save(background);
        user.incrementVersion();
        userRepository.save(user);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //actually save the file

                saveImage(file, background.getFile());

            }
        });


    }

    @Transactional
    public void deleteBackground(String usernameWithId, String name) throws GenericException {

        User user = findByUsernameWithId(usernameWithId);
        Background background = null;
        int backgroundIndex = -1;
        int i = 0;

        for (Background b : user.getBackgrounds()) {
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
        user.getBackgrounds().remove(backgroundIndex);

        user.incrementVersion();
        userRepository.save(user);
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

            }
        });


    }


    public Set<User> findAllFriendsOf(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        return user.getFriends();
    }

    public Set<User> findAllBlockedAssociationsOf(User user) {

        Set<User> associations = new HashSet<>();
        associations.addAll(user.getBlockers());
        associations.addAll(user.getBlocked());
        return associations;
    }

    public Set<User> findAllBlockedOf(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        return user.getBlocked();
    }

    public Set<User> findAllBlockersOf(String usernameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        return user.getBlockers();
    }

    //Send a friend request to a user
    @Transactional
    public User sendFriendRequest(String usernameWithId, String friendNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        if (user.getFriends().size() >= 100) {
            throw new UserException("Unfortunately, you have hit the limit of 100 friends!");
        }
        if (user.getPendingOutgoing().size() >= 100) {
            throw new UserException("You cannot have more than 100 outgoing friend requests at the same time!");
        }

        User newFriend = findByUsernameWithId(friendNameWithId);


        if (newFriend == null) {
            throw new UserException("User not found");
        }

        if (Objects.equals(user.getId(), newFriend.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        if (newFriend.getPendingIncoming().size() >= 100) {
            throw new UserException("Unfortunately, the target user already has more than 100 incoming friend requests - please try again later!");
        }


        Set<User> blocked = newFriend.getBlocked();
        if (blocked.contains(user)) {
            throw new UserException("Cannot send friend request to the user who blocked you!");
        }

        blocked = user.getBlocked();

        if (blocked.contains(newFriend)) {
            throw new UserException("Cannot send friend request to the user whom you blocked!");
        }

        if (userRepository.isFriend(user.getId(), newFriend.getId())) {
            throw new UserException("This user is already your friend!");
        }

        Set<User> outgoingPendingRequests = user.getPendingOutgoing();
        if (outgoingPendingRequests.contains(newFriend)) {
            throw new UserException("Friend request already pending");
        }
        Set<User> thisUserIncomingPendingRequests = user.getPendingIncoming();
        if (thisUserIncomingPendingRequests.contains(newFriend)) {
            throw new UserException("Friend request already pending");
        }

        //respect user friend request settings

        if (!newFriend.isAllowFriendRequestEveryone()) {

            if (!newFriend.isAllowFriendRequestFof() && !newFriend.isAllowFriendRequestGroup()) {
                throw new UserException("This user does not accept any friend requests");
            }

            boolean isFriendofFriend = isFriendofFriend(user, newFriend);
            boolean isSharingGroup = isSharingGroup(user, newFriend);

            if (newFriend.isAllowFriendRequestGroup() && newFriend.isAllowFriendRequestFof()
                    && !isFriendofFriend && !isSharingGroup) {
                throw new UserException("This user does not accept friend requests from only friends of friends or those in a same group");
            } else if (newFriend.isAllowFriendRequestFof() && !isFriendofFriend) {
                throw new UserException("This user accepts friend request from only friends of friends");
            } else if (newFriend.isAllowFriendRequestGroup() && !isSharingGroup) {
                throw new UserException("This user accepts friend request from only those in a same group");
            }
        }

        outgoingPendingRequests.add(newFriend);
        Set<User> incomingPendingRequests = newFriend.getPendingIncoming();

        incomingPendingRequests.add(user);
        user.incrementVersion();
        userRepository.save(user);
        newFriend.incrementVersion();
        userRepository.save(newFriend);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(newFriend.getUsername() + "@" + newFriend.getId(), "/general/onSendFriendRequest", user);
            }
        });


        return newFriend;
    }

    //Accept a friend request from a user
    @Transactional
    public User acceptFriendRequest(String usernameWithId, String friendNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        User newFriend = findByUsernameWithId(friendNameWithId);
        if (newFriend == null) {
            throw new UserException("User not found");
        }

        if (user.getFriends().size() >= 100) {
            throw new UserException("Unfortunately, you have hit the limit of 100 friends!");
        }


        if (Objects.equals(user.getId(), newFriend.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> incomingPendingRequests = user.getPendingIncoming();
        if (!incomingPendingRequests.contains(newFriend)) {
            throw new UserException("Friend request not found");
        }

        incomingPendingRequests.remove(newFriend);

        Set<User> outgoingPendingRequests = newFriend.getPendingOutgoing();

        outgoingPendingRequests.remove(user);

        Set<User> friends = user.getFriends();

        Set<User> friends2 = newFriend.getFriends();


        friends.add(newFriend);
        friends2.add(user);

        user.incrementVersion();
        userRepository.save(user);
        userRepository.save(newFriend);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(friendNameWithId, "/general/onAcceptFriendRequest", user);
            }
        });


        return newFriend;
    }

    @Transactional
    public void rejectFriendRequest(String usernameWithId, String friendNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        User newFriend = findByUsernameWithId(friendNameWithId);
        if (newFriend == null) {
            throw new UserException("User not found");
        }

        if (Objects.equals(user.getId(), newFriend.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> incomingPendingRequests = user.getPendingIncoming();
        if (!incomingPendingRequests.contains(newFriend)) {
            throw new UserException("Friend request not found");
        }

        incomingPendingRequests.remove(newFriend);

        Set<User> outgoingPendingRequests = newFriend.getPendingOutgoing();

        outgoingPendingRequests.remove(user);

        user.incrementVersion();
        userRepository.save(user);
        userRepository.save(newFriend);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(friendNameWithId, "/general/onRejectFriendRequest", usernameWithId);
            }
        });


    }

    @Transactional
    public void removeFriend(String usernameWithId, String friendNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        User friend = findByUsernameWithId(friendNameWithId);
        if (friend == null) {
            throw new UserException("User not found");
        }

        if (Objects.equals(user.getId(), friend.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> friends = user.getFriends();

        if (!friends.contains(friend)) {
            throw new UserException("Not a friend");
        }

        friends.remove(friend);

        Set<User> friends2 = friend.getFriends();

        friends2.remove(user);

        user.incrementVersion();
        userRepository.save(user);
        userRepository.save(friend);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(friend.getUsername() + "@" + friend.getId(), "/general/onRemoveFriend", usernameWithId);
            }
        });


    }

    @Transactional
    public User addBlocked(String usernameWithId, String blockNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }

        if (user.getBlocked().size() >= 100) {
            throw new UserException("Unfortunately, you have hit the limit of 100 blocks! Please unblock some users to block this user.");
        }
        User blocked = findByUsernameWithId(blockNameWithId);
        if (blocked == null) {
            throw new UserException("User not found");
        }


        if (Objects.equals(user.getId(), blocked.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> blocks = user.getBlocked();
        if (blocks.contains(blocked)) {
            throw new UserException("User already blocked");
        }


        blocks.add(blocked);
        blocked.getBlockers().add(user);

        //reject any friend request pending from the blocked user
        boolean hadIncomingPendingFriendRequest = false;
        boolean hasOutgoingPendingFriendRequest = false;

        Set<User> incomingPendingRequests = user.getPendingIncoming();
        hadIncomingPendingFriendRequest = incomingPendingRequests.contains(blocked);
        incomingPendingRequests.remove(blocked);
        Set<User> outgoingPendingRequests = blocked.getPendingOutgoing();
        outgoingPendingRequests.remove(user);

        //also reject it the other way around
        outgoingPendingRequests = user.getPendingOutgoing();
        if (outgoingPendingRequests.contains(blocked)) {
            hasOutgoingPendingFriendRequest = true;
            outgoingPendingRequests.remove(blocked);
        }
        incomingPendingRequests = blocked.getPendingIncoming();
        if (incomingPendingRequests.contains(user)) {
            incomingPendingRequests.remove(user);
        }

        user.incrementVersion();
        userRepository.save(user);
        blocked.incrementVersion();
        userRepository.save(blocked);

        boolean finalHadIncomingPendingFriendRequest = hadIncomingPendingFriendRequest;
        boolean finalHasOutgoingPendingFriendRequest = hasOutgoingPendingFriendRequest;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (finalHadIncomingPendingFriendRequest)
                    broker.convertAndSendToUser(blocked.getUsername() + "@" + blocked.getId(), "/general/onRejectFriendRequest", usernameWithId);
                if (finalHasOutgoingPendingFriendRequest)
                    broker.convertAndSendToUser(blocked.getUsername() + "@" + blocked.getId(), "/general/onCancelFriendRequest", usernameWithId);
                broker.convertAndSendToUser(blocked.getUsername() + "@" + blocked.getId(), "/general/onUserBlocked", user);
            }
        });


        return blocked;
    }

    @Transactional(readOnly = true)
    public List<User> getMutualFriends(String usernameWithId, int targetUserId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        User targetUser = findByUsernameWithId("user@" + targetUserId);

        if (user.getId().equals(targetUserId)) {
            throw new UserException("Invalid mutual friends request");
        }

        Set<User> userFriend = user.getFriends();
        Set<User> targetUserFriend = targetUser.getFriends();

        System.out.println("userFriend=" + userFriend);
        System.out.println("targetUserFriend=" + targetUserFriend);

        userFriend.retainAll(targetUserFriend);

        return new ArrayList<>(userFriend);


    }

    @Transactional(readOnly = true)
    public List<ChatRoom> getMutualChatrooms(String usernameWithId, int targetUserId) throws GenericException {
        User user = findByUsernameWithId(usernameWithId);
        User targetUser = findByUsernameWithId("user@" + targetUserId);

        if (user.getId().equals(targetUserId)) {
            throw new UserException("Invalid mutual chatrooms request");
        }

        Set<ChatRoom> userChatRooms = user.getChatRooms();
        Set<ChatRoom> targetUserChatRooms = targetUser.getChatRooms();

        userChatRooms = userChatRooms.stream().filter((room) -> room.getDirect1to1Identifier() == null || room.getDirect1to1Identifier().isEmpty()).collect(Collectors.toSet());
        userChatRooms.retainAll(targetUserChatRooms);

        for (ChatRoom room : userChatRooms) {
            Hibernate.initialize(room.getParticipants());

        }

        return new ArrayList<>(userChatRooms);
    }

    @Transactional
    public int removeBlocked(String usernameWithId, String blockNameWithId) throws UserException {
        User user = findByUsernameWithId(usernameWithId);
        if (user == null) {
            throw new UserException("User not found");
        }
        User blocked = findByUsernameWithId(blockNameWithId);
        if (blocked == null) {
            throw new UserException("User not found");
        }

        if (Objects.equals(user.getId(), blocked.getId())) {
            throw new UserException("Invalid request : user and target user is the same");
        }

        Set<User> blocks = user.getBlocked();
        if (!blocks.contains(blocked)) {
            throw new UserException("User never blocked");
        }

        blocks.remove(blocked);
        blocked.getBlockers().remove(user);

        user.incrementVersion();
        userRepository.save(user);
        blocked.incrementVersion();
        userRepository.save(blocked);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSendToUser(blocked.getUsername() + "@" + blocked.getId(), "/general/onUserUnblocked", user.getId());
            }
        });
        return blocked.getId();
    }
}
