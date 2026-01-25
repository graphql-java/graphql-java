package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * A interface for named Schema Definition Language (SDL) definition.
 *
 * @param <T> the actual Node type
 */
@PublicApi
@NullMarked
public interface SDLNamedDefinition<T extends SDLNamedDefinition> extends SDLDefinition<T> {

    /**
     * @return The name of this SDL definition
     */
    String getName();
}
