package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;

import static graphql.schema.validation.SchemaValidationErrorType.ObjectDoesNotContainsAnyField;

/**
 * Schema validation rule ensuring an object type has defined any field.
 */
@Internal
public class ObjectTypeWithNoField implements SchemaValidationRule {
    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {
        if (type instanceof GraphQLObjectType) {
            check((GraphQLObjectType) type, validationErrorCollector);
        }
    }

    public void check(GraphQLObjectType type, SchemaValidationErrorCollector validationErrorCollector) {
        if (type.getFieldDefinitions().isEmpty()) {
            String msg = String.format("%s type does not contains any fields", type.getName());
            SchemaValidationError error = new SchemaValidationError(ObjectDoesNotContainsAnyField, msg);
            validationErrorCollector.addError(error);
        }
    }
}
