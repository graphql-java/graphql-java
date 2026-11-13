package graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Custom contract annotation used for jspecify and NullAway checks.
 *
 * This is the same as Spring does: we don't want any additional dependencies, therefore we define our own Contract annotation.
 *
 * @see <a href="https://raw.githubusercontent.com/spring-projects/spring-framework/refs/heads/main/spring-core/src/main/java/org/springframework/lang/Contract.java">Spring Framework Contract</a>
 * @see <a href="https://github.com/JetBrains/java-annotations/blob/master/src/jvmMain/java/org/jetbrains/annotations/Contract.java">org.jetbrains.annotations.Contract</a>
 * @see <a href="https://github.com/uber/NullAway/wiki/Configuration#custom-contract-annotations">
 * NullAway custom contract annotations</a>
 */
@Documented
@Target(ElementType.METHOD)
@Internal
public @interface Contract {

    /**
     * Describing the contract between call arguments and the returned value.
     */
    String value() default "";

}
