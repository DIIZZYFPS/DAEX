package com.daex.android.services;

import kotlin.jvm.functions.Function1;

public class TtsCallback implements Function1<float[], Integer> {
    private final Function1<float[], Integer> delegate;

    public TtsCallback(Function1<float[], Integer> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Integer invoke(float[] samples) {
        return delegate.invoke(samples);
    }
}
