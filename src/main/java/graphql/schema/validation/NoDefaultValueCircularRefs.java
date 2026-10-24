package graphql.schema.validation;

import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
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
public class NoDefaultValueCircularRefs extends GraphQLTypeVisitorStub {

    private final Set<String> checkedFields = new LinkedHashSet<>();
    private final LinkedHashSet<String> fieldPath = new LinkedHashSet<>();

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        checkType(type, getErrorCollector(context));
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        GraphQLType namedType = unwrapAll(argument.getType());
        if (namedType instanceof GraphQLInputObjectType) {
            checkType((GraphQLInputObjectType) namedType, getErrorCollector(context));
        }
        return TraversalControl.CONTINUE;
    }

    private void checkType(GraphQLInputObjectType type, SchemaValidationErrorCollector errorCollector) {
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            GraphQLInputObjectType fieldType = getInputObjectType(field);
            if (fieldType == null) {
                continue;
            }
            checkFieldDefaultValue(field, fieldType, type.getName(), errorCollector);
        }
    }

    private void checkValue(
            GraphQLInputObjectType inputObject,
            @Nullable Object value,
            SchemaValidationErrorCollector errorCollector
    ) {
        if (value == null) {
            return;
        }
        if (value instanceof ArrayValue) {
            for (Value<?> itemValue : ((ArrayValue) value).getValues()) {
                checkValue(inputObject, itemValue, errorCollector);
            }
            return;
        }
        if (FpKit.isIterable(value)) {
            for (Object itemValue : FpKit.toIterable(value)) {
                checkValue(inputObject, itemValue, errorCollector);
            }
            return;
        }

        Map<?, ?> valueMap = getValueMap(value);
        if (valueMap == null) {
            return;
        }
        checkObjectValue(inputObject, valueMap, errorCollector);
    }

    private void checkObjectValue(
            GraphQLInputObjectType inputObject,
            Map<?, ?> valueMap,
            SchemaValidationErrorCollector errorCollector
    ) {
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            boolean hasValue = valueMap.containsKey(field.getName());
            if (!hasValue && field.getInputFieldDefaultValue().isNotSet()) {
                continue;
            }

            GraphQLInputObjectType fieldType = getInputObjectType(field);
            if (fieldType == null) {
                continue;
            }
            if (hasValue) {
                checkValue(fieldType, valueMap.get(field.getName()), errorCollector);
                continue;
            }
            checkFieldDefaultValue(field, fieldType, inputObject.getName(), errorCollector);
        }
    }

    private void checkFieldDefaultValue(
            GraphQLInputObjectField field,
            GraphQLInputObjectType fieldType,
            String parentTypeName,
            SchemaValidationErrorCollector errorCollector
    ) {
        InputValueWithState defaultValue = field.getInputFieldDefaultValue();
        if (!defaultValue.isLiteral() && !defaultValue.isExternal()) {
            return;
        }

        String coordinate = parentTypeName + "." + field.getName();
        if (fieldPath.contains(coordinate)) {
            addError(coordinate, errorCollector);
            return;
        }
        if (!checkedFields.add(coordinate)) {
            return;
        }

        fieldPath.add(coordinate);
        checkValue(fieldType, defaultValue.getValue(), errorCollector);
        fieldPath.remove(coordinate);
    }

    private void addError(String coordinate, SchemaValidationErrorCollector errorCollector) {
        List<String> path = new ArrayList<>(fieldPath);
        List<String> intermediaries = path.subList(path.indexOf(coordinate) + 1, path.size());
        String via = intermediaries.isEmpty()
                ? ""
                : " via the default values of: " + String.join(", ", intermediaries);
        String message = "Invalid circular reference. The default value of Input Object field "
                + coordinate + " references itself" + via + ".";
        errorCollector.addError(new SchemaValidationError(
                SchemaValidationErrorType.DefaultValueCircularRef, message));
    }

    private @Nullable GraphQLInputObjectType getInputObjectType(GraphQLInputObjectField field) {
        GraphQLType type = unwrapAll(field.getType());
        if (type instanceof GraphQLInputObjectType) {
            return (GraphQLInputObjectType) type;
        }
        return null;
    }

    private @Nullable Map<?, ?> getValueMap(Object value) {
        if (value instanceof Map) {
            return (Map<?, ?>) value;
        }
        if (!(value instanceof ObjectValue)) {
            return null;
        }

        Map<String, Value<?>> valueMap = new LinkedHashMap<>();
        for (ObjectField field : ((ObjectValue) value).getObjectFields()) {
            valueMap.put(field.getName(), field.getValue());
        }
        return valueMap;
    }

    private SchemaValidationErrorCollector getErrorCollector(TraverserContext<GraphQLSchemaElement> context) {
        return context.getVarFromParents(SchemaValidationErrorCollector.class);
    }
}
