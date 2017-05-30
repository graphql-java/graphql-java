package graphql.schema.idl;

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.TypeDefinition;
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
     * @param registry   the registry of all types
     * @param parentType the type of the parent
     * @param definition the field definition in play
     * @return true if the factory can give out a date fetcher
     */
    default boolean providesDataFetcher(TypeDefinitionRegistry registry, TypeDefinition parentType, FieldDefinition definition) {
        return providesDataFetcher(registry, definition);
    }

    /**
     * This is called to ask if this factory can provide a data fetcher for the definition
     *
     * @param registry   the registry of all types
     * @param definition the field definition in play
     * @return true if the factory can give out a date fetcher
     * @deprecated use overloaded version with parentType
     */
    @Deprecated
    default boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        return false;
    }

    /**
     * Returns a {@link DataFetcher} given the type definition
     *
     * @param registry   the registry of all types
     * @param parentType the type of the parent
     * @param definition the definition to be resolved
     * @return a {@link DataFetcher}
     */
    default DataFetcher getDataFetcher(TypeDefinitionRegistry registry, TypeDefinition parentType, FieldDefinition definition) {
        return getDataFetcher(registry, definition);
    }

    /**
     * Returns a {@link DataFetcher} given the type definition
     *
     * @param registry   the registry of all types
     * @param definition the definition to be resolved
     * @return a {@link DataFetcher}
     * @deprecated use overloaded version with parentType
     */
    @Deprecated
    default DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
        return null;
    }

}
