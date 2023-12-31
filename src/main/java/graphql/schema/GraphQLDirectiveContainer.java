package graphql.schema;

import graphql.PublicApi;

import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.emptyList;

/**
 * Represents a graphql runtime type that can have {@link graphql.schema.GraphQLAppliedDirective}s.
 * <p>
 * Directives can be repeatable and (by default) non-repeatable.
 * <p>
 * There are access methods here that get the two different types.
 * <p>
 * The use of {@link GraphQLDirective} to represent a directive applied to an element is deprecated in favour of
 * {@link GraphQLAppliedDirective}. A {@link GraphQLDirective} really should represent the definition of a directive in a schema, not its use
 * on schema elements. However, it has been left in place for legacy reasons and will be removed in a
 * future version.
 *
 * @see graphql.language.DirectiveDefinition
 * @see graphql.language.DirectiveDefinition#isRepeatable()
 */
@PublicApi
public interface GraphQLDirectiveContainer extends GraphQLNamedSchemaElement {

    String CHILD_DIRECTIVES = "directives";
    String CHILD_APPLIED_DIRECTIVES = "appliedDirectives";


    /**
     * This will return a list of all the directives that have been put on {@link graphql.schema.GraphQLNamedSchemaElement} as a flat list, which may contain repeatable
     * and non-repeatable directives.
     *
     * @return a list of all the directives associated with this named schema element
     */
    List<GraphQLAppliedDirective> getAppliedDirectives();

    /**
     * This will return a Map of the all directives that are associated with a {@link graphql.schema.GraphQLNamedSchemaElement}, including both
     * repeatable and non-repeatable directives.
     *
     * @return a map of all directives by directive name
     */
    Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName();

    /**
     * Returns a non-repeatable directive with the provided name.
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is not one with that name
     */
    GraphQLAppliedDirective getAppliedDirective(String directiveName);


    /**
     * Returns all of the directives with the provided name, including repeatable and non repeatable directives.
     *
     * @param directiveName the name of the directives to retrieve
     *
     * @return the directives or empty list if there is not one with that name
     */
    default List<GraphQLAppliedDirective> getAppliedDirectives(String directiveName) {
        return getAllAppliedDirectivesByName().getOrDefault(directiveName, emptyList());
    }

    /**
     * This will return true if the element has a directive (repeatable or non repeatable) with the specified name
     *
     * @param directiveName the name of the directive
     *
     * @return true if there is a directive on this element with that name
     *
     * @deprecated use {@link #hasAppliedDirective(String)} instead
     */
    @Deprecated(since = "2022-02-24")
    default boolean hasDirective(String directiveName) {
        return getAllDirectivesByName().containsKey(directiveName);
    }

    /**
     * This will return true if the element has a directive (repeatable or non repeatable) with the specified name
     *
     * @param directiveName the name of the directive
     *
     * @return true if there is a directive on this element with that name
     */
    default boolean hasAppliedDirective(String directiveName) {
        return getAllAppliedDirectivesByName().containsKey(directiveName);
    }

    /**
     * This will return a list of all the directives that have been put on {@link graphql.schema.GraphQLNamedSchemaElement} as a flat list, which may contain repeatable
     * and non-repeatable directives.
     *
     * @return a list of all the directives associated with this named schema element
     *
     * @deprecated - use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    List<GraphQLDirective> getDirectives();

    /**
     * This will return a Map of the non repeatable directives that are associated with a {@link graphql.schema.GraphQLNamedSchemaElement}.  Any repeatable directives
     * will be filtered out of this map.
     *
     * @return a map of non repeatable directives by directive name.
     *
     * @deprecated - use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    Map<String, GraphQLDirective> getDirectivesByName();

    /**
     * This will return a Map of the all directives that are associated with a {@link graphql.schema.GraphQLNamedSchemaElement}, including both
     * repeatable and non repeatable directives.
     *
     * @return a map of all directives by directive name
     *
     * @deprecated - use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    Map<String, List<GraphQLDirective>> getAllDirectivesByName();

    /**
     * Returns a non-repeatable directive with the provided name.  This will throw a {@link graphql.AssertException} if
     * the directive is a repeatable directive that has more then one instance.
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is not one with that name
     *
     * @deprecated - use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    GraphQLDirective getDirective(String directiveName);

    /**
     * Returns all of the directives with the provided name, including repeatable and non repeatable directives.
     *
     * @param directiveName the name of the directives to retrieve
     *
     * @return the directives or empty list if there is not one with that name
     *
     * @deprecated - use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    default List<GraphQLDirective> getDirectives(String directiveName) {
        return getAllDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
