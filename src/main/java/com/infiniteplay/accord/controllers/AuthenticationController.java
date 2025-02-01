package com.infiniteplay.accord.controllers;


import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.models.OauthRegisterDetails;
import com.infiniteplay.accord.models.RegisterDetails;
import com.infiniteplay.accord.security.authentication.JWTHandler;
import com.infiniteplay.accord.services.AuthenticationService;
import com.infiniteplay.accord.utils.RegisterException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/authentication")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JWTHandler jwtHandler;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterDetails registerDetails) {
        authenticationService.register(registerDetails);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/registerGithub")
    public ResponseEntity<Void> registerGithub(@RequestBody OauthRegisterDetails registerDetails) {

        int accountId = jwtHandler.isValidGithubOauthToken(registerDetails.getRegistrationToken());
        if(accountId != -1) {
            authenticationService.registerGithub(registerDetails,accountId);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/authenticate")
    public ResponseEntity<Void> isAuthenticated() {
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDeniedException(final AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(RegisterException.class)
    public ResponseEntity<Map<String,String>> handleRegisterException(RegisterException exc) {

        return ResponseEntity.badRequest().body(exc.toErrorData());
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
