package graphql.validation.rules;


import graphql.language.*;
import graphql.schema.GraphQLDirective;
import graphql.validation.*;

import java.util.List;

public class KnownDirectives extends AbstractRule {


    public KnownDirectives(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        GraphQLDirective graphQLDirective = getValidationContext().getSchema().getDirective(directive.getName());
        if (graphQLDirective == null) {
            String message = String.format("Unknown directive %s", directive.getName());
            addError(new ValidationError(ValidationErrorType.UnknownDirective, directive.getSourceLocation(), message));
            return;
        }

        Node ancestor = ancestors.get(ancestors.size() - 1);
        if (ancestor instanceof OperationDefinition && !graphQLDirective.isOnOperation()) {
            String message = String.format("Directive %s not allowed here", directive.getName());
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), message));

        }
        if (ancestor instanceof Field && !graphQLDirective.isOnField()) {
            String message = String.format("Directive %s not allowed here", directive.getName());
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), message));

        }

        if ((ancestor instanceof FragmentSpread
                || ancestor instanceof FragmentDefinition
                || ancestor instanceof InlineFragment)
                && !graphQLDirective.isOnFragment()) {
            String message = String.format("Directive %s not allowed here", directive.getName());
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), message));
        }

    }
}
