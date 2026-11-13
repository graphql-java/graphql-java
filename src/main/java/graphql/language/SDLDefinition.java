package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * An interface for Schema Definition Language (SDL) definitions.
 *
 * @param <T> the actual Node type
 */
@PublicApi
@NullMarked
public interface SDLDefinition<T extends SDLDefinition> extends Definition<T> {

}
