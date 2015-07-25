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
            addError(new ValidationError(ValidationErrorType.UnknownDirective, directive.getSourceLocation(), null));
            return;
        }

        Node ancestor = ancestors.get(ancestors.size() - 1);
        if (ancestor instanceof OperationDefinition && !graphQLDirective.isOnOperation()) {
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), null));

        }
        if (ancestor instanceof Field && !graphQLDirective.isOnField()) {
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), null));

        }

        if ((ancestor instanceof FragmentSpread
                || ancestor instanceof FragmentDefinition
                || ancestor instanceof InlineFragment)
                && !graphQLDirective.isOnFragment()) {
            addError(new ValidationError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), null));
        }

    }
}
