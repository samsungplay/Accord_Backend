package com.infiniteplay.accord.utils;

import jakarta.persistence.Persistence;

public class JacksonLazyFieldsFilter {
    @Override
    public boolean equals(Object obj) {
        return !Persistence.getPersistenceUtil().isLoaded(obj);
    }
}
