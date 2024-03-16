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

import java.util.LinkedHashMap;
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
    private final Map<FieldCoordinates, Integer> allowFieldInstances;

    public GoodFaithIntrospectionInstrumentation() {
        this(ALLOWED_FIELD_INSTANCES);
    }

    private GoodFaithIntrospectionInstrumentation(Map<FieldCoordinates, Integer> allowFieldInstances) {
        this.allowFieldInstances = allowFieldInstances;
    }

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
        for (Map.Entry<FieldCoordinates, Integer> entry : allowFieldInstances.entrySet()) {
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

    public static Builder newGoodFaithIntrospection() {
        return new Builder();
    }

    public static class Builder {


        private final Map<FieldCoordinates, Integer> allowFieldInstances = new LinkedHashMap<>(ALLOWED_FIELD_INSTANCES);

        /**
         * This allows you to set how many <code>__type(name : "x)</code>  field instances are allowed in an introspection query
         *
         * @param maxInstances the number allowed
         *
         * @return this builder
         */
        public Builder maximumUnderscoreTypeInstances(int maxInstances) {
            allowFieldInstances.put(coordinates("Query", "__type"), maxInstances);
            return this;
        }

        /**
         * This allows you to set how many <code>__schema</code> field instances are allowed in an introspection query
         *
         * @param maxInstances the number allowed
         *
         * @return this builder
         */
        public Builder maximumUnderscoreSchemaInstances(int maxInstances) {
            allowFieldInstances.put(coordinates("Query", "__schema"), maxInstances);
            return this;
        }

        /**
         * This allows you to set how many qualified field instances are allowed in an introspection query
         *
         * @param coordinates  - the qualified field name
         * @param maxInstances the number allowed
         *
         * @return this builder
         */
        public Builder maximumFieldInstances(FieldCoordinates coordinates, int maxInstances) {
            allowFieldInstances.put(coordinates, maxInstances);
            return this;
        }

        public GoodFaithIntrospectionInstrumentation build() {
            return new GoodFaithIntrospectionInstrumentation(allowFieldInstances);
        }
    }
}
