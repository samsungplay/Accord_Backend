package com.infiniteplay.accord.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.infiniteplay.accord.services.RateLimiterService;
import com.infiniteplay.accord.services.WebpageMetadataFetcherService;
import com.infiniteplay.accord.utils.GenericException;
import com.infiniteplay.accord.utils.InternalException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fetchmetadata")
public class WebpageMetadataFetcherController {

    private final WebpageMetadataFetcherService webpageMetadataFetcherService;
    private final RateLimiterService rateLimiterService;

    public WebpageMetadataFetcherController(WebpageMetadataFetcherService webpageMetadataFetcherService, RateLimiterService rateLimiterService) {
        this.webpageMetadataFetcherService = webpageMetadataFetcherService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("")
    public ResponseEntity<Map<String, String>> queryWebpageMetadata(Authentication authentication, @RequestBody Map<String, String> payload) {



        Map<String, String> metadata = webpageMetadataFetcherService.getMetadata(payload.get("url"));


        return ResponseEntity.ok(metadata);
    }

    @ExceptionHandler(value={
            GenericException.class
    })
    public ResponseEntity<String> handleException(Exception ex) {
        if(ex instanceof InternalException) {
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
