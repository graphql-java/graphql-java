package graphql.execution;

import graphql.PublicSpi;

@PublicSpi
public interface ValueUnboxer {

    ValueUnboxer DEFAULT = new DefaultValueUnboxer();

    /**
     * Unboxes 'object' if it is boxed in an {@link java.util.Optional } like
     * type that this unboxer can handle. Otherwise returns its input
     * unmodified
     *
     * @param object to unbox
     *
     * @return unboxed object, or original if cannot unbox
     */
    Object unbox(final Object object);
}