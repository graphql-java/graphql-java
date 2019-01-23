package graphql.execution.directives;

import graphql.PublicApi;
import graphql.schema.GraphQLDirective;

import java.util.List;
import java.util.Map;

/**
 * This gives you access to the directives on a field including hierarchical ones that may be on
 * an enclosing parent fields or fragments.
 * <p>
 * For example the following (quite pathological) query has a hierarchical list of directives in play.
 * <pre>
 * {@code
 *   fragment Details on Book @timeout(afterMillis: 25) {
 *       title
 *       review @timeout(afterMillis: 5)
 *   }
 *
 *   query Books @timeout(afterMillis: 30) {
 *       books(searchString: "monkey") {
 *           id
 *           ...Details @timeout(afterMillis: 20)
 *           review @timeout(afterMillis: 10)
 *       }
 *   }
 *   }
 * </pre>
 * <p>
 * Also note that graphql specifications says that directives in the one location MUST be unique however because of field merging
 * and the hierarchical ones we can get the same named directive multiple times.  It is therefore up to you
 * to decide how to treat that in your {@link graphql.schema.DataFetcher} code.
 *
 * @see graphql.execution.MergedField
 */
@PublicApi
public interface QueryDirectives {

    /**
     * This will return a map of the directives that are immediately on a field (as opposed to hierarchical ones that might be on an enclosing
     * parent field or fragments)
     *
     * @return a map of all the directives immediately on this field
     */
    Map<String, List<GraphQLDirective>> getImmediateDirectives();

    /**
     * This will return a list of the named directives that are closest to a field
     *
     * @param directiveName the named directive
     *
     * @return a list of the named directives that are closest to a field
     */
    List<GraphQLDirective> getClosestDirective(String directiveName);

    /**
     * This will return a list of the named directives that are immediately on this field (as opposed to hierarchical ones that might be on an enclosing
     * parent field or fragments)
     *
     * @param directiveName the named directive
     *
     * @return a list of the named directives that are immediately on this field
     */
    List<GraphQLDirective> getImmediateDirective(String directiveName);

    /**
     * This will return all the directives that are on this field including hierarchical ones.
     * <p>
     * The list is sorted in distance order with the closest directives coming first.
     * <p>
     * {@link FieldDirectivesInfo} tells you their distance from the
     * the field, the AST element that contained this directives and all the directives that are present
     * at that location.
     *
     * @return a map of all the directives on a field sorted in distance order
     */
    List<FieldDirectivesInfo> getAllDirectives();

    /**
     * This will return all the named directives that are on this field including hierarchical ones.
     * <p>
     * The list is sorted in distance order with the closest directives coming first.
     * <p>
     * {@link FieldDirectivesInfo} tells you their distance from the
     * the field, the AST element that contained this directives and all the directives that are present
     * at that location but in this case the list of directives is filtered to the named directive
     *
     * @param directiveName the name of the directive to return
     *
     * @return a list of directive info with just the named directive in them
     */
    List<FieldDirectivesInfo> getAllDirectivesNamed(String directiveName);
}
