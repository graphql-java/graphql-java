package graphql.schema.validation.rules;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DirectiveRule implements SchemaValidationRule {

    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLDirective> directives = schema.getDirectives();
        if (directives == null || directives.isEmpty()) {
            return;
        }

        Set<String> directivesName = new HashSet<>();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            if (directiveName.length() >= 2 && directiveName.startsWith("__")) {
                SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                        String.format("Directive \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }


            if (directivesName.contains(directiveName)) {
                SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                        String.format("All directives within a GraphQL schema must have unique names. directive named  \"%s\" is already define.", directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }
            directivesName.add(directiveName);
        }
    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {

    }

}