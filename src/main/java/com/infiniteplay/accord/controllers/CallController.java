package com.infiniteplay.accord.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.infiniteplay.accord.entities.Call;
import com.infiniteplay.accord.models.ICECandidate;
import com.infiniteplay.accord.models.MusicSyncEvent;
import com.infiniteplay.accord.services.CallService;
import com.infiniteplay.accord.services.JanusService;
import com.infiniteplay.accord.services.UserService;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.ICECandidates;
import com.infiniteplay.accord.utils.InternalException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/call")
public class CallController {

    private final CallService callService;
    private final JanusService janusService;
    private final UserService userService;

    public CallController(CallService callService, JanusService janusService, UserService userService) {
        this.callService = callService;
        this.janusService = janusService;
        this.userService = userService;
    }


    @PostMapping("/pingSession")
    public ResponseEntity<Void> pingSession(Authentication authentication) {
        janusService.sendKeepAlive(userService.extractId(authentication.getName()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/streamPreview")
    public ResponseEntity<Void> uploadStreamPreview(Authentication authentication,
                                                    @RequestParam("chatRoomId") String chatRoomId,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestParam("type") String type) {
        callService.uploadStreamPreview(authentication.getName(), chatRoomId, file, type);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/expressEmoji")
    public ResponseEntity<Void> expressEmoji(Authentication authentication, @RequestBody Map<String, String> payload) {
        callService.expressEmoji(authentication.getName(), payload.get("shortCodes"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/musicSync")
    public ResponseEntity<Void> syncMusic(Authentication authentication, @RequestBody MusicSyncEvent syncEvent) {
        callService.synchronizeMusic(authentication.getName(), syncEvent);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sound")
    public ResponseEntity<Void> shareSound(Authentication authentication,
                                           @RequestBody Map<String, String> payload) {
        callService.shareSound(authentication.getName(), payload.get("name"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prepareStart/{chatRoomId}")
    public ResponseEntity<String> prepareStartCall(Authentication authentication, @PathVariable String chatRoomId,
                                                   @RequestBody Map<String, String> sdpOffer) {
        String sdpAnswer = callService.prepareStartCall(authentication.getName(), chatRoomId, sdpOffer.get("sdpOffer"));
        return ResponseEntity.ok(sdpAnswer);
    }

    @PostMapping("/prepareSubscribe/{chatRoomId}")
    public ResponseEntity<String> prepareSubscription(Authentication authentication, @PathVariable String chatRoomId) {
        String sdpOffer = callService.prepareSubscription(authentication.getName(), chatRoomId);
        return ResponseEntity.ok(sdpOffer);
    }


    @PostMapping("/finalizeSubscribe/{chatRoomId}")
    public ResponseEntity<ICECandidates> finalizeSubscription(Authentication authentication, @PathVariable String chatRoomId,
                                                              @RequestBody Map<String, Object> payload) {
        ICECandidates ices = callService.finalizeSubscription(authentication.getName(), chatRoomId, (String) payload.get("sdpAnswer"));
        return ResponseEntity.ok(ices);
    }


    @PostMapping("/prepareJoin/{chatRoomId}")
    public ResponseEntity<String> prepareJoinCall(Authentication authentication, @PathVariable String chatRoomId,
                                                  @RequestBody Map<String, String> sdpOffer) {
        String sdpAnswer = callService.prepareJoinCall(authentication.getName(), chatRoomId, sdpOffer.get("sdpOffer"));
        return ResponseEntity.ok(sdpAnswer);
    }

    @DeleteMapping("/abort/{chatRoomId}")
    public ResponseEntity<Void> abortCall(Authentication authentication, @PathVariable String chatRoomId) {
        callService.abortCall(authentication.getName(),chatRoomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/start/{chatRoomId}")
    public ResponseEntity<Call> startCall(Authentication authentication, @PathVariable String chatRoomId,
                                          @RequestBody ICECandidates iceCandidates) {
        Call call = callService.startCall(authentication.getName(), chatRoomId, iceCandidates);
        return ResponseEntity.ok(call);
    }

    @PostMapping("/join/{chatRoomId}")
    public ResponseEntity<Void> joinCall(Authentication authentication, @PathVariable String chatRoomId, @RequestBody ICECandidates iceCandidates) {
        callService.joinCall(authentication.getName(), chatRoomId, iceCandidates);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject/{chatRoomId}")
    public ResponseEntity<Void> rejectCall(Authentication authentication, @PathVariable String chatRoomId) {
        callService.rejectCall(authentication.getName(), chatRoomId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/leave")
    public ResponseEntity<Void> leaveCall(Authentication authentication) {
        callService.leaveCall(authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/kick/{chatRoomId}/{usernameWithId}")
    public ResponseEntity<Void> kickCall(Authentication authentication, @PathVariable String chatRoomId, @PathVariable String usernameWithId) {
        callService.kickUserFromCall(authentication.getName(), chatRoomId, usernameWithId);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(value = {
            GenericException.class,
    })
    public ResponseEntity<String> handleException(Exception ex) {
        if (ex instanceof InternalException) {
            return ResponseEntity.internalServerError().body(ex.getMessage());
        }
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(value={
            StaleObjectStateException.class,
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<String> handleOptimisticLockingException(Exception exc) {
        return ResponseEntity.status(409).body("Your action couldn't be completed because the data has changed. Please try again.");
    }
}
