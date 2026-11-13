package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static java.lang.String.format;

/**
 * Spec: if the Input Object is a OneOf Input Object then the type of each input field must be nullable,
 * no input field can have a default value, and the type must be inhabited by a finite value.
 */
@Internal
@NullMarked
public class OneOfInputObjectRules implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;

    public OneOfInputObjectRules(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkInputObjectType(GraphQLInputObjectType inputObjectType) {
        if (!inputObjectType.isOneOf()) {
            return;
        }
        if (!canBeProvidedAFiniteValue(inputObjectType, Collections.newSetFromMap(new IdentityHashMap<>()))) {
            addError(SchemaValidationErrorType.OneOfNotInhabited,
                    format("OneOf Input Object %s must be inhabited but all fields recursively reference only other OneOf Input Objects forming an unresolvable cycle.", inputObjectType.getName()));
        }
    }

    private boolean canBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        if (!visited.add(oneOfInputObject)) {
            return false;
        }
        boolean result = anyFieldCanBeProvidedAFiniteValue(oneOfInputObject, visited);
        visited.remove(oneOfInputObject);
        return result;
    }

    private boolean anyFieldCanBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        for (GraphQLInputObjectField field : oneOfInputObject.getFieldDefinitions()) {
            GraphQLType fieldType = field.getType();
            if (isList(fieldType)) {
                return true;
            }
            GraphQLType namedFieldType = unwrapAll(fieldType);
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                return true;
            }
            GraphQLInputObjectType inputFieldType = (GraphQLInputObjectType) namedFieldType;
            if (!inputFieldType.isOneOf() || canBeProvidedAFiniteValue(inputFieldType, visited)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void checkInputObjectField(GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField) {
        if (!inputObjectType.isOneOf()) {
            return;
        }
        if (inputObjectField.hasSetDefaultValue()) {
            addError(SchemaValidationErrorType.OneOfDefaultValueOnField,
                    format("OneOf input field %s.%s cannot have a default value.", inputObjectType.getName(), inputObjectField.getName()));
        }
        if (isNonNull(inputObjectField.getType())) {
            addError(SchemaValidationErrorType.OneOfNonNullableField,
                    format("OneOf input field %s.%s must be nullable.", inputObjectType.getName(), inputObjectField.getName()));
        }
    }

    private void addError(SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }
}
