package graphql.language;


import graphql.PublicApi;

import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.allDirectivesByName;
import static graphql.language.NodeUtil.nonRepeatableDirectivesByName;
import static graphql.language.NodeUtil.nonRepeatedDirectiveByNameWithAssert;
import static java.util.Collections.emptyList;

/**
 * Represents a language node that can contain Directives.  Directives can be repeatable and (by default) non repeatable.
 * <p>
 * There are access methods here that get the two different types.
 *
 * @see graphql.language.DirectiveDefinition
 * @see DirectiveDefinition#isRepeatable()
 */
@PublicApi
public interface DirectivesContainer<T extends DirectivesContainer> extends Node<T> {

    /**
     * This will return a list of all the directives that have been put on {@link graphql.language.Node} as a flat list, which may contain repeatable
     * and non repeatable directives.
     *
     * @return a list of all the directives associated with this Node
     */
    List<Directive> getDirectives();

    /**
     * This will return a Map of the non repeatable directives that are associated with a {@link graphql.language.Node}.  Any repeatable directives
     * will be filtered out of this map.
     *
     * @return a map of non repeatable directives by directive name.
     */
    default Map<String, Directive> getDirectivesByName() {
        return nonRepeatableDirectivesByName(getDirectives());
    }

    /**
     * This will return a Map of the all directives that are associated with a {@link graphql.language.Node}, including both repeatable and non repeatable directives.
     *
     * @return a map of all directives by directive name
     */
    default Map<String, List<Directive>> getAllDirectivesByName() {
        return allDirectivesByName(getDirectives());
    }

    /**
     * Returns a non repeatable directive with the provided name.  This will throw a {@link graphql.AssertException} if
     * the directive is a repeatable directive that has more then one instance.
     *
     * @param directiveName the name of the directive to retrieve
     * @return the directive or null if there is not one with that name
     */
    default Directive getDirective(String directiveName) {
        return nonRepeatedDirectiveByNameWithAssert(getAllDirectivesByName(), directiveName);
    }

    /**
     * Returns all of the directives with the provided name, including repeatable and non repeatable directives.
     *
     * @param directiveName the name of the directives to retrieve
     * @return the directives or empty list if there is not one with that name
     */
    default List<Directive> getDirectives(String directiveName) {
        return getAllDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
