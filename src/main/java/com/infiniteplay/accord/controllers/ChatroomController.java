package com.infiniteplay.accord.controllers;


import com.infiniteplay.accord.entities.ChatRoom;
import com.infiniteplay.accord.entities.ChatRoomRoleSettings;
import com.infiniteplay.accord.entities.Sound;
import com.infiniteplay.accord.models.ChatRoomDetails;
import com.infiniteplay.accord.models.DirectMessageCreateRequest;
import com.infiniteplay.accord.models.SoundData;
import com.infiniteplay.accord.services.ChatRoomService;
import com.infiniteplay.accord.utils.ChatRoomException;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.UserException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/chatrooms")
@Slf4j
public class ChatroomController {

    @Autowired
    private ChatRoomService chatRoomService;

    @PostMapping("/openPublic/{id}")
    public ResponseEntity<Void> openPublicRoom(Authentication authentication, @PathVariable("id") int chatRoomId, @RequestParam("open") boolean open) {

        chatRoomService.setPublic(authentication.getName(), chatRoomId, open);
        return ResponseEntity.ok().build();

    }

    @PostMapping("/publicAccess/{id}")
    public ResponseEntity<Void> joinPublicRoom(Authentication authentication, @PathVariable("id") int chatRoomId) {

        chatRoomService.joinPublicRoom(authentication.getName(), chatRoomId);
        return ResponseEntity.ok().build();

    }

    @PostMapping("/search")
    public ResponseEntity<List<ChatRoom>> searchPublicChatRooms(@RequestBody Map<String, String> query) {

        return ResponseEntity.ok(chatRoomService.searchChatroom(query.get("query")));

    }


    @GetMapping("/invitationData/{shortCode}")
    public ResponseEntity<ChatRoom> getInvitationData(@PathVariable String shortCode) {
        ChatRoom chatRoom = chatRoomService.getChatRoomByInvitationCode(shortCode);

        return chatRoom == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/invitation/{id}")
    public ResponseEntity<String> getInvitationCode(Authentication authentication, @PathVariable("id") int chatRoomId,
                                                    @RequestParam("permanent") boolean permanent) {
        String code = chatRoomService.getInvitationCode(authentication.getName(), chatRoomId, permanent);
        if (code.equals("empty")) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(code);


    }


    @PostMapping("/invitation/{shortCode}")
    public ResponseEntity<Void> acceptInvitationCode(Authentication authentication, @PathVariable("shortCode") String shortCode
    ) {
        chatRoomService.acceptInvitationCode(authentication.getName(), shortCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/invitation/{id}")
    public ResponseEntity<String> invalidateAllInvitationCodes(Authentication authentication, @PathVariable("id") String chatRoomId
    ) {
        chatRoomService.invalidateAllInvitationCodes(authentication.getName(), chatRoomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invitationGenerate/{id}")
    public ResponseEntity<String> generateInvitationCode(Authentication authentication, @PathVariable("id") String chatRoomId,
                                                         @RequestParam("permanent") boolean permanent) {
        return ResponseEntity.ok(chatRoomService.generateInvitationCode(authentication.getName(), chatRoomId, permanent));
    }

    @PostMapping("/roles/moderator/{id}/{targetUserId}")
    public ResponseEntity<Integer[]> grantModerator(Authentication authentication, @PathVariable("id") String chatRoomId, @PathVariable int targetUserId) {
        Integer[] modIds = chatRoomService.grantModeratorRole(authentication.getName(), chatRoomId, targetUserId);
        return ResponseEntity.ok(modIds);
    }

    @DeleteMapping("/roles/moderator/{id}/{targetUserId}")
    public ResponseEntity<Integer[]> revokeModerator(Authentication authentication, @PathVariable("id") String chatRoomId, @PathVariable int targetUserId) {
        Integer[] modIds = chatRoomService.revokeModeratorRole(authentication.getName(), chatRoomId, targetUserId);
        return ResponseEntity.ok(modIds);
    }


    @GetMapping("/roleSettings/{id}")
    public ResponseEntity<ChatRoomRoleSettings> getRoleSettings(@PathVariable("id") String chatRoomId) {
        return ResponseEntity.ok(chatRoomService.getRoleSettings(chatRoomId));
    }

    @PostMapping("/roleSettings")
    public ResponseEntity<Void> updateRoleSettings(Authentication authentication, @RequestBody ChatRoomRoleSettings settings) {
        chatRoomService.updateRoleSettings(authentication.getName(), settings);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/directmessaging/{id}")
    public ResponseEntity<ChatRoom> updateDirectMessagingRoom(@RequestPart("chatRoomDetails") ChatRoomDetails chatRoomDetails, Authentication authentication,
                                                              @RequestPart(value = "roomImage", required = false) MultipartFile roomImage) {
        ChatRoom chatRoom = chatRoomService.updateChatRoom(authentication.getName(), chatRoomDetails, roomImage
        );
        return ResponseEntity.ok(chatRoom);
    }

    @PostMapping("/directmessaging")
    public ResponseEntity<ChatRoom> createDirectMessagingRoom(@RequestBody DirectMessageCreateRequest request, Authentication authentication) {
        ChatRoom chatRoom = chatRoomService.createDirectMessagingChatroom(authentication.getName(), request.getFriendNames(), request.getChatRoomName(), request.isDm());
        return ResponseEntity.status(HttpStatus.CREATED).body(chatRoom);
    }

    @GetMapping("/directmessaging")
    public Set<ChatRoom> getDirectMessagingRooms(Authentication authentication) {
        return chatRoomService.getAllChatRooms(authentication.getName());
    }

    @GetMapping("/directmessaging/{id}")
    public ChatRoom getDirectMessagingChatroom(@PathVariable String id, Authentication authentication) {

        return chatRoomService.getChatRoom(authentication.getName(), id);
    }

    @DeleteMapping("/directmessaging/all/{id}")
    public ResponseEntity<Integer> deleteDirectMessagingChatRoom(@PathVariable String id, Authentication authentication) {
        int deleted = chatRoomService.deleteDirectMessagingChatRoom(authentication.getName(), id);
        return ResponseEntity.ok(deleted);
    }

    @PostMapping("/directmessaging/readAllMessages/{chatroomId}")
    public ResponseEntity<Long> readAllMessages(Authentication authentication, @PathVariable String chatroomId) {
        long serverReadTime = chatRoomService.markAllMessagesRead(authentication.getName(), chatroomId);
        return ResponseEntity.ok(serverReadTime);
    }

    @GetMapping("/directmessaging/notifications/{chatRoomId}")
    public ResponseEntity<List<Integer>> getNotificationData(Authentication authentication, @PathVariable String chatRoomId) {
        return ResponseEntity.ok(chatRoomService.getNotificationData(authentication.getName(), chatRoomId));
    }

    @PostMapping("/directmessaging/{id}/ownership")
    public ResponseEntity<Void> transferOwnership(@PathVariable String id, Authentication authentication, @RequestBody Map<String, String> payload) {
        chatRoomService.transferOwnership(authentication.getName(), id, payload.get("target"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/directmessaging/{id}")
    public ResponseEntity<Void> leaveDirectMessagingChatroom(@PathVariable String id, Authentication authentication) {

        chatRoomService.leaveDirectMessagingChatRoom(authentication.getName(), id);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/directmessaging/{id}/music/reorder")
    public ResponseEntity<Void> reorderMusic(@PathVariable String id, Authentication authentication,
                                             @RequestBody Map<String, Integer> orderIndices) {
        chatRoomService.reorderMusic(authentication.getName(), id, orderIndices);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/directmessaging/{id}/background")
    public ResponseEntity<Void> addBackground(@PathVariable String id, Authentication authentication,
                                              @RequestParam("name") String name,
                                              @RequestParam("file") MultipartFile file) {
        chatRoomService.addBackground(authentication.getName(), id, name, file);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/directmessaging/{id}/background/{name}")
    public ResponseEntity<Void> deleteBackground(@PathVariable String id, @PathVariable String name, Authentication authentication) {
        chatRoomService.deleteBackground(authentication.getName(), id, name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/directmessaging/{id}/sound")
    public ResponseEntity<Void> addSound(@PathVariable String id, Authentication authentication,
                                         @RequestParam("type") String type,
                                         @RequestParam("name") String name,
                                         @RequestParam("icon") String icon,
                                         @RequestParam("duration") long duration,
                                         @RequestParam("file") MultipartFile file) {
        chatRoomService.addSound(authentication.getName(), id, new SoundData(type, name, icon, file, duration));

        return ResponseEntity.ok().build();
    }


    @DeleteMapping("/directmessaging/{id}/sound/{name}")
    public ResponseEntity<Void> deleteSound(@PathVariable String id, @PathVariable String name, Authentication authentication) {
        chatRoomService.deleteSound(authentication.getName(), id, name, "sound");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/directmessaging/{id}/music/{name}")
    public ResponseEntity<Void> deleteMusic(@PathVariable String id, @PathVariable String name, Authentication authentication) {
        chatRoomService.deleteSound(authentication.getName(), id, name, "music");
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<String> handleUserException(Exception ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(value = {
            ChatRoomException.class,
            GenericException.class
    })
    public ResponseEntity<String> handleChatRoomException(Exception ex) {
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
