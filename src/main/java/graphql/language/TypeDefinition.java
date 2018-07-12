package graphql.language;


import graphql.PublicApi;

import java.util.List;

@PublicApi
public interface TypeDefinition<T extends TypeDefinition> extends SDLDefinition<T> {
    /**
     * @return the name of the type being defined.
     */
    String getName();

    /**
     * @return the directives of this type being defined
     */
    List<Directive> getDirectives();
}
