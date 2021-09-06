package graphql.language;


import graphql.PublicApi;

/**
 * An interface for Schema Definition Language (SDL) definitions.
 *
 * @param <T> the actual Node type
 */
@PublicApi
public interface SDLDefinition<T extends SDLDefinition> extends Definition<T> {

}
