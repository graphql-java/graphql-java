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

    // Coordinates already fully traversed without finding a cycle, used to avoid duplicate error reports
    // when the same coordinate is reachable from multiple input object types.
    private final Set<String> fullyExplored = new LinkedHashSet<>();

    // The spec's "visitedFields" set, tracked as coordinate strings ("Type.field").
    // The spec creates a new immutable set at each step; this implementation mutates and backtracks
    // for the same effect.
    private final LinkedHashSet<String> visitedFields = new LinkedHashSet<>();

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);

        // Implements InputObjectDefaultValueHasCycle(inputObject) from the spec:
        // "If defaultValue is not provided, initialize it to an empty unordered map."
        inputObjectDefaultValueHasCycle(type, ObjectValue.newObjectValue().build(), errorCollector);

        return TraversalControl.CONTINUE;
    }

    /**
     * Implements {@code InputObjectDefaultValueHasCycle(inputObject, defaultValue, visitedFields)}
     * from the spec, for literal (AST) default values.
     */
    private void inputObjectDefaultValueHasCycle(
            GraphQLInputObjectType inputObject,
            Value<?> defaultValue,
            SchemaValidationErrorCollector errorCollector
    ) {
        // "If defaultValue is a list: for each itemValue in defaultValue..."
        if (defaultValue instanceof ArrayValue) {
            for (Value<?> itemValue : ((ArrayValue) defaultValue).getValues()) {
                inputObjectDefaultValueHasCycle(inputObject, itemValue, errorCollector);
            }
            return;
        }

        // "Otherwise, if defaultValue is an unordered map..."
        if (!(defaultValue instanceof ObjectValue)) {
            return;
        }

        ObjectValue objectValue = (ObjectValue) defaultValue;
        Map<String, Value<?>> defaultValueMap = new LinkedHashMap<>();
        for (ObjectField field : objectValue.getObjectFields()) {
            defaultValueMap.put(field.getName(), field.getValue());
        }

        // "For each field in inputObject: if InputFieldDefaultValueHasCycle(...)"
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            GraphQLType namedFieldType = unwrapAll(field.getType());
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                continue;
            }

            GraphQLInputObjectType fieldInputObject = (GraphQLInputObjectType) namedFieldType;
            String fieldName = field.getName();
            if (defaultValueMap.containsKey(fieldName)) {
                // "Let fieldDefaultValue be the value for fieldName in defaultValue.
                //  If fieldDefaultValue exists: InputObjectDefaultValueHasCycle(namedFieldType, fieldDefaultValue, visitedFields)"
                inputObjectDefaultValueHasCycle(fieldInputObject, defaultValueMap.get(fieldName), errorCollector);
            } else {
                // "Otherwise: let fieldDefaultValue be the default value of field..."
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName(), errorCollector);
            }
        }
    }

    /**
     * Implements {@code InputObjectDefaultValueHasCycle(inputObject, defaultValue, visitedFields)}
     * from the spec, for external (programmatic Map/List) default values.
     */
    private void inputObjectDefaultValueHasCycle(
            GraphQLInputObjectType inputObject,
            Object defaultValue,
            SchemaValidationErrorCollector errorCollector
    ) {
        // "If defaultValue is a list: for each itemValue in defaultValue..."
        if (defaultValue instanceof Iterable) {
            for (Object itemValue : (Iterable<?>) defaultValue) {
                if (itemValue != null) {
                    inputObjectDefaultValueHasCycle(inputObject, itemValue, errorCollector);
                }
            }
            return;
        }

        // "Otherwise, if defaultValue is an unordered map..."
        if (!(defaultValue instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> defaultValueMap = (Map<String, Object>) defaultValue;

        // "For each field in inputObject: if InputFieldDefaultValueHasCycle(...)"
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            GraphQLType namedFieldType = unwrapAll(field.getType());
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                continue;
            }

            GraphQLInputObjectType fieldInputObject = (GraphQLInputObjectType) namedFieldType;
            String fieldName = field.getName();
            if (defaultValueMap.containsKey(fieldName)) {
                // "Let fieldDefaultValue be the value for fieldName in defaultValue.
                //  If fieldDefaultValue exists: InputObjectDefaultValueHasCycle(namedFieldType, fieldDefaultValue, visitedFields)"
                Object fieldDefaultValue = defaultValueMap.get(fieldName);
                if (fieldDefaultValue != null) {
                    inputObjectDefaultValueHasCycle(fieldInputObject, fieldDefaultValue, errorCollector);
                }
            } else {
                // "Otherwise: let fieldDefaultValue be the default value of field..."
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName(), errorCollector);
            }
        }
    }

    /**
     * Implements the "Otherwise" branch of {@code InputFieldDefaultValueHasCycle(field, defaultValue, visitedFields)}
     * from the spec — called when the field is not present in the parent's default value,
     * so the field's own default will be used at runtime.
     */
    private void inputFieldDefaultValueHasCycle(
            GraphQLInputObjectField field,
            GraphQLInputObjectType namedFieldType,
            String parentTypeName,
            SchemaValidationErrorCollector errorCollector
    ) {
        // "Let fieldDefaultValue be the default value of field.
        //  If fieldDefaultValue does not exist: return false."
        InputValueWithState fieldDefaultValue = field.getInputFieldDefaultValue();
        if (fieldDefaultValue.isNotSet()) {
            return;
        }

        String coordinate = parentTypeName + "." + field.getName();

        // "If field is within visitedFields: return true."
        if (visitedFields.contains(coordinate)) {
            // Cycle found — collect intermediate nodes (everything after the coordinate itself)
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

        // "Let nextVisitedFields be a new set containing field and everything from visitedFields.
        //  Return InputObjectDefaultValueHasCycle(namedFieldType, fieldDefaultValue, nextVisitedFields)."
        visitedFields.add(coordinate);

        if (fieldDefaultValue.isLiteral() && fieldDefaultValue.getValue() instanceof Value) {
            inputObjectDefaultValueHasCycle(namedFieldType, (Value<?>) fieldDefaultValue.getValue(), errorCollector);
        } else if (fieldDefaultValue.isExternal() && fieldDefaultValue.getValue() != null) {
            inputObjectDefaultValueHasCycle(namedFieldType, fieldDefaultValue.getValue(), errorCollector);
        }

        visitedFields.remove(coordinate);
    }
}
