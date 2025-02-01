package com.infiniteplay.accord.configs;


import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import com.infiniteplay.accord.ai.ImageClassificationTranslator;
import com.infiniteplay.accord.ai.TextClassificationTranslator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
public class AIModelConfig {

    @Value("${aimodels.path}")
    String aiModelsPath;

    @Bean
    public Predictor<String, Classifications> nsfwTextDetector() throws ModelNotFoundException, MalformedModelException, IOException {
        List<String> classLabels = Arrays.asList("safe", "nsfw");
        String modelPath = aiModelsPath + "/nsfw_text_detector/model.onnx";
        String tokenizerPath = aiModelsPath + "/nsfw_text_detector";

        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelPath(Paths.get(modelPath))
                .optTranslator(new TextClassificationTranslator(tokenizerPath, classLabels))
                .optEngine("OnnxRuntime")
                .build();
        ZooModel<String, Classifications> model = criteria.loadModel();
        Predictor<String, Classifications> predictor = model.newPredictor();

        return predictor;
    }

    @Bean
    public Predictor<String, Classifications> spamTextDetector() throws ModelNotFoundException, MalformedModelException, IOException {
        List<String> classLabels = Arrays.asList("ham", "spam");
        String modelPath = aiModelsPath + "/spam_text_detector/model.onnx";
        String tokenizerPath = aiModelsPath + "/spam_text_detector";

        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelPath(Paths.get(modelPath))
                .optTranslator(new TextClassificationTranslator(tokenizerPath, classLabels))
                .optEngine("OnnxRuntime")
                .build();
        ZooModel<String, Classifications> model = criteria.loadModel();
        Predictor<String, Classifications> predictor = model.newPredictor();

        return predictor;
    }

    @Bean
    public Predictor<Image, Classifications> nsfwImageDetector() throws ModelNotFoundException, MalformedModelException, IOException {
        List<String> classLabels = Arrays.asList("normal", "nsfw");
        String modelPath = aiModelsPath + "/nsfw_image_detector/model.onnx";

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optModelPath(Paths.get(modelPath))
                .optTranslator(new ImageClassificationTranslator(classLabels))
                .optEngine("OnnxRuntime")
                .build();
        ZooModel<Image, Classifications> model = criteria.loadModel();
        Predictor<Image, Classifications> predictor = model.newPredictor();

        return predictor;
    }

}
