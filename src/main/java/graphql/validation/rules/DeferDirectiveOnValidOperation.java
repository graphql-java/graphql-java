package graphql.validation.rules;

import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.VariableReference;
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
 *
 * See proposed spec:<a href="https://github.com/graphql/graphql-spec/pull/742">spec/Section 5 -- Validation.md ### Defer And Stream Directives Are Used On Valid Operations</a>
 *
 */
@ExperimentalApi
public class DeferDirectiveOnValidOperation extends AbstractRule {
    public DeferDirectiveOnValidOperation(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        this.setVisitFragmentSpreads(true);
    }


    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        // ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT must be true
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT)) {
            return;
        }

        if (!Directives.DeferDirective.getName().equals(directive.getName())) {
            return;
        }
        // check if the directive is on allowed operation
        Optional<OperationDefinition> operationDefinition = getOperationDefinition(ancestors);
        if (operationDefinition.isPresent() &&
                SUBSCRIPTION.equals(operationDefinition.get().getOperation()) &&
                !ifArgumentMightBeFalse(directive) ){
            String message = i18n(MisplacedDirective, "IncrementalDirective.notAllowedSubscriptionOperation", directive.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    /**
     * Extract from ancestors the OperationDefinition using the document ancestor.
     * @param ancestors list of ancestors
     * @return Optional of OperationDefinition
     */
    private Optional<OperationDefinition> getOperationDefinition(List<Node> ancestors) {
        return ancestors.stream()
                .filter(doc -> doc instanceof OperationDefinition)
                .map((def -> (OperationDefinition) def))
                .findFirst();
    }

    private Boolean ifArgumentMightBeFalse(Directive directive) {
        Argument ifArgument = directive.getArgumentsByName().get("if");
        if (ifArgument == null) {
            return false;
        }
        if(ifArgument.getValue() instanceof BooleanValue){
            return !((BooleanValue) ifArgument.getValue()).isValue();
        }
        if(ifArgument.getValue() instanceof VariableReference){
            return true;
        }
        return false;
    }

}

