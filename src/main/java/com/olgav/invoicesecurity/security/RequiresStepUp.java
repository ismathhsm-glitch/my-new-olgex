package com.olgav.invoicesecurity.security;

import com.olgav.invoicesecurity.model.SensitiveAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresStepUp {
    SensitiveAction action();
}
