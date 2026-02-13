package graphql.introspection;

import graphql.ErrorClassification;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.validation.QueryComplexityLimits;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Good Faith Introspection ensures that introspection queries are not abused to cause denial of service.
 * <p>
 * There are attack vectors where a crafted introspection query can cause the engine to spend too much time
 * producing introspection data.  This is especially true on large schemas with lots of types and fields.
 * <p>
 * Schemas form a cyclic graph and hence it's possible to send in introspection queries that can reference those cycles
 * and in large schemas this can be expensive and perhaps a "denial of service".
 * <p>
 * When enabled, the validation layer enforces that:
 * <ul>
 * <li>Only one {@code __schema} and one {@code __type} field can appear per operation</li>
 * <li>The {@code __Type} fields {@code fields}, {@code inputFields}, {@code interfaces}, and {@code possibleTypes}
 * can each only appear once (preventing cyclic traversals)</li>
 * <li>The query complexity is limited to {@link #GOOD_FAITH_MAX_FIELDS_COUNT} fields and
 * {@link #GOOD_FAITH_MAX_DEPTH_COUNT} depth</li>
 * </ul>
 * This allows the standard and common introspection queries to work so tooling such as graphiql can work.
 */
@PublicApi
@NullMarked
public class GoodFaithIntrospection {

    /**
     * Placing a boolean value under this key in the per request {@link GraphQLContext} will enable
     * or disable Good Faith Introspection on that request.
     */
    public static final String GOOD_FAITH_INTROSPECTION_DISABLED = "GOOD_FAITH_INTROSPECTION_DISABLED";

    private static final AtomicBoolean ENABLED_STATE = new AtomicBoolean(true);
    /**
     * This is the maximum number of executable fields that can be in a good faith introspection query
     */
    public static final int GOOD_FAITH_MAX_FIELDS_COUNT = 500;
    /**
     * This is the maximum depth a good faith introspection query can be
     */
    public static final int GOOD_FAITH_MAX_DEPTH_COUNT = 20;

    /**
     * @return true if good faith introspection is enabled
     */
    public static boolean isEnabledJvmWide() {
        return ENABLED_STATE.get();
    }

    /**
     * This allows you to disable good faith introspection, which is on by default.
     *
     * @param flag the desired state
     *
     * @return the previous state
     */
    public static boolean enabledJvmWide(boolean flag) {
        return ENABLED_STATE.getAndSet(flag);
    }

    /**
     * Checks whether Good Faith Introspection is enabled for the given request context.
     *
     * @param graphQLContext the per-request context
     *
     * @return true if good faith introspection checks should be applied
     */
    public static boolean isEnabled(GraphQLContext graphQLContext) {
        if (!isEnabledJvmWide()) {
            return false;
        }
        return !graphQLContext.getBoolean(GOOD_FAITH_INTROSPECTION_DISABLED, false);
    }

    /**
     * Performs a shallow scan of the document to check if any operation's top-level selections
     * contain introspection fields ({@code __schema} or {@code __type}).
     *
     * @param document the parsed document
     *
     * @return true if the document contains top-level introspection fields
     */
    public static boolean containsIntrospectionFields(Document document) {
        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                SelectionSet selectionSet = ((OperationDefinition) definition).getSelectionSet();
                if (selectionSet != null) {
                    for (Selection<?> selection : selectionSet.getSelections()) {
                        if (selection instanceof Field) {
                            String name = ((Field) selection).getName();
                            if ("__schema".equals(name) || "__type".equals(name)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns query complexity limits that are the minimum of the existing limits and the
     * good faith introspection limits. This ensures introspection queries are bounded
     * without overriding tighter user-specified limits.
     *
     * @param existing the existing complexity limits (may be null, in which case defaults are used)
     *
     * @return complexity limits with good faith bounds applied
     */
    public static QueryComplexityLimits goodFaithLimits(QueryComplexityLimits existing) {
        if (existing == null) {
            existing = QueryComplexityLimits.getDefaultLimits();
        }
        int maxFields = Math.min(existing.getMaxFieldsCount(), GOOD_FAITH_MAX_FIELDS_COUNT);
        int maxDepth = Math.min(existing.getMaxDepth(), GOOD_FAITH_MAX_DEPTH_COUNT);
        return QueryComplexityLimits.newLimits()
                .maxFieldsCount(maxFields)
                .maxDepth(maxDepth)
                .build();
    }

    public static class BadFaithIntrospectionError implements GraphQLError {
        private final String message;

        public static BadFaithIntrospectionError tooManyFields(String fieldCoordinate) {
            return new BadFaithIntrospectionError(String.format("This request is not asking for introspection in good faith - %s is present too often!", fieldCoordinate));
        }

        public static BadFaithIntrospectionError tooBigOperation(String message) {
            return new BadFaithIntrospectionError(String.format("This request is not asking for introspection in good faith - the query is too big: %s", message));
        }

        private BadFaithIntrospectionError(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public ErrorClassification getErrorType() {
            return ErrorClassification.errorClassification("BadFaithIntrospection");
        }

        @Override
        public @Nullable List<SourceLocation> getLocations() {
            return null;
        }

        @Override
        public String toString() {
            return "BadFaithIntrospectionError{" +
                    "message='" + message + '\'' +
                    '}';
        }
    }
}
