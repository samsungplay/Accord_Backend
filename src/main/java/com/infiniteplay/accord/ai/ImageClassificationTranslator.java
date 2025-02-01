package com.infiniteplay.accord.ai;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.util.List;

public class ImageClassificationTranslator implements Translator<Image, Classifications> {

    List<String> labels;

    public ImageClassificationTranslator(List<String> labels) {
        this.labels = labels;
    }


    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) throws Exception {
        return new Classifications(labels, list.singletonOrThrow().softmax(0));
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