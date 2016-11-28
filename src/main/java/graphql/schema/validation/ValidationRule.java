package graphql.schema.validation;

import graphql.schema.GraphQLFieldDefinition;

public interface ValidationRule {

    void check(GraphQLFieldDefinition fieldDef, ValidationErrorCollector validationErrorCollector);
    
}
