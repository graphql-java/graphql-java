package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * An interface for type definitions in a Schema Definition Language (SDL).
 *
 * @param <T> the actual Node type
 */
@PublicApi
@NullMarked
public interface TypeDefinition<T extends TypeDefinition> extends SDLNamedDefinition<T>, DirectivesContainer<T>, NamedNode<T> {

    @Override
    String getName();
}
