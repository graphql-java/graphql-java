package graphql.schema.validation.rules;

import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;

public interface SchemaValidationRule {
    void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector);
}