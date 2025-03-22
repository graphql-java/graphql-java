package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.execution.directives.QueryAppliedDirective;
import graphql.execution.directives.QueryAppliedDirectiveArgument;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.collect.ImmutableKit.map;
import static graphql.language.Argument.newArgument;

/**
 * This class is a peer class and broken out of {@link ExecutableNormalizedOperationToAstCompiler} to deal with
 * argument value making.
 */
@Internal
class ArgumentMaker {

    static List<Argument> createArguments(ExecutableNormalizedField executableNormalizedField,
                                          VariableAccumulator variableAccumulator) {
        ImmutableList.Builder<Argument> result = ImmutableList.builder();
        ImmutableMap<String, NormalizedInputValue> normalizedArguments = executableNormalizedField.getNormalizedArguments();
        for (String argName : normalizedArguments.keySet()) {
            NormalizedInputValue normalizedInputValue = normalizedArguments.get(argName);
            Value<?> value = argValue(executableNormalizedField, null, argName, normalizedInputValue, variableAccumulator);
            Argument argument = newArgument()
                    .name(argName)
                    .value(value)
                    .build();
            result.add(argument);
        }
        return result.build();
    }

    static List<Argument> createDirectiveArguments(ExecutableNormalizedField executableNormalizedField,
                                                   QueryDirectives queryDirectives,
                                                   QueryAppliedDirective queryAppliedDirective,
                                                   VariableAccumulator variableAccumulator) {

        Map<String, NormalizedInputValue> argValueMap = queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives().getOrDefault(queryAppliedDirective, emptyMap());

        ImmutableList.Builder<Argument> result = ImmutableList.builder();
        for (QueryAppliedDirectiveArgument directiveArgument : queryAppliedDirective.getArguments()) {
            String argName = directiveArgument.getName();
            if (argValueMap != null && argValueMap.containsKey(argName)) {
                NormalizedInputValue normalizedInputValue = argValueMap.get(argName);
                Value<?> value = argValue(executableNormalizedField, queryAppliedDirective, argName, normalizedInputValue, variableAccumulator);
                Argument argument = newArgument()
                        .name(argName)
                        .value(value)
                        .build();
                result.add(argument);
            }
        }
        return result.build();
    }

    @SuppressWarnings("unchecked")
    private static Value<?> argValue(ExecutableNormalizedField executableNormalizedField,
                                     QueryAppliedDirective queryAppliedDirective,
                                     String argName,
                                     @Nullable Object value,
                                     VariableAccumulator variableAccumulator) {
        if (value instanceof List) {
            ArrayValue.Builder arrayValue = ArrayValue.newArrayValue();
            arrayValue.values(map((List<Object>) value, val -> argValue(executableNormalizedField, queryAppliedDirective, argName, val, variableAccumulator)));
            return arrayValue.build();
        }
        if (value instanceof Map) {
            ObjectValue.Builder objectValue = ObjectValue.newObjectValue();
            Map<String, Object> map = (Map<String, Object>) value;
            for (String fieldName : map.keySet()) {
                Value<?> fieldValue = argValue(executableNormalizedField, queryAppliedDirective, argName, (NormalizedInputValue) map.get(fieldName), variableAccumulator);
                objectValue.objectField(ObjectField.newObjectField().name(fieldName).value(fieldValue).build());
            }
            return objectValue.build();
        }
        if (value == null) {
            return NullValue.newNullValue().build();
        }
        return (Value<?>) value;
    }

    @NonNull
    private static Value<?> argValue(ExecutableNormalizedField executableNormalizedField,
                                     QueryAppliedDirective queryAppliedDirective,
                                     String argName,
                                     NormalizedInputValue normalizedInputValue,
                                     VariableAccumulator variableAccumulator) {
        if (variableAccumulator.shouldMakeVariable(executableNormalizedField, queryAppliedDirective, argName, normalizedInputValue)) {
            VariableValueWithDefinition variableWithDefinition = variableAccumulator.accumulateVariable(normalizedInputValue);
            return variableWithDefinition.getVariableReference();
        } else {
            return argValue(executableNormalizedField, queryAppliedDirective, argName, normalizedInputValue.getValue(), variableAccumulator);
        }
    }
}
