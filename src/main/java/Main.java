import ai.djl.Application;
import ai.djl.Model;
import ai.djl.ModelException;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {

    //todo:
    //1. incorporate nsfw detectors - set isNSFW flag
    //2. incorporate spam detectors - set isSpam flag

    public static void main(String[] args) throws IOException, ModelException, TranslateException {
        List<String> classLabels = Arrays.asList("normal", "nsfw");
        String modelPath = "./src/ai_models/spam_text_detector/model.onnx";
        String tokenizerPath = "./src/ai_models/spam_text_detector";

        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelPath(Paths.get(modelPath))
                .optTranslator(new TextClassificationTranslator(tokenizerPath, List.of("ham","spam")))
                .optEngine("OnnxRuntime")
                .build();

        try (ZooModel<String, Classifications> model = criteria.loadModel();
             Predictor<String, Classifications> predictor = model.newPredictor()) {

            String input = "20% off limited time sale! Get your chance now";

                 Classifications result = predictor.predict(input);

                double max = Integer.MIN_VALUE;
                String maxClassName = "";

                 for(int i=0; i<result.getProbabilities().size(); i++) {
                     if(result.getProbabilities().get(i)>max) {
                         max = result.getProbabilities().get(i);
                         maxClassName = result.getClassNames().get(i);
                     }
                 }
            System.out.println(maxClassName);
        }
    }

    public static class ImageClassificationTranslator implements Translator<Image, Classifications> {

        @Override
        public Classifications processOutput(TranslatorContext ctx, NDList list) throws Exception {
            return new Classifications(List.of("normal","nsfw"), list.singletonOrThrow().softmax(0));
        }


        @Override
        public NDList processInput(TranslatorContext ctx, Image input) throws Exception {
            NDManager manager = ctx.getNDManager();


            input = input.resize(224,224,false);
            NDArray arr = input.toNDArray(manager);

            arr = arr.toType(DataType.FLOAT32,false).div(255.0f);
            arr = arr.sub(manager.create(new float[]{0.485f, 0.456f, 0.406f})); // Subtract mean
            arr = arr.div(manager.create(new float[]{0.229f, 0.224f, 0.225f}));
            arr = arr.transpose(2,0,1);
//            arr = arr.expandDims(0);


            return new NDList(arr);
        }
    }

    public static class TextClassificationTranslator implements Translator<String, Classifications> {

        private HuggingFaceTokenizer tokenizer;
        private List<String> labels;

        public TextClassificationTranslator(String tokenizerPath, List<String> labels) {
            try {
                this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(tokenizerPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.labels = labels;
        }
        @Override
        public NDList processInput(TranslatorContext ctx, String input) {
            Encoding token = tokenizer.encode(input);
            long[] inputIds = token.getIds();
            long[] attentionMask = token.getAttentionMask();
            NDArray inputIdsArray = ctx.getNDManager().create(inputIds).expandDims(0);

            NDArray attentionMaskArray = ctx.getNDManager().create(attentionMask).expandDims(0);
            NDList list = new NDList(inputIdsArray, attentionMaskArray);

            return list;
        }

        @Override
        public Classifications processOutput(TranslatorContext ctx, ai.djl.ndarray.NDList list) {
            NDArray logits = list.singletonOrThrow();

            NDArray probs = logits.softmax(1);

            return new Classifications(labels, probs);
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
