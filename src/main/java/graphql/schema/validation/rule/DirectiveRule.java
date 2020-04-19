package graphql.schema.validation.rule;

import graphql.language.Directive;
import graphql.language.InputValueDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The validation about directive.
 * <p>
 * details in https://spec.graphql.org/June2018/#sec-Type-System.Directives
 */
public class DirectiveRule implements SchemaValidationRule {

    @Override
    public void apply(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLDirective> directives = schema.getDirectives();
        if (directives == null || directives.isEmpty()) {
            return;
        }

        Set<String> directivesName = new HashSet<>();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            //invalide name start with "__"
            if (directiveName.length() >= 2 && directiveName.startsWith("__")) {
                SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                        String.format("Directive \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }

            //duplicate directive name. This rule can be removed, because duplicate directive name has already been verified.
            if (directivesName.contains(directiveName)) {
                SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                        String.format("All directives within a GraphQL schema must have unique names. directive named  \"%s\" is already define.", directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }
            directivesName.add(directiveName);

            if(isRefereneIeself(directive)){
                SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                        String.format("Directive \"%s\" must not reference itself directly or indirectly.", directiveName));
                validationErrorCollector.addError(schemaValidationError);
            }
        }
    }

    //
    //https://spec.graphql.org/June2018/#sec-Type-System.Directives
    //
    //While defining a directive, it must not reference itself directly or indirectly:
    //directive @invalidExample(arg: String @invalidExample) on ARGUMENT_DEFINITION
    //
    private boolean isRefereneIeself(GraphQLDirective directive) {
        List<GraphQLArgument> arguments = directive.getArguments();
        if (arguments == null || arguments.size() == 0) {
            return false;
        }

        for (GraphQLArgument argument : arguments) {
            InputValueDefinition argumentDefine = argument.getDefinition();
            if(argumentDefine==null){
                return false;
            }

            List<Directive> argumentDefineDirectives = argumentDefine.getDirectives();
            if(argumentDefineDirectives==null){
                return false;
            }

            for (Directive argumentDefineDirective : argumentDefineDirectives) {
                if(directive.getName().equals(argumentDefineDirective.getName())){
                    return true;
                }
            }
        }

        return false;
    }
}