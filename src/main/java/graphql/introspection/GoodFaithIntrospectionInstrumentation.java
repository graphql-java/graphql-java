package graphql.introspection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.FieldCoordinates;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
public class GoodFaithIntrospectionInstrumentation extends SimplePerformantInstrumentation {

    private static final Map<FieldCoordinates, Integer> ALLOWED_FIELD_INSTANCES = Map.of(
            coordinates("Query", "__schema"), 1
            , coordinates("Query", "__type"), 1

            , coordinates("__Type", "fields"), 1
            , coordinates("__Type", "inputFields"), 1
            , coordinates("__Type", "interfaces"), 1
            , coordinates("__Type", "possibleTypes"), 1
    );

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {

        // We check first to see if there are any introspection fields present on the top level field set
        // if there isn't, then there is nothing to check.  This ensures we are performant.
        List<String> topLevelFieldName = parameters.getExecutionStrategyParameters().getFields().getKeys();
        boolean weHaveIntrospectionFields = false;
        for (String fieldName : topLevelFieldName) {
            if (Introspection.SchemaMetaFieldDef.getName().equals(fieldName) || Introspection.TypeMetaFieldDef.getName().equals(fieldName)) {
                weHaveIntrospectionFields = true;
                break;
            }
        }
        if (weHaveIntrospectionFields) {
            ensureTheyAreInGoodFaith(parameters.getExecutionContext());
        }
        return ExecutionStrategyInstrumentationContext.NOOP;
    }

    private void ensureTheyAreInGoodFaith(ExecutionContext executionContext) {
        ExecutableNormalizedOperation operation = executionContext.getNormalizedQueryTree().get();
        ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToENFs = operation.getCoordinatesToNormalizedFields();
        for (Map.Entry<FieldCoordinates, Integer> entry : ALLOWED_FIELD_INSTANCES.entrySet()) {
            FieldCoordinates coordinates = entry.getKey();
            Integer allowSize = entry.getValue();
            ImmutableList<ExecutableNormalizedField> normalizedFields = coordinatesToENFs.get(coordinates);
            if (normalizedFields.size() > allowSize) {
                throw new BadFaithIntrospectionAbortExecutionException(coordinates.toString());
            }
        }
    }

    @PublicApi
    public static class BadFaithIntrospectionAbortExecutionException extends AbortExecutionException {
        public BadFaithIntrospectionAbortExecutionException(String qualifiedField) {
            super(String.format("This request is not asking for introspection in good faith - %s is present too often!", qualifiedField));
        }
    }
}
