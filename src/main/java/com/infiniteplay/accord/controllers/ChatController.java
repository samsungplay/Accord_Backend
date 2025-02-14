package com.infiniteplay.accord.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteplay.accord.entities.*;
import com.infiniteplay.accord.models.*;
import com.infiniteplay.accord.services.ChatService;
import com.infiniteplay.accord.services.RateLimiterService;
import com.infiniteplay.accord.utils.*;
import com.nimbusds.oauth2.sdk.GeneralException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @GetMapping("/message/scheduled/{chatRoomId}")
    public List<ChatRecord> getScheduledMessages(Authentication authentication, @PathVariable String chatRoomId) {
        return chatService.getScheduledChatRecords(authentication.getName(), chatRoomId);
    }

    @DeleteMapping("/message/scheduled/{chatRecordId}")
    public ResponseEntity<Void> unscheduleMessage(Authentication authentication, @PathVariable int chatRecordId) {
        chatService.unscheduleMessage(authentication.getName(), chatRecordId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/message/scheduled/{chatRecordId}")
    public ResponseEntity<Void> rescheduleMessage(Authentication authentication, @PathVariable int chatRecordId,
                                                  @RequestBody Map<String, String> payload
    ) {
        try {
            chatService.rescheduleMessage(authentication.getName(), chatRecordId, Long.parseLong(payload.get("scheduledTime")));
        } catch (NumberFormatException e) {
            throw new GenericException("Invalid reschedule request");
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/message/type/{chatRoomId}")
    public void dispatchTypingEvent(Authentication authentication, @PathVariable int chatRoomId,
                                    @RequestBody List<String> participants) {
        chatService.dispatchTypingEvent(authentication.getName(), chatRoomId, participants);
    }

    @PostMapping("/message/search/{chatRoomId}")
    public List<ChatRecord> searchMessage(Authentication authentication, @PathVariable String chatRoomId,
                                          @RequestBody ChatRecordSearchParameters searchParameters,
                                          @RequestParam(name = "nsfw", defaultValue = "ANY") String nsfwFlag,
                                          @RequestParam(name = "spam", defaultValue = "ANY") String spamFlag) {

        return chatService.searchChatRecord(authentication.getName(), chatRoomId, searchParameters.getCursorId(),
                searchParameters.getPrevious(), searchParameters.getOrder(), searchParameters.getContent(),
                searchParameters.getTags(), searchParameters.getLocalTimezone(), ContentFilterFlag.valueOf(nsfwFlag),
                ContentFilterFlag.valueOf(spamFlag), chatRoomId.equals("-1"));
    }


    @GetMapping("/message/verify/{chatRoomId}/{chatRecordId}")
    public ResponseEntity<Void> verifyMessageExistsById(Authentication authentication,
                                                        @PathVariable String chatRoomId,
                                                        @PathVariable String chatRecordId,
                                                        @RequestParam(name = "nsfw", defaultValue = "ANY") String nsfwFlag,
                                                        @RequestParam(name = "spam", defaultValue = "ANY") String spamFlag

    ) {

        if (chatRoomId.equals(("-1"))) {
            chatService.verifySpamCheckRecordExistsById(authentication.getName(), chatRecordId,
                    ContentFilterFlag.valueOf(nsfwFlag));
            return ResponseEntity.ok().build();
        }
        chatService.verifyCheckRecordExistsById(authentication.getName(), chatRoomId, chatRecordId,
                ContentFilterFlag.valueOf(nsfwFlag),
                ContentFilterFlag.valueOf(spamFlag));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/message/{chatroomId}/{chatRecordId}/embeds")
    public ResponseEntity<Void> hideMessageEmbed(Authentication authentication, @PathVariable String chatroomId, @PathVariable String chatRecordId) {
        chatService.hideMessageEmbed(authentication.getName(), chatroomId, chatRecordId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/message/{chatroomId}/{chatRecordId}")
    public ResponseEntity<Void> deleteMessage(Authentication authentication,
                                              @PathVariable("chatroomId") String chatroomId, @PathVariable("chatRecordId") String chatRecordId) {


        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        chatService.deleteMessage(authentication.getName(), chatroomId, chatRecordId);

        return ResponseEntity.ok().build();

    }

    @PutMapping("/message/{chatroomId}/{chatRecordId}")
    public ResponseEntity<Void> editMessage(Authentication authentication, @RequestBody Map<String, String> chatMessage,
                                            @PathVariable("chatroomId") String chatroomId, @PathVariable("chatRecordId") String chatRecordId) {


        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        chatService.editMessage(authentication.getName(), chatroomId, chatRecordId, new ChatMessage(chatMessage.get("message"), null, null, null));

        return ResponseEntity.ok().build();

    }


    @DeleteMapping("/message/attachments/{chatroomId}/{chatrecordId}/{attachmentId}")
    public ResponseEntity<String> deleteAttachment(Authentication authentication, @PathVariable String chatroomId,
                                                   @PathVariable String chatrecordId, @PathVariable String attachmentId) {
        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String attachmentsCode = chatService.deleteAttachment(authentication.getName(), chatroomId, chatrecordId, UUID.fromString(attachmentId));
        return ResponseEntity.ok(attachmentsCode);
    }

    @PostMapping("/message/attachments/{chatroomId}")
    public ResponseEntity<ChatRecord> dispatchMessageWithAttachments(Authentication authentication,
                                                                     @RequestParam("message") String message,
                                                                     @RequestParam("replyTarget") String replyTarget,
                                                                     @RequestParam("replyTargetSenderId") String replyTargetSenderId,
                                                                     @RequestParam("replyTargetMessage") String replyTargetMessage,
                                                                     @RequestParam("attachmentsMetadata") String attachmentsMetadata,
                                                                     @RequestParam("attachments") MultipartFile[] attachments,
                                                                     @RequestParam(value = "scheduledTime", required = false) Long scheduledTime,
                                                                     @PathVariable("chatroomId") String chatroomId) {
        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        ChatRecord recorded = chatService.sendMessage(authentication.getName(), chatroomId, new ChatMessage(message, replyTarget, replyTargetSenderId,
                replyTargetMessage), attachments, attachmentsMetadata, scheduledTime);


        return ResponseEntity.ok(recorded);
    }

    @PostMapping("/message/poll/{chatroomId}")
    public ResponseEntity<ChatRecord> dispatchPollMessage(Authentication authentication, @RequestBody PollMessage pollMessage, @PathVariable("chatroomId") String chatroomId) {


        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        ChatRecord record = chatService.sendPoll(authentication.getName(), chatroomId, pollMessage);

        return ResponseEntity.ok(record);
    }

    @PostMapping("/message/poll/{pollId}/vote/{chatroomId}")
    public ResponseEntity<Void> votePollMessage(Authentication authentication, @RequestBody Map<String, List<Integer>> payload, @PathVariable String chatroomId,
                                                @PathVariable String pollId) {
        chatService.votePoll(authentication.getName(), chatroomId, pollId, payload.get("answerIndex"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/message/poll/{pollId}/vote/{chatroomId}")
    public ResponseEntity<Void> unVotePollMessage(Authentication authentication, @PathVariable String chatroomId,
                                                  @PathVariable String pollId) {
        chatService.unVotePoll(authentication.getName(), chatroomId, pollId);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/message/{chatroomId}")
    public ResponseEntity<ChatRecord> dispatchMessage(Authentication authentication, @RequestBody Map<String, String> chatMessage, @PathVariable("chatroomId") String chatroomId) {


        if (!rateLimiterService.limitRate("dispatchMessage", authentication.getName(), 1, Duration.ofMillis(500))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }


        ChatRecord recorded = chatService.sendMessage(authentication.getName(), chatroomId, new ChatMessage(chatMessage.get("message"), chatMessage.get("replyTarget"), chatMessage.get("replyTargetSenderId"),
                chatMessage.get("replyTargetMessage")), null, null, chatMessage.get("scheduledTime") == null ? null : Long.parseLong(chatMessage.get("scheduledTime")));

        return ResponseEntity.ok(recorded);
    }

    @GetMapping("/message/{chatroomId}/pinned")
    public List<ChatRecord> getPinnedMessages(Authentication authentication, @PathVariable String chatroomId,
                                              @RequestParam(name = "nsfw", defaultValue = "ANY") String nsfwFlag,
                                              @RequestParam(name = "spam", defaultValue = "ANY") String spamFlag) {
        return chatService.getPinnedChatRecords(authentication.getName(), chatroomId,
                ContentFilterFlag.valueOf(nsfwFlag),
                ContentFilterFlag.valueOf(spamFlag));
    }

    @PostMapping("/message/{chatroomId}/pinned/{chatRecordId}")
    public ResponseEntity<Void> pinMessage(Authentication authentication, @PathVariable String chatroomId, @PathVariable String chatRecordId) {
        chatService.pinMessage(authentication.getName(), chatroomId, chatRecordId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/message/{chatroomId}/pinned/{chatRecordId}")
    public ResponseEntity<Void> unpinMessage(Authentication authentication, @PathVariable String chatroomId, @PathVariable String chatRecordId) {
        chatService.unpinMessage(authentication.getName(), chatroomId, chatRecordId);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/message/{chatroomId}")
    public List<ChatRecord> getMessages(Authentication authentication, @PathVariable String chatroomId,
                                        @RequestParam("pageKey") Integer pageKey,
                                        @RequestParam(name = "nsfw", defaultValue = "ANY") String nsfwFlag,
                                        @RequestParam(name = "spam", defaultValue = "ANY") String spamFlag

    ) {

        if (chatroomId.equals("-1")) {
            return chatService.getSpamChatRecords(authentication.getName(), pageKey, pageKey < 0, ContentFilterFlag.valueOf(nsfwFlag));
        }

        return chatService.getChatRecords(authentication.getName(), chatroomId, pageKey, pageKey < 0,
                ContentFilterFlag.valueOf(nsfwFlag), ContentFilterFlag.valueOf(spamFlag));
    }


    @PostMapping("/reaction/{chatroomId}/{chatRecordId}")
    public ResponseEntity<ChatReaction> reactMessage(Authentication authentication, @PathVariable String chatroomId,
                                                     @PathVariable String chatRecordId, @RequestBody Map<String, String> reactionPayload) {
        ChatReaction chatReaction = chatService.reactMessage(authentication.getName(), chatroomId, chatRecordId, reactionPayload.get("reaction"));
        return ResponseEntity.ok(chatReaction);
    }

    @PostMapping("/unreaction/{chatroomId}/{chatRecordId}")
    public ResponseEntity<Integer> unReactMessage(Authentication authentication, @PathVariable String chatroomId,
                                                  @PathVariable String chatRecordId, @RequestBody Map<String, String> reactionPayload) {
        int id = chatService.unReactMessage(authentication.getName(), chatroomId, chatRecordId, reactionPayload.get("reaction"));
        return ResponseEntity.ok(id);
    }

    @ExceptionHandler(value = {
            GenericException.class,
            NullPointerException.class,
            JsonProcessingException.class,
    })
    public ResponseEntity<String> handleException(Exception ex) {
        if (ex instanceof InternalException) {
            return ResponseEntity.internalServerError().body(ex.getMessage());
        }
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
