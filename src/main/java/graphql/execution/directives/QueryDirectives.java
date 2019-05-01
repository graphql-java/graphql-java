package graphql.execution.directives;

import graphql.PublicApi;
import graphql.schema.GraphQLDirective;

import java.util.List;
import java.util.Map;

/**
 * This gives you access to the immediate directives on a {@link graphql.execution.MergedField}.  This does not include directives on parent
 * fields or fragment containers.
 * <p>
 * Because a {@link graphql.execution.MergedField} can actually have multiple fields and hence
 * directives on each field instance its possible that there is more than one directive named "foo"
 * on the merged field.  How you decide which one to use is up to your code.
 *
 * @see graphql.execution.MergedField
 */
@PublicApi
public interface QueryDirectives {

    /**
     * This will return a map of the directives that are immediately on a field
     *
     * @return a map of all the directives immediately on this field
     */
    Map<String, List<GraphQLDirective>> getImmediateDirectives();


    /**
     * This will return a list of the named directives that are immediately on this field.
     *
     * Read above for why this is a list of directives and not just one
     *
     * @param directiveName the named directive
     *
     * @return a list of the named directives that are immediately on this field
     */
    List<GraphQLDirective> getImmediateDirective(String directiveName);
}
