package com.infiniteplay.accord.controllers;


import com.infiniteplay.accord.entities.Background;
import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.UserSettingsDTO;
import com.infiniteplay.accord.models.UserStatus;
import com.infiniteplay.accord.services.AuthenticationService;
import com.infiniteplay.accord.services.PushNotificationService;
import com.infiniteplay.accord.services.UserService;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.UserException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private PushNotificationService pushNotificationService;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {

        authenticationService.logout(response);

        pushNotificationService.unsubscribe(authentication.getName());

        return ResponseEntity.ok().build();
    }


    @PostMapping("/muteChatroom/{chatRoomId}")
    public ResponseEntity<List<Integer>> muteChatRoom(Authentication authentication, @PathVariable int chatRoomId) {
        List<Integer> mutedChatRoomIds = userService.muteChatRoomNotification(authentication.getName(), chatRoomId);
        return ResponseEntity.ok(mutedChatRoomIds);
    }

    @PostMapping("/unmuteChatroom/{chatRoomId}")
    public ResponseEntity<List<Integer>> unmuteChatRoom(Authentication authentication, @PathVariable int chatRoomId) {
        List<Integer> mutedChatRoomIds = userService.unmuteChatRoomNotification(authentication.getName(), chatRoomId);
        return ResponseEntity.ok(mutedChatRoomIds);
    }

    @PostMapping("/status")
    public ResponseEntity<Void> updateUserStatus(Authentication authentication, @RequestBody String status) {
        userService.updateStatus(authentication.getName(), UserStatus.valueOf(status.substring(0, status.length() - 1)));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/settings")
    public ResponseEntity<Void> updateUserSettings(Authentication authentication, @RequestBody UserSettingsDTO userSettingsDTO) {
        userService.updateUserSettings(authentication.getName(), userSettingsDTO);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/search")
    public ResponseEntity<List<User>> searchUser(@RequestBody Map<String, String> query) {
        return ResponseEntity.ok(userService.searchUser(query.get("query")));
    }

    @GetMapping("/youmayknow")
    public ResponseEntity<List<User>> getYouMayKnow(Authentication authentication) {
        return ResponseEntity.ok(userService.getYouMayKnow(authentication.getName(), 5));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsDTO> getUserSettings(Authentication authentication) {
        return ResponseEntity.ok(userService.getUserSettings(authentication.getName()));
    }

    @PostMapping("/enableScreenShare/{chatRoomId}")
    public ResponseEntity<Void> updateScreenShareEnable(Authentication authentication, @RequestBody Map<String, String> payload,
                                                        @PathVariable String chatRoomId) {
        userService.setEnableScreenShare(authentication.getName(), payload.get("enabled"), chatRoomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/enableVideo/{chatRoomId}")
    public ResponseEntity<Void> updateVideoEnable(Authentication authentication, @RequestBody Map<String, Boolean> payload,
                                                  @PathVariable String chatRoomId) {
        userService.setEnableVideo(authentication.getName(), payload.get("enabled"), chatRoomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deafen")
    public ResponseEntity<Void> updateDeafen(Authentication authentication, @RequestBody Map<String, Boolean> payload) {
        userService.setDeafened(authentication.getName(), payload.get("deafened"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mute")
    public ResponseEntity<Void> updateMute(Authentication authentication, @RequestBody Map<String, Boolean> payload) {
        userService.setMute(authentication.getName(), payload.get("muted"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @RequestBody Map<String, String> payload) {
        userService.changePassword(authentication.getName(), payload.get("oldPassword"), payload.get("newPassword"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/profileNoImage")
    public ResponseEntity<User> updateProfileNoImage(Authentication authentication,
                                                     @RequestParam("editProfileUsername") String username,
                                                     @RequestParam("editProfileNickname") String nickname,
                                                     @RequestParam("editProfileEmail") String email,
                                                     @RequestParam("editProfileStatusMessage") String statusMessage,
                                                     @RequestParam("editProfileAboutMe") String aboutMe,
                                                     @RequestParam("editProfileColor") String profileColor) {
        User user = userService.updateUserProfile(authentication.getName(), username, nickname, email, statusMessage, aboutMe, profileColor);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/profile")
    public ResponseEntity<User> updateProfile(Authentication authentication,
                                              @RequestParam("editProfileUsername") String username,
                                              @RequestParam("editProfileNickname") String nickname,
                                              @RequestParam("editProfileEmail") String email,
                                              @RequestParam("editProfileStatusMessage") String statusMessage,
                                              @RequestParam("editProfileAboutMe") String aboutMe,
                                              @RequestParam("editProfileImage") MultipartFile profileImage
    ) {

        User user = userService.updateUserProfile(authentication.getName(), username, nickname, email, statusMessage, aboutMe, profileImage);
        return ResponseEntity.ok(user);
    }


    @GetMapping
    public User getCurrentUser(Authentication authentication) {
        return userService.findByUsernameWithId(authentication.getName());
    }

    @GetMapping("/friends")
    public Set<User> getFriends(Authentication authentication) {
        return userService.findAllFriendsOf(authentication.getName());
    }

    @GetMapping("/backgrounds")
    public List<Background> getBackgrounds(Authentication authentication) {
        return userService.findAllBackgroundsOf(authentication.getName());
    }

    @PostMapping("/backgrounds")
    public ResponseEntity<Void> addBackground(Authentication authentication, @RequestParam("name") String name,
                                              @RequestParam("file") MultipartFile file) {
        userService.addBackground(authentication.getName(), name, file);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/aboutme/{targetUserId}")
    public ResponseEntity<String> getAboutMe(Authentication authentication, @PathVariable int targetUserId) {

        return ResponseEntity.ok(userService.getAboutMe(authentication.getName(), targetUserId));

    }

    @GetMapping("/aboutme")
    public ResponseEntity<String> getAboutMe(Authentication authentication) {

        return ResponseEntity.ok(userService.getAboutMe(authentication.getName()));

    }

    @DeleteMapping("/backgrounds/{name}")
    public ResponseEntity<Void> addBackground(Authentication authentication, @PathVariable String name) {
        userService.deleteBackground(authentication.getName(), name);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/mutual/friends/{targetUserId}")
    public List<User> getMutualFriends(Authentication authentication, @PathVariable int targetUserId) {
        return userService.getMutualFriends(authentication.getName(), targetUserId);
    }

    @GetMapping("/mutual/chatrooms/{targetUserId}")
    public List<ChatRoom> getMutualChatrooms(Authentication authentication, @PathVariable int targetUserId) {
        return userService.getMutualChatrooms(authentication.getName(), targetUserId);
    }


    @GetMapping("/blockeds")
    public Set<User> getBlockeds(Authentication authentication) {
        return userService.findAllBlockedOf(authentication.getName());
    }

    @GetMapping("/blockers")
    public Set<User> getBlockers(Authentication authentication) {
        return userService.findAllBlockersOf(authentication.getName());
    }

    @GetMapping("/friends/pendings/outgoing")
    public Set<User> getOutgoingPendings(Authentication authentication) {
        return userService.findOutgoingPendingsOf(authentication.getName());
    }

    @GetMapping("/friends/pendings/incoming")
    public Set<User> getIncomingPendings(Authentication authentication) {
        return userService.findIncomingPendingsOf(authentication.getName());
    }

    @DeleteMapping("/friends/pendings/outgoing/{usernameWithId}")
    public ResponseEntity<Void> cancelFriendRequest(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        userService.cancelFriendRequest(authentication.getName(), usernameWithId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/friends/{usernameWithId}")
    public ResponseEntity<User> requestFriend(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        User user = userService.sendFriendRequest(authentication.getName(), usernameWithId);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/friends/accept/{usernameWithId}")
    public ResponseEntity<User> acceptFriend(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        User newFriend = userService.acceptFriendRequest(authentication.getName(), usernameWithId);
        return ResponseEntity.ok(newFriend);
    }

    @PostMapping("/friends/reject/{usernameWithId}")
    public ResponseEntity<Void> rejectFriend(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        userService.rejectFriendRequest(authentication.getName(), usernameWithId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/friends/{usernameWithId}")
    public ResponseEntity<Void> removeFriend(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        userService.removeFriend(authentication.getName(), usernameWithId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/block/{usernameWithId}")
    public ResponseEntity<User> addBlock(@PathVariable("usernameWithId") String usernameWithId, Authentication authentication) {
        User blocked = userService.addBlocked(authentication.getName(), usernameWithId);
        return ResponseEntity.ok(blocked);
    }

    @DeleteMapping("/block/{usernameWIthId}")
    public ResponseEntity<Integer> removeBlock(@PathVariable("usernameWIthId") String usernameWithId, Authentication authentication) {
        int id = userService.removeBlocked(authentication.getName(), usernameWithId);
        return ResponseEntity.ok(id);
    }

    @ExceptionHandler(value = {
            UserException.class,
            GenericException.class
    })
    public ResponseEntity<String> handleException(Exception ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(value = {
            StaleObjectStateException.class,
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<String> handleOptimisticLockingException(Exception exc) {
        return ResponseEntity.status(409).body("Your action couldn't be completed because the data has changed. Please try again.");
    }

}
