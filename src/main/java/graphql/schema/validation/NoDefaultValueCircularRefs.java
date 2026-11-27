package graphql.schema.validation;

import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Validates that {@code InputObjectDefaultValueHasCycle(inputObject)} is {@code false}
 * for every input object type, as required by the Input Object type validation rules
 * in the GraphQL specification.
 * <br>
 * For example, consider this type configuration:
 * <code>
 *     input A { b:B = {} }
 *     input B { a:A = {} }
 * </code>
 * <br>
 * The default values used in these types form a cycle that can create an infinitely large
 * value. This validator rejects default values that can create these kinds of cycles.
 *
 * @see <a href="https://spec.graphql.org/draft/#sec-Input-Objects.Type-Validation">Input Objects Type Validation</a>
 */
@Internal
@NullMarked
public class NoDefaultValueCircularRefs implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;

    // coordinates already explored, so each cycle is reported only once
    private final Set<String> fullyExplored = new LinkedHashSet<>();

    // the spec's "visitedFields" set, mutated and backtracked instead of copied at each step
    private final LinkedHashSet<String> visitedFields = new LinkedHashSet<>();

    public NoDefaultValueCircularRefs(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkInputObjectType(GraphQLInputObjectType inputObjectType) {
        // equivalent to InputObjectDefaultValueHasCycle(type, {}) without building the empty map
        for (GraphQLInputObjectField field : inputObjectType.getFieldDefinitions()) {
            GraphQLInputObjectType fieldInputObject = inputObjectTypeWithDefault(field, false);
            if (fieldInputObject != null) {
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObjectType.getName());
            }
        }
    }

    private void literalDefaultValueHasCycle(GraphQLInputObjectType inputObject, @Nullable Value<?> defaultValue) {
        if (defaultValue instanceof ArrayValue) {
            for (Value<?> itemValue : ((ArrayValue) defaultValue).getValues()) {
                literalDefaultValueHasCycle(inputObject, itemValue);
            }
            return;
        }
        if (!(defaultValue instanceof ObjectValue)) {
            return;
        }
        Map<String, Value<?>> defaultValueMap = new LinkedHashMap<>();
        for (ObjectField field : ((ObjectValue) defaultValue).getObjectFields()) {
            defaultValueMap.put(field.getName(), field.getValue());
        }
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            boolean hasDefaultValue = defaultValueMap.containsKey(field.getName());
            GraphQLInputObjectType fieldInputObject = inputObjectTypeWithDefault(field, hasDefaultValue);
            if (fieldInputObject == null) {
                continue;
            }
            if (hasDefaultValue) {
                literalDefaultValueHasCycle(fieldInputObject, defaultValueMap.get(field.getName()));
            } else {
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName());
            }
        }
    }

    private void externalDefaultValueHasCycle(GraphQLInputObjectType inputObject, Object defaultValue) {
        if (defaultValue instanceof Iterable) {
            externalListDefaultValueHasCycle(inputObject, (Iterable<?>) defaultValue);
            return;
        }
        if (!(defaultValue instanceof Map)) {
            return;
        }
        Map<?, ?> defaultValueMap = (Map<?, ?>) defaultValue;
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            boolean hasDefaultValue = defaultValueMap.containsKey(field.getName());
            GraphQLInputObjectType fieldInputObject = inputObjectTypeWithDefault(field, hasDefaultValue);
            if (fieldInputObject == null) {
                continue;
            }
            if (!hasDefaultValue) {
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName());
                continue;
            }
            Object fieldDefaultValue = defaultValueMap.get(field.getName());
            if (fieldDefaultValue != null) {
                externalDefaultValueHasCycle(fieldInputObject, fieldDefaultValue);
            }
        }
    }

    private void externalListDefaultValueHasCycle(GraphQLInputObjectType inputObject, Iterable<?> defaultValues) {
        for (Object itemValue : defaultValues) {
            if (itemValue != null) {
                externalDefaultValueHasCycle(inputObject, itemValue);
            }
        }
    }

    private @Nullable GraphQLInputObjectType inputObjectTypeWithDefault(GraphQLInputObjectField field, boolean hasDefaultInParent) {
        if (!hasDefaultInParent && field.getInputFieldDefaultValue().isNotSet()) {
            return null;
        }
        GraphQLType namedFieldType = unwrapAll(field.getType());
        return namedFieldType instanceof GraphQLInputObjectType ? (GraphQLInputObjectType) namedFieldType : null;
    }

    private void inputFieldDefaultValueHasCycle(GraphQLInputObjectField field, GraphQLInputObjectType namedFieldType, String parentTypeName) {
        InputValueWithState fieldDefaultValue = field.getInputFieldDefaultValue();
        String coordinate = parentTypeName + "." + field.getName();
        if (visitedFields.contains(coordinate)) {
            errors.addError(new SchemaValidationError(SchemaValidationErrorType.DefaultValueCircularRef, cycleMessage(coordinate)));
            return;
        }
        if (!fullyExplored.add(coordinate)) {
            return;
        }
        visitedFields.add(coordinate);
        if (fieldDefaultValue.isLiteral() && fieldDefaultValue.getValue() instanceof Value) {
            literalDefaultValueHasCycle(namedFieldType, (Value<?>) fieldDefaultValue.getValue());
        } else if (fieldDefaultValue.isExternal() && fieldDefaultValue.getValue() != null) {
            externalDefaultValueHasCycle(namedFieldType, fieldDefaultValue.getValue());
        }
        visitedFields.remove(coordinate);
    }

    private String cycleMessage(String coordinate) {
        List<String> intermediaries = new ArrayList<>();
        boolean found = false;
        for (String entry : visitedFields) {
            if (found) {
                intermediaries.add(entry);
            }
            if (entry.equals(coordinate)) {
                found = true;
            }
        }
        if (intermediaries.isEmpty()) {
            return "Invalid circular reference. The default value of Input Object field "
                    + coordinate + " references itself.";
        }
        return "Invalid circular reference. The default value of Input Object field "
                + coordinate + " references itself via the default values of: "
                + String.join(", ", intermediaries) + ".";
    }
}
