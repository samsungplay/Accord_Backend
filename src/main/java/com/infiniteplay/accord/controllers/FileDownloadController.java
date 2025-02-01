package com.infiniteplay.accord.controllers;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;

@RestController
@RequestMapping("/download")
public class FileDownloadController {

    @Value("${staticfile.path}")
    private String STATIC_FILE_PATH;
    @Autowired
    ResourceLoader resourceLoader;

    @GetMapping("/attachments/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
            Resource resource = resourceLoader.getResource(STATIC_FILE_PATH + filename);
            if(!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            String fullFileName = resource.getFilename();
            if(fullFileName == null) {
                return ResponseEntity.notFound().build();
            }
            String displayFileName = fullFileName.substring(fullFileName.indexOf('_') + 1);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + displayFileName + "\"")
                    .body(resource);
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
