package dev.application.analyze.bm_m023;

import java.util.function.Function;

import dev.application.analyze.common.entity.ThresHoldEntity;


public class FieldMapping {
    private final int index;
    private final Function<ThresHoldEntity, String> getter;

    public FieldMapping(int index, Function<ThresHoldEntity, String> getter) {
        this.index = index;
        this.getter = getter;
    }

    public int getIndex() {
        return index;
    }

    public Function<ThresHoldEntity, String> getGetter() {
        return getter;
    }
}
