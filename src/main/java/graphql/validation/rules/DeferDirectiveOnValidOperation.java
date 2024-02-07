package graphql.validation.rules;

import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import java.util.List;
import java.util.Optional;

import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static graphql.validation.ValidationErrorType.MisplacedDirective;

/**
 * Defer Directive is Used On Valid Operations
 *
 * A GraphQL document is only valid if defer directives are not used on subscription types.
 */
public class DeferDirectiveOnValidOperation extends AbstractRule {

    public DeferDirectiveOnValidOperation(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        this.setVisitFragmentSpreads(true);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        if (!directive.getName().equals("defer") ||
                (directive.getArgumentsByName().get("if") != null && !((BooleanValue) directive.getArgumentsByName().get("if").getValue()).isValue() )) {
            return;
        }
        // check if the directive is on allowed operation
        Optional<OperationDefinition> operationDefinition = getOperation(ancestors);
        if (operationDefinition.isPresent() &&  operationDefinition.get().getOperation() == SUBSCRIPTION) {
            String message = i18n(MisplacedDirective, "DirectiveMisplaced.notAllowedOperation", directive.getName(), SUBSCRIPTION.name().toLowerCase());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    /**
     * Extract from ancestors the OperationDefinition using the document ancestor.
     * @param ancestors list of ancestors
     * @return OperationDefinition
     */
    private Optional<OperationDefinition> getOperation(List<Node> ancestors) {
        return ancestors.stream()
                .filter(doc -> doc instanceof OperationDefinition)
                .map((def -> (OperationDefinition) def))
                .findFirst();
    }

}

