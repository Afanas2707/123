package org.nobilis.nobichat.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtectByUISchema {
    String operationType();

    /**
     * Указывает конкретное действие (например, "action:delete"),
     * наличие которого необходимо проверить в UI-схеме.
     */
    String requiredAction() default "";
}