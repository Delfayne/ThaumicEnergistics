package thaumicenergistics.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// https://github.com/embeddedt/VintageFix/blob/15291a5829ead82cf7dde9b482a2a2cc95ea45b7/src/main/java/org/embeddedt/vintagefix/annotation/ClientOnlyMixin.java
@Retention(CLASS)
@Target(TYPE)
public @interface ClientOnlyMixin {}
