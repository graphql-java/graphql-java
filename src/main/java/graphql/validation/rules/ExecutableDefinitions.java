package graphql.validation.rules;

import graphql.Internal;
import graphql.language.Definition;
import graphql.language.DirectiveDefinition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.NonExecutableDefinition;

@Internal
public class ExecutableDefinitions extends AbstractRule {

    public ExecutableDefinitions(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /**
     * Executable definitions
     *
     * A GraphQL document is only valid for execution if all definitions are either
     * operation or fragment definitions.
     */
    @Override
    public void checkDocument(Document document) {
        document.getDefinitions().forEach(definition -> {
            if (!(definition instanceof OperationDefinition)
                && !(definition instanceof FragmentDefinition)) {

                String message = nonExecutableDefinitionMessage(definition);
                addError(NonExecutableDefinition, definition.getSourceLocation(), message);
            }
        });
    }

    private String nonExecutableDefinitionMessage(Definition definition) {
        if (definition instanceof TypeDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableType", ((TypeDefinition) definition).getName());
        } else if (definition instanceof SchemaDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableSchema");
        } else if (definition instanceof DirectiveDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableDirective", ((DirectiveDefinition) definition).getName());
        }

        return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableDefinition");
    }
}
