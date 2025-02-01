package com.infiniteplay.accord.services;


import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.translate.TranslateException;
import com.infiniteplay.accord.utils.GenericException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;

@Service
public class AIService {

    private final Predictor<String, Classifications> nsfwTextDetector;
    private final Predictor<String, Classifications> spamTextDetector;
    private final Predictor<Image, Classifications> nsfwImageDetector;

    public AIService(@Qualifier("nsfwTextDetector") Predictor<String, Classifications> nsfwTextDetector, @Qualifier("spamTextDetector") Predictor<String, Classifications> spamTextDetector, @Qualifier("nsfwImageDetector") Predictor<Image, Classifications> nsfwImageDetector) {
        this.nsfwTextDetector = nsfwTextDetector;
        this.spamTextDetector = spamTextDetector;
        this.nsfwImageDetector = nsfwImageDetector;
    }

    public boolean detectNSFW(String input) throws GenericException {

        if(input.length() < 3) {
            return false;
        }
        try {
            Classifications result = nsfwTextDetector.predict(input);
            double max = Integer.MIN_VALUE;
            String maxClassName = "";

            for(int i=0; i<result.getProbabilities().size(); i++) {
                if(result.getProbabilities().get(i)>max) {
                    max = result.getProbabilities().get(i);
                    maxClassName = result.getClassNames().get(i);
                }
            }

            return maxClassName.equals("nsfw");

        } catch (TranslateException e) {
            throw new GenericException("Error while applying content filter");
        }


    }

    public boolean detectSpam(String input) throws GenericException {

        if(input.length() < 3) {
            return false;
        }
        try {
            Classifications result = spamTextDetector.predict(input);
            double max = Integer.MIN_VALUE;
            String maxClassName = "";

            for(int i=0; i<result.getProbabilities().size(); i++) {
                if(result.getProbabilities().get(i)>max) {
                    max = result.getProbabilities().get(i);
                    maxClassName = result.getClassNames().get(i);
                }
            }

            return maxClassName.equals("spam");

        } catch (TranslateException e) {
            throw new GenericException("Error while applying content filter");
        }


    }

    public boolean detectNSFW(MultipartFile imageFile) throws GenericException {

        if(!isImageFile(imageFile)) {
            return false;
        }
        try {
            Classifications result = nsfwImageDetector.predict(ImageFactory.getInstance().fromInputStream(imageFile.getInputStream()));
            double max = Integer.MIN_VALUE;
            String maxClassName = "";

            for(int i=0; i<result.getProbabilities().size(); i++) {
                if(result.getProbabilities().get(i)>max) {
                    max = result.getProbabilities().get(i);
                    maxClassName = result.getClassNames().get(i);
                }
            }

            return maxClassName.equals("nsfw");

        } catch (TranslateException | IOException e) {
            throw new GenericException("Error while applying content filter");
        }


    }


    public boolean isImageFile(MultipartFile imageFile) {
        try {

            return ImageIO.read(imageFile.getInputStream()) != null;
        } catch (IOException e) {
            return false;
        }
    }

}
