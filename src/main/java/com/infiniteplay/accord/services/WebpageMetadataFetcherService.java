package com.infiniteplay.accord.services;

import com.infiniteplay.accord.utils.GenericException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebpageMetadataFetcherService {
    public Map<String, String> getMetadata(String url) throws GenericException {
        Map<String, String> metadata = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).followRedirects(true).get();
            String siteName = doc.select("meta[property=og:site_name]").attr("content");

            String title = doc.select("meta[property=og:title]").attr("content");
            if(title.isEmpty()) {
                title = doc.title();
            }
            String description = doc.select("meta[property=og:description]").attr("content");
            if(description.isEmpty())
                description = doc.select("meta[name=description]").attr("content");
            String image = doc.select("meta[property=og:image]").attr("content");
            if(image.isEmpty()) {
                image = doc.select("meta[property=twitter:image]").attr("content");
            }
            if(image.isEmpty()) {
                image = doc.select("link[rel=icon]").attr("href");
            }
            String video = doc.select("meta[property=og:video:url]").attr("content");
            String videoWidth = doc.select("meta[property=og:video:width]").attr("content");
            String videoHeight = doc.select("meta[property=og:video:height]").attr("content");
            metadata.put("title", title);
            metadata.put("description", description);
            metadata.put("image", image);
            metadata.put("video", video);
            metadata.put("videoWidth", videoWidth);
            metadata.put("videoHeight", videoHeight);
            metadata.put("siteName",siteName);
        } catch (IOException e) {
            throw new GenericException("Invalid url");
        }
        return metadata;
    }
}
