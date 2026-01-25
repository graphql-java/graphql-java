package graphql.execution.directives;

import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.CoercedVariables;
import graphql.execution.MergedField;
import graphql.execution.NormalizedVariables;
import graphql.language.Field;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This gives you access to the immediate directives on a {@link graphql.execution.MergedField}.  This does not include directives on parent
 * fields or fragment containers.
 * <p>
 * Because a {@link graphql.execution.MergedField} can actually have multiple fields and hence
 * directives on each field instance its possible that there is more than one directive named "foo"
 * on the merged field.  How you decide which one to use is up to your code.
 * <p>
 * NOTE: A future version of the interface will try to add access to the inherited directives from
 * parent fields and fragments.  This proved to be a non-trivial problem and hence we decide
 * to give access to immediate field directives and provide this holder interface, so we can
 * add the other directives in the future
 *
 * @see graphql.execution.MergedField
 */
@PublicApi
@NullMarked
public interface QueryDirectives {

    /**
     * This will return a map of the applied directives that are immediately on a merged field
     *
     * @return a map of all the applied directives immediately on this merged field
     */
    Map<String, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByName();

    /**
     * This will return a list of the named applied directives that are immediately on this merged field.
     *
     * Read above for why this is a list of applied directives and not just one
     *
     * @param directiveName the named directive
     *
     * @return a list of the named applied directives that are immediately on this merged field
     */
    List<QueryAppliedDirective> getImmediateAppliedDirective(String directiveName);

    /**
     * This will return a map of the directives that are immediately on a merged field
     *
     * @return a map of all the directives immediately on this merged field
     *
     * @deprecated - use the {@link QueryAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    Map<String, List<GraphQLDirective>> getImmediateDirectivesByName();

    /**
     * This will return a map of the {@link graphql.language.Field}s inside a {@link graphql.execution.MergedField}
     * and the immediate applied directives that are on each specific field
     *
     * @return a map of all directives on each field inside this
     */
    Map<Field, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByField();

    /**
     * This will return a map of {@link QueryAppliedDirective} to a map of their argument values in {@link NormalizedInputValue} form
     * <p>
     * NOTE : This will only be available when {@link graphql.normalized.ExecutableNormalizedOperationFactory} is used
     * to create the {@link QueryAppliedDirective} information
     *
     * @return a map of applied directive to named argument values
     */
    Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> getNormalizedInputValueByImmediateAppliedDirectives();

    /**
     * This will return a list of the named directives that are immediately on this merged field.
     *
     * Read above for why this is a list of directives and not just one
     *
     * @param directiveName the named directive
     *
     * @return a list of the named directives that are immediately on this merged field
     *
     * @deprecated - use the {@link QueryAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    List<GraphQLDirective> getImmediateDirective(String directiveName);

    /**
     * This will return a map of the {@link graphql.language.Field}s inside a {@link graphql.execution.MergedField}
     * and the immediate directives that are on each specific field
     *
     * @return a map of all directives on each field inside this
     *
     * @deprecated - use the {@link QueryAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField();

    /**
     * @return a builder of {@link QueryDirectives}
     */
    static Builder newQueryDirectives() {
        return new QueryDirectivesBuilder();
    }

    @NullUnmarked
    interface Builder {

        Builder schema(GraphQLSchema schema);

        Builder mergedField(MergedField mergedField);

        Builder field(Field field);

        Builder coercedVariables(CoercedVariables coercedVariables);

        Builder normalizedVariables(Supplier<NormalizedVariables> normalizedVariables);

        Builder graphQLContext(GraphQLContext graphQLContext);

        Builder locale(Locale locale);

        QueryDirectives build();
    }
}
