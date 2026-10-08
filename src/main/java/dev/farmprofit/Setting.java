package dev.farmprofit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts a Config field into the settings menu (/profit settings).
 * Every new feature adds its options to Config with this annotation, so they show up automatically.
 * Fields without it still appear, under "Other".
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Setting {
    String category();
    String label();
    String desc() default "";
    /** For text settings with fixed choices: the button cycles through these. */
    String[] options() default {};
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
    /** Kept in config.json but not shown in the menu (e.g. replaced by the HUD editor). */
    boolean hidden() default false;
    /** Heading this setting sits under inside its tab. */
    String section() default "";
    /** Position in the menu (lower = higher up). */
    int order() default 100000;
}
