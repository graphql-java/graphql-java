package graphql.schema.validation.rule;

import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLEnumType;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The SchemaValidationRule interface is used validate the type system.
 * <p>
 * See https://spec.graphql.org/June2018/#sec-Type-System
 */
public interface SchemaValidationRule {

    void apply(GraphQLSchema graphQLSchema, SchemaValidationErrorCollector validationErrorCollector);

}
