package graphql.validation.rules;

import graphql.Internal;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.StringKit;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.UnknownOperation;

/**
 * Unique variable names
 * <p>
 * A GraphQL operation is only valid if all its variables are uniquely named.
 */
@Internal
public class KnownOperationTypes extends AbstractRule {

    public KnownOperationTypes(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        OperationDefinition.Operation documentOperation = operationDefinition.getOperation();
        GraphQLSchema graphQLSchema = getValidationContext().getSchema();
        if (documentOperation == OperationDefinition.Operation.MUTATION
                && graphQLSchema.getMutationType() == null) {
            String message = i18n(UnknownOperation, "KnownOperationTypes.noOperation", formatOperation(documentOperation));
            addError(UnknownOperation, operationDefinition.getSourceLocation(), message);
        } else if (documentOperation == OperationDefinition.Operation.SUBSCRIPTION
                && graphQLSchema.getSubscriptionType() == null) {
            String message = i18n(UnknownOperation, "KnownOperationTypes.noOperation", formatOperation(documentOperation));
            addError(UnknownOperation, operationDefinition.getSourceLocation(), message);
        } else if (documentOperation == OperationDefinition.Operation.QUERY
                && graphQLSchema.getQueryType() == null) {
            // This is unlikely to happen, as a validated GraphQLSchema must have a Query type by definition
            String message = i18n(UnknownOperation, "KnownOperationTypes.noOperation", formatOperation(documentOperation));
            addError(UnknownOperation, operationDefinition.getSourceLocation(), message);
        }
    }

    private String formatOperation(OperationDefinition.Operation operation) {
        return StringKit.capitalize(operation.name().toLowerCase());
    }
}
