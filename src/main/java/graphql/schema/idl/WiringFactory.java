package graphql.schema.idl;

import graphql.language.FieldDefinition;
import graphql.language.ResolvedTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

/**
 * A WiringFactory allows you to more dynamically wire in {@link TypeResolver}s and {@link DataFetcher}s
 * based on the IDL definitions.  For example you could look at the directives say to build a more dynamic
 * set of type resolvers and data fetchers.
 */
public interface WiringFactory {

    /**
     * This is called to ask if this factory can provide a type resolver for the definition
     *
     * @param registry   the registry of all types
     * @param definition the definition to be resolved
     *
     * @return true if the factory can give out a type resolver
     */
    boolean providesTypeResolver(TypeDefinitionRegistry registry, ResolvedTypeDefinition definition);

    /**
     * Returns a {@link TypeResolver} given the type definition
     *
     * @param registry   the registry of all types
     * @param definition the definition to be resolved
     *
     * @return a {@link TypeResolver}
     */
    TypeResolver getTypeResolver(TypeDefinitionRegistry registry, ResolvedTypeDefinition definition);

    /**
     * This is called to ask if this factory can provide a data fetcher for the definition
     *
     * @param registry   the registry of all types
     * @param definition the field definition in play
     *
     * @return true if the factory can give out a date fetcher
     */
    boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition);

    /**
     * Returns a {@link DataFetcher} given the type definition
     *
     * @param registry   the registry of all types
     * @param definition the definition to be resolved
     *
     * @return a {@link DataFetcher}
     */
    DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition);


    /**
     * A {@link WiringFactory} that never wires anything and never says it can wire anything
     */
    WiringFactory NOOP_WIRING_FACTORY = new WiringFactory() {
        @Override
        public boolean providesTypeResolver(TypeDefinitionRegistry registry, ResolvedTypeDefinition definition) {
            return false;
        }

        @Override
        public boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return false;
        }

        @Override
        public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, ResolvedTypeDefinition definition) {
            return null;
        }

        @Override
        public DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return null;
        }
    };
}
