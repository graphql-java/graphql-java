package graphql.schema;


import graphql.PublicApi;

import java.util.List;
import java.util.Map;

import static graphql.DirectivesUtil.allDirectivesByName;
import static graphql.DirectivesUtil.directivesByName;
import static graphql.DirectivesUtil.nonRepeatedDirectiveByNameWithAssert;

/**
 * Represents a graphql object that can have {@link graphql.schema.GraphQLDirective}s
 */
@PublicApi
public interface GraphQLDirectiveContainer extends GraphQLNamedSchemaElement {

    /**
     * @return a list of directives associated with the type or field
     */
    List<GraphQLDirective> getDirectives();

    /**
     * @return a a map of non repeatable directives by directive name
     */
    default Map<String, GraphQLDirective> getDirectivesByName() {
        return directivesByName(getDirectives());
    }

    /**
     * Directives can be `repeatable` and hence this returns a list of directives by name, some with an arity of 1 and some with an arity of greater than
     * 1.
     *
     * @return a map of all directives by directive name
     */
    default Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return allDirectivesByName(getDirectives());
    }
    /**
     * Returns a named directive
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is one one with that name
     */
    default GraphQLDirective getDirective(String directiveName) {
        return nonRepeatedDirectiveByNameWithAssert(getAllDirectivesByName(), directiveName);
    }

}
