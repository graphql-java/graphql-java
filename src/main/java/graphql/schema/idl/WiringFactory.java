package graphql.schema.idl;

import graphql.Assert;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

/**
 * A WiringFactory allows you to more dynamically wire in {@link TypeResolver}s and {@link DataFetcher}s
 * based on the IDL definitions.  For example you could look at the directives say to build a more dynamic
 * set of type resolvers and data fetchers.
 */
public interface WiringFactory {

    /**
     * This is called to ask if this factory can provide a type resolver for the interface
     *
     * @param registry      the registry of all types
     * @param interfaceType the definition to be resolved
     * @return true if the factory can give out a type resolver
     */
    boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType);

    /**
     * This is called to ask if this factory can provide a type resolver for the union
     *
     * @param registry  the registry of all types
     * @param unionType the definition to be resolved
     * @return true if the factory can give out a type resolver
     */
    boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType);

    /**
     * Returns a {@link TypeResolver} given the type interface
     *
     * @param registry   the registry of all types
     * @param interfaceType the definition to be resolved
     * @return a {@link TypeResolver}
     */
    TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition interfaceType);

    /**
     * Returns a {@link TypeResolver} given the type union
     *
     * @param registry  the registry of all types
     * @param unionType the definition to be resolved
     * @return a {@link TypeResolver}
     */
    TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition unionType);

    /**
     * This is called to ask if this factory can provide a data fetcher for the definition
     *
     * @param context the context where wiring is happening
     * @return true if the factory can give out a data fetcher
     */
    default boolean providesDataFetcher(WiringContext context) {
        return false;
    }

    /**
     * Returns a {@link DataFetcher} given the type definition
     *
     * @param context the context where wiring is happening
     * @return a {@link DataFetcher}
     */
    default DataFetcher getDataFetcher(WiringContext context) {
        return Assert.assertNeverCalled();
    }

}
