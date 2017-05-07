package graphql.schema.validation;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLType;

public interface ValidationRule {

    void check(GraphQLFieldDefinition fieldDef, ValidationErrorCollector validationErrorCollector);

    void check(GraphQLType type, ValidationErrorCollector validationErrorCollector);
}
