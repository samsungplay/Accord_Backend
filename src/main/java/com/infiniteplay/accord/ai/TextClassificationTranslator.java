package com.infiniteplay.accord.ai;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class TextClassificationTranslator implements Translator<String, Classifications> {

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