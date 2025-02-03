package com.infiniteplay.accord.controllers;

import com.infiniteplay.accord.models.PushSubscriptionDTO;
import com.infiniteplay.accord.services.PushNotificationService;
import com.infiniteplay.accord.utils.GenericException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pushNotification")
public class PushNotificationController {
    private final PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }



    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(Authentication authentication, @RequestBody PushSubscriptionDTO pushSubscriptionDTO,
                                          HttpServletRequest request) {
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if(cookies != null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals("accord_refresh_token")) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        pushNotificationService.subscribe(authentication.getName(), pushSubscriptionDTO, refreshToken);
        return ResponseEntity.ok().build();
    }
    @ExceptionHandler(value = {
            GenericException.class
    })
    public ResponseEntity<String> handleException(Exception ex) {
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
