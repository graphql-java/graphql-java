package graphql.execution.directives;

import graphql.introspection.Introspection;
import graphql.language.DirectivesContainer;
import graphql.schema.GraphQLDirective;

import java.util.Map;

/**
 * Directives can be hierarchical and can occur not just directly on a field but only above it in the query tree.
 * <p>
 * So imagine a (quite pathological query) like the following and the field "review"
 *
 * <pre>
 * {@code
 *   fragment Details on Book @timeout(afterMillis: 25) {       # container = fragment definition ; distance = 1
 *       title
 *       review @timeout(afterMillis: 5)                        # container = field ; distance = 0
 *   }
 *
 *   query Books @timeout(afterMillis: 30) {                    # container = operation definition ; distance = 3
 *       books(searchString: "monkey") {
 *           id
 *           ...Details @timeout(afterMillis: 20)               # container = fragment spread; distance = 2
 *           review @timeout(afterMillis: 10)                   # container = field ; distance = 0
 *       }
 *   }
 *   }
 * </pre>
 */
public interface FieldDirectivesInfo extends Comparable<FieldDirectivesInfo> {

    /**
     * @return the query AST node that contained the directives
     */
    DirectivesContainer getDirectivesContainer();

    /**
     * @return the map of resolved directives on that AST element
     */
    Map<String, GraphQLDirective> getDirectives();

    /**
     * @return the distance from the originating field where 0 is on the field itself
     */
    int getDistance();

    /**
     * @return an enum of the location of the directive
     */
    Introspection.DirectiveLocation getDirectiveLocation();

    /**
     * This will create a new FieldDirectivesInfo that filters our the list of directives to a specifically named
     * directive and otherwise keeps the other information the same
     *
     * @return a copy that only contains the named directive
     */
    FieldDirectivesInfo restrictTo(String directiveName);
}
