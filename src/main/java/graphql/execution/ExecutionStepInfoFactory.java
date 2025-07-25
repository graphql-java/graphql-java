package graphql.execution;

import graphql.Internal;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.util.FpKit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

@Internal
@NullMarked
public class ExecutionStepInfoFactory {

    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, ResultPath indexedPath) {
        GraphQLList fieldType = executionInfo.getUnwrappedNonNullTypeAs();
        GraphQLOutputType typeInList = (GraphQLOutputType) fieldType.getWrappedType();
        return executionInfo.transform(typeInList, executionInfo, indexedPath);
    }

    /**
     * Builds the type info hierarchy for the current field
     *
     * @param executionContext the execution context  in play
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fieldDefinition  the field definition to build type info for
     * @param fieldContainer   the field container
     *
     * @return a new type info
     */
    public ExecutionStepInfo createExecutionStepInfo(ExecutionContext executionContext,
                                                        ExecutionStrategyParameters parameters,
                                                        GraphQLFieldDefinition fieldDefinition,
                                                        @Nullable GraphQLObjectType fieldContainer) {
        MergedField field = parameters.getField();
        ExecutionStepInfo parentStepInfo = parameters.getExecutionStepInfo();
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<GraphQLArgument> fieldArgDefs = fieldDefinition.getArguments();
        Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues = ImmutableMapWithNullValues::emptyMap;
        //
        // no need to create args at all if there are none on the field def
        //
        if (!fieldArgDefs.isEmpty()) {
            argumentValues = getArgumentValues(executionContext, fieldArgDefs, field.getArguments());
        }


        return newExecutionStepInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .fieldContainer(fieldContainer)
                .field(field)
                .path(parameters.getPath())
                .parentInfo(parentStepInfo)
                .arguments(argumentValues)
                .build();
    }

    @NonNull
    private static Supplier<ImmutableMapWithNullValues<String, Object>> getArgumentValues(ExecutionContext executionContext,
                                                                                          List<GraphQLArgument> fieldArgDefs,
                                                                                          List<Argument> fieldArgs) {
        Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues;
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        Supplier<ImmutableMapWithNullValues<String, Object>> argValuesSupplier = () -> {
            Map<String, Object> resolvedValues = ValuesResolver.getArgumentValues(codeRegistry,
                    fieldArgDefs,
                    fieldArgs,
                    executionContext.getCoercedVariables(),
                    executionContext.getGraphQLContext(),
                    executionContext.getLocale());

            return ImmutableMapWithNullValues.copyOf(resolvedValues);
        };
        argumentValues = FpKit.intraThreadMemoize(argValuesSupplier);
        return argumentValues;
    }


}
