package graphql.schema.idl;

import graphql.PublicSpi;
import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

import static graphql.Assert.assertShouldNeverHappen;

/**
 * A WiringFactory allows you to more dynamically wire in {@link TypeResolver}s and {@link DataFetcher}s
 * based on the IDL definitions.  For example you could look at the directives say to build a more dynamic
 * set of type resolvers and data fetchers.
 */
@PublicSpi
public interface WiringFactory {

    /**
     * This is called to ask if this factory can provide a type resolver for the interface
     *
     * @param environment the wiring environment
     *
     * @return true if the factory can give out a type resolver
     */
    default boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return false;
    }

    /**
     * Returns a {@link TypeResolver} given the type interface
     *
     * @param environment the wiring environment
     *
     * @return a {@link TypeResolver}
     */
    default TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
        return assertShouldNeverHappen();
    }

    /**
     * This is called to ask if this factory can provide a type resolver for the union
     *
     * @param environment the wiring environment
     *
     * @return true if the factory can give out a type resolver
     */
    default boolean providesTypeResolver(UnionWiringEnvironment environment) {
        return false;
    }

    /**
     * Returns a {@link TypeResolver} given the type union
     *
     * @param environment the union wiring environment
     *
     * @return a {@link TypeResolver}
     */
    default TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
        return assertShouldNeverHappen();
    }

    /**
     * This is called to ask if this factory can provide a data fetcher for the definition
     *
     * @param environment the wiring environment
     *
     * @return true if the factory can give out a data fetcher
     */
    default boolean providesDataFetcher(FieldWiringEnvironment environment) {
        return false;
    }

    /**
     * Returns a {@link DataFetcher} given the type definition
     *
     * @param environment the wiring environment
     *
     * @return a {@link DataFetcher}
     */
    default DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
        return assertShouldNeverHappen();
    }
}
