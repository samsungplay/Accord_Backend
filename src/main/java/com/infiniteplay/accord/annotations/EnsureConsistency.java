package com.infiniteplay.accord.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EnsureConsistency {
    int maxRetries() default 10;
    long delay() default 100;  // Delay between retries in milliseconds
}