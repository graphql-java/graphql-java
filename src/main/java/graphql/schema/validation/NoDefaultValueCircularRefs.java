package graphql.schema.validation;

import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Ensures that input object field default values do not form circular references.
 * <br>
 * For example, consider this type configuration:
 * <code>
 *     input A { b:B = {} }
 *     input B { a:A = {} }
 * </code>
 * <br>
 * The default values used in these types form a cycle that can create an infinitely large
 * value. This validator rejects default values that can create these kinds of cycles.
 * <br>
 * This validator is equivalent to graphql-js v17's
 * {@code createInputObjectDefaultValueCircularRefsValidator}
 */
@Internal
public class NoDefaultValueCircularRefs extends GraphQLTypeVisitorStub {

    // Coordinates already fully traversed without finding a cycle, used to avoid duplicate error reports
    // when the same coordinate is reachable from multiple input object types.
    private final Set<String> fullyExplored = new LinkedHashSet<>();

    // The current traversal path as an insertion-ordered set of coordinate strings ("Type.field").
    private final LinkedHashSet<String> traversalPath = new LinkedHashSet<>();

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);

        // Start with an empty object as a way to visit every field in this input
        // object type and apply every default value.
        checkLiteralDefaultValueCycle(type, ObjectValue.newObjectValue().build(), errorCollector);

        return TraversalControl.CONTINUE;
    }

    /** traverse a Value literal and check for cycles */
    private void checkLiteralDefaultValueCycle(
            GraphQLInputObjectType inputObj,
            Value<?> defaultValue,
            SchemaValidationErrorCollector errorCollector
    ) {
        if (defaultValue instanceof ArrayValue) {
            for (Value<?> itemValue : ((ArrayValue) defaultValue).getValues()) {
                checkLiteralDefaultValueCycle(inputObj, itemValue, errorCollector);
            }
            return;
        }

        if (!(defaultValue instanceof ObjectValue)) {
            return;
        }

        ObjectValue objectValue = (ObjectValue) defaultValue;
        Map<String, Value<?>> fieldValues = new LinkedHashMap<>();
        for (ObjectField field : objectValue.getObjectFields()) {
            fieldValues.put(field.getName(), field.getValue());
        }

        for (GraphQLInputObjectField field : inputObj.getFieldDefinitions()) {
            GraphQLType namedFieldType = unwrapAll(field.getType());
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                continue;
            }

            GraphQLInputObjectType fieldInputType = (GraphQLInputObjectType) namedFieldType;
            if (fieldValues.containsKey(field.getName())) {
                // Field is explicitly provided -- check the provided value
                checkLiteralDefaultValueCycle(fieldInputType, fieldValues.get(field.getName()), errorCollector);
            } else {
                // Field is not provided -- check its default value
                checkFieldDefaultValueCycle(field, fieldInputType, inputObj.getName(), errorCollector);
            }
        }
    }

    /** traverse an external value and check for cycles */
    private void checkExternalDefaultValueCycle(
            GraphQLInputObjectType inputObj,
            Object defaultValue,
            SchemaValidationErrorCollector errorCollector
    ) {
        if (defaultValue instanceof Iterable) {
            for (Object itemValue : (Iterable<?>) defaultValue) {
                if (itemValue != null) {
                    checkExternalDefaultValueCycle(inputObj, itemValue, errorCollector);
                }
            }
            return;
        }

        if (!(defaultValue instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mapValue = (Map<String, Object>) defaultValue;

        for (GraphQLInputObjectField field : inputObj.getFieldDefinitions()) {
            GraphQLType namedFieldType = unwrapAll(field.getType());
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                continue;
            }

            GraphQLInputObjectType fieldInputType = (GraphQLInputObjectType) namedFieldType;
            if (mapValue.containsKey(field.getName())) {
                Object value = mapValue.get(field.getName());
                if (value != null) {
                    checkExternalDefaultValueCycle(fieldInputType, value, errorCollector);
                }
            } else {
                checkFieldDefaultValueCycle(field, fieldInputType, inputObj.getName(), errorCollector);
            }
        }
    }

    /** Check if a field's default value creates a cycle. */
    private void checkFieldDefaultValueCycle(
            GraphQLInputObjectField field,
            GraphQLInputObjectType fieldType,
            String parentTypeName,
            SchemaValidationErrorCollector errorCollector
    ) {
        InputValueWithState defaultInput = field.getInputFieldDefaultValue();
        if (defaultInput.isNotSet()) {
            return;
        }

        String coordinate = parentTypeName + "." + field.getName();

        if (traversalPath.contains(coordinate)) {
            // Cycle found — collect intermediate nodes (everything after the coordinate itself)
            List<String> intermediaries = new ArrayList<>();
            boolean found = false;
            for (String entry : traversalPath) {
                if (found) {
                    intermediaries.add(entry);
                }
                if (entry.equals(coordinate)) {
                    found = true;
                }
            }

            String message;
            if (intermediaries.isEmpty()) {
                message = "Invalid circular reference. The default value of Input Object field "
                        + coordinate + " references itself.";
            } else {
                message = "Invalid circular reference. The default value of Input Object field "
                        + coordinate + " references itself via the default values of: "
                        + String.join(", ", intermediaries) + ".";
            }

            errorCollector.addError(new SchemaValidationError(
                    SchemaValidationErrorType.DefaultValueCircularRef, message));
            return;
        }

        if (fullyExplored.contains(coordinate)) {
            return;
        }
        fullyExplored.add(coordinate);

        traversalPath.add(coordinate);

        if (defaultInput.isLiteral() && defaultInput.getValue() instanceof Value) {
            checkLiteralDefaultValueCycle(fieldType, (Value<?>) defaultInput.getValue(), errorCollector);
        } else if (defaultInput.isExternal() && defaultInput.getValue() != null) {
            checkExternalDefaultValueCycle(fieldType, defaultInput.getValue(), errorCollector);
        }

        traversalPath.remove(coordinate);
    }
}
