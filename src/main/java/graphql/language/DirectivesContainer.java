package graphql.language;


import graphql.PublicApi;

import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.allDirectivesByName;
import static graphql.language.NodeUtil.directivesByName;
import static graphql.language.NodeUtil.nonRepeatedDirectiveByNameWithAssert;
import static java.util.Collections.emptyList;

/**
 * Represents a language node that can contain Directives.
 */
@PublicApi
public interface DirectivesContainer<T extends DirectivesContainer> extends Node<T> {

    /**
     * @return a list of directives associated with this Node
     */
    List<Directive> getDirectives();

    /**
     * @return a map of directives by directive name where the directives are only the ones with a single value
     */
    default Map<String, Directive> getDirectivesByName() {
        return directivesByName(getDirectives());
    }

    /**
     * Directives can be `repeatable` and hence this returns a list of directives by name, some with an arity of 1 and some with an arity of greather than
     * 1.
     *
     * @return a map of all directives by directive name
     */
    default Map<String, List<Directive>> getAllDirectivesByName() {
        return allDirectivesByName(getDirectives());
    }

    /**
     * Returns a directive with the provided name.  This will throw a {@link graphql.AssertException} if
     * the directive is a repeatable directive and has more then one instance.
     *
     * @param directiveName the name of the directive to retrieve
     * @return the directive or null if there is not one with that name
     */
    default Directive getDirective(String directiveName) {
        return nonRepeatedDirectiveByNameWithAssert(getAllDirectivesByName(), directiveName);
    }

    /**
     * Returns all of the directives with the provided name.
     *
     * @param directiveName the name of the directives to retrieve
     * @return the directives or empty list if there is not one with that name
     */
    default List<Directive> getDirectives(String directiveName) {
        return getAllDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
