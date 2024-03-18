package graphql.introspection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import graphql.ErrorClassification;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.language.SourceLocation;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.FieldCoordinates;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static graphql.schema.FieldCoordinates.coordinates;

/**
 * This {@link graphql.execution.instrumentation.Instrumentation} ensure that a submitted introspection query is done in
 * good faith.
 * <p>
 * There are attack vectors where a crafted introspection query can cause the engine to spend too much time
 * producing introspection data.  This is especially true on large schemas with lots of types and fields.
 * <p>
 * Schemas form a cyclic graph and hence it's possible to send in introspection queries that can reference those cycles
 * and in large schemas this can be expensive and perhaps a "denial of service".
 * <p>
 * This instrumentation only allows one __schema field or one __type field to be present, and it does not allow the `__Type` fields
 * to form a cycle, i.e., that can only be present once.  This allows the standard and common introspection queries to work
 * so tooling such as graphiql can work.
 */
@PublicApi
public class GoodFaithIntrospection {

    /**
     * Placing a boolean value under this key in the per request {@link GraphQLContext} will enable
     * or disable Good Faith Introspection on that request.
     */
    public static final String GOOD_FAITH_INTROSPECTION_DISABLED = "GOOD_FAITH_INTROSPECTION_DISABLED";

    private static final AtomicBoolean ENABLED_STATE = new AtomicBoolean(true);

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

    private static final Map<FieldCoordinates, Integer> ALLOWED_FIELD_INSTANCES = Map.of(
            coordinates("Query", "__schema"), 1
            , coordinates("Query", "__type"), 1

            , coordinates("__Type", "fields"), 1
            , coordinates("__Type", "inputFields"), 1
            , coordinates("__Type", "interfaces"), 1
            , coordinates("__Type", "possibleTypes"), 1
    );

    public static Optional<ExecutionResult> checkIntrospection(ExecutionContext executionContext) {
        if (isIntrospectionEnabled(executionContext.getGraphQLContext())) {
            ExecutableNormalizedOperation operation = executionContext.getNormalizedQueryTree().get();
            ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToENFs = operation.getCoordinatesToNormalizedFields();
            for (Map.Entry<FieldCoordinates, Integer> entry : ALLOWED_FIELD_INSTANCES.entrySet()) {
                FieldCoordinates coordinates = entry.getKey();
                Integer allowSize = entry.getValue();
                ImmutableList<ExecutableNormalizedField> normalizedFields = coordinatesToENFs.get(coordinates);
                if (normalizedFields.size() > allowSize) {
                    BadFaithIntrospectionError error = new BadFaithIntrospectionError(coordinates.toString());
                    return Optional.of(ExecutionResult.newExecutionResult().addError(error).build());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isIntrospectionEnabled(GraphQLContext graphQlContext) {
        if (!isEnabledJvmWide()) {
            return false;
        }
        return !graphQlContext.getOrDefault(GOOD_FAITH_INTROSPECTION_DISABLED, false);
    }

    public static class BadFaithIntrospectionError implements GraphQLError {
        private final String message;

        public BadFaithIntrospectionError(String qualifiedField) {
            this.message = String.format("This request is not asking for introspection in good faith - %s is present too often!", qualifiedField);
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
        public List<SourceLocation> getLocations() {
            return null;
        }
    }
}
