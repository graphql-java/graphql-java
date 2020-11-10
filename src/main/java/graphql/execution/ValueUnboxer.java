package graphql.execution;

import graphql.PublicSpi;

/**
 * A value unboxer takes values that are wrapped in classes like {@link java.util.Optional} / {@link java.util.OptionalInt} etc..
 * and returns value from them.  You can provide your own implementation if you have your own specific
 * holder classes.
 */
@PublicSpi
public interface ValueUnboxer {

    /**
     * The default value unboxer handles JDK classes such as {@link java.util.Optional} and {@link java.util.OptionalInt} etc..
     */
    ValueUnboxer DEFAULT = new DefaultValueUnboxer();

    /**
     * Unboxes 'object' if it is boxed in an {@link java.util.Optional } like
     * type that this unboxer can handle. Otherwise returns its input
     * unmodified
     *
     * @param object to unbox
     * @return unboxed object, or original if cannot unbox
     */
    Object unbox(final Object object);
}