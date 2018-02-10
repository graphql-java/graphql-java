package graphql.schema;


import java.util.List;
import java.util.Map;

/**
 * Represents a type that can have {@link graphql.schema.GraphQLDirective}s
 */
public interface GraphQLDirectiveContainer extends GraphQLType {

    /**
     * @return a list of directives associated with the type
     */
    List<GraphQLDirective> getDirectives();

    /**
     * @return a a map of directives by directive name
     */
    Map<String, GraphQLDirective> getDirectivesByName();

    /**
     * Returns a named directive
     *
     * @param directiveName the name of the directive to retrive
     *
     * @return the directive or null if there is one one with that name
     */
    default GraphQLDirective getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
    }

}
