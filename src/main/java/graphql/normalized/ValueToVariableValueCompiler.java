package graphql.normalized;

import graphql.AssertException;
import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.map;
import static java.util.stream.Collectors.toList;

@Internal
public class ValueToVariableValueCompiler {

    static VariableValueWithDefinition normalizedInputValueToVariable(NormalizedInputValue normalizedInputValue, int queryVariableCount) {
        Object variableValue = normalisedValueToVariableValue(normalizedInputValue);
        String varName = getVarName(queryVariableCount);
        return new VariableValueWithDefinition(
                variableValue,
                VariableDefinition.newVariableDefinition()
                        .name(varName)
                        .type(TypeName.newTypeName(normalizedInputValue.getTypeName()).build())
                        .build(),
                VariableReference.newVariableReference().name(varName).build());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Object normalisedValueToVariableValue(Object maybeValue) {
        Object variableValue;
        if (maybeValue instanceof NormalizedInputValue) {
            NormalizedInputValue normalizedInputValue = (NormalizedInputValue) maybeValue;
            Object inputValue = normalizedInputValue.getValue();
            if (inputValue instanceof Value) {
                variableValue = toVariableValue((Value<?>) inputValue);
            } else if (inputValue instanceof List) {
                variableValue = normalisedValueToVariableValues((List<Object>) inputValue);
            } else if (inputValue instanceof Map) {
                variableValue = normalisedValueToVariableValues((Map<String, Object>) inputValue);
            } else {
                throw new AssertException("Should never happen. Did not expect NormalizedInputValue.getValue() of type: " + inputValue.getClass());
            }
        } else if (maybeValue instanceof Value) {
            Value<?> value = (Value<?>) maybeValue;
            variableValue = toVariableValue(value);
        } else if (maybeValue instanceof List) {
            variableValue = normalisedValueToVariableValues((List<Object>) maybeValue);
        } else if (maybeValue instanceof Map) {
            variableValue = normalisedValueToVariableValues((Map<String, Object>) maybeValue);
        } else {
            throw new AssertException("Should never happen. Did not expect type: " + maybeValue.getClass());
        }
        return variableValue;
    }

    private static List<Object> normalisedValueToVariableValues(List<Object> arrayValues) {
        return map(arrayValues, ValueToVariableValueCompiler::normalisedValueToVariableValue);
    }

    @NotNull
    private static Map<String, Object> normalisedValueToVariableValues(Map<String, Object> objectMap) {
        Map<String, Object> output = new LinkedHashMap<>();
        objectMap.forEach((k, v) -> {
            Object value = normalisedValueToVariableValue(v);
            output.put(k, value);
        });
        return output;
    }

    private static Map<String, Object> toVariableValue(ObjectValue objectValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<ObjectField> objectFields = objectValue.getObjectFields();
        for (ObjectField objectField : objectFields) {
            String objectFieldName = objectField.getName();
            Value<?> objectFieldValue = objectField.getValue();
            map.put(objectFieldName, toVariableValue(objectFieldValue));
        }
        return map;
    }

    @NotNull
    private static List<Object> toVariableValues(List<Value> arrayValues) {
        // some values can be null (NullValue) and hence we can use Immutable Lists
        return arrayValues.stream()
                .map(ValueToVariableValueCompiler::toVariableValue)
                .collect(toList());
    }

    @Nullable
    private static Object toVariableValue(Value<?> value) {
        if (value instanceof ObjectValue) {
            return toVariableValue((ObjectValue) value);
        } else if (value instanceof ArrayValue) {
            return toVariableValues(((ArrayValue) value).getValues());
        } else if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        } else if (value instanceof FloatValue) {
            return ((FloatValue) value).getValue();
        } else if (value instanceof IntValue) {
            return ((IntValue) value).getValue();
        } else if (value instanceof BooleanValue) {
            return ((BooleanValue) value).isValue();
        } else if (value instanceof NullValue) {
            return null;
        }
        throw new AssertException("Should never happen. Cannot handle node of type: " + value.getClass());
    }

    private static String getVarName(int variableOrdinal) {
        return "v" + variableOrdinal;
    }

}
