package graphql.language;


import graphql.PublicApi;

/**
 * An interface for type definitions in a Schema Definition Language (SDL).
 *
 * @param <T> the actual Node type
 */
@PublicApi
public interface TypeDefinition<T extends TypeDefinition> extends SDLNamedDefinition<T>, DirectivesContainer<T>, NamedNode<T> {

}
