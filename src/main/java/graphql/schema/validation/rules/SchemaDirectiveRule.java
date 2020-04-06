package graphql.schema.validation.rules;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidationErrorCollector;
import graphql.schema.validation.SchemaValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SchemaDirectiveRule implements SchemaValidationRule {
    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(List<GraphQLDirective> directives, SchemaValidationErrorCollector validationErrorCollector) {
        if(directives==null||directives.isEmpty()){
            return;
        }

        Set<String> directivesName=new HashSet<>();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            if(directiveName.length()>=2&&directiveName.startsWith("__")){
                SchemaValidationError schemaValidationError= new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalideError,
                        String.format("Directive \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.",directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }


            if(directivesName.contains(directiveName)){
                SchemaValidationError schemaValidationError= new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalideError,
                        String.format("All directives within a GraphQL schema must have unique names. directive named  \"%s\" is already define.",directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }
            directivesName.add(directiveName);
        }

    }

    @Override
    public void check(GraphQLObjectType rootType, SchemaValidationErrorCollector validationErrorCollector) {

    }
}