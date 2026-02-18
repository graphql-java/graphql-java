package graphql.language;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * Represents a language node that can contain Directives.  Directives can be repeatable and (by default) non repeatable.
 * <p>
 * There are access methods here that get the two different types.
 *
 * @see graphql.language.DirectiveDefinition
 * @see DirectiveDefinition#isRepeatable()
 */
@PublicApi
@NullMarked
public interface DirectivesContainer<T extends DirectivesContainer> extends Node<T> {

    /**
     * This will return a list of all the directives that have been put on {@link graphql.language.Node} as a flat list, which may contain repeatable
     * and non repeatable directives.
     *
     * @return a list of all the directives associated with this Node
     */
    List<Directive> getDirectives();

    /**
     * This will return a Map of the all directives that are associated with a {@link graphql.language.Node}, including both repeatable and non repeatable directives.
     *
     * @return a map of all directives by directive name
     */
    Map<String, List<Directive>> getDirectivesByName();

    /**
     * Returns all the directives with the provided name, including repeatable and non repeatable directives.
     *
     * @param directiveName the name of the directives to retrieve
     *
     * @return the directives or empty list if there is not one with that name
     */
    List<Directive> getDirectives(String directiveName);

    /**
     * This returns true if the AST node contains one or more directives by the specified name
     *
     * @param directiveName the name ot check
     *
     * @return true if the AST node contains one or more directives by the specified name
     */
    boolean hasDirective(String directiveName);
}
