package graphql.validation.rules;

import graphql.language.Directive;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static graphql.validation.ValidationErrorType.MisplacedDirective;

public class DeferDirectiveOnRootLevel extends AbstractRule {

    public DeferDirectiveOnRootLevel(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        this.setVisitFragmentSpreads(true);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        if (directive.getName().equals("defer")){
            Optional<Node> fragmentAncestor = getFragmentAncestor(ancestors);
            if (fragmentAncestor.isPresent() && fragmentAncestor.get() instanceof OperationDefinition){
                OperationDefinition operationDefinition = (OperationDefinition) fragmentAncestor.get();
                if (OperationDefinition.Operation.MUTATION.equals(operationDefinition.getOperation()) || OperationDefinition.Operation.SUBSCRIPTION.equals(operationDefinition.getOperation())) {
                    String message = i18n(MisplacedDirective, "DirectiveMisplaced.notAllowedOperationRootLevel", directive.getName(), operationDefinition.getOperation().name().toLowerCase());
                    addError(MisplacedDirective, directive.getSourceLocation(), message);
                }
            }

        }
    }

    /**
     * Get the first ancestor that is not InlineFragment, SelectionSet or FragmentDefinition
     * @param ancestors list of ancestors
     * @return Optional of Node parent that is not InlineFragment, SelectionSet or FragmentDefinition.
     */
    protected Optional<Node> getFragmentAncestor(List<Node> ancestors){
        List<Node> ancestorsCopy = new ArrayList(ancestors);
        Collections.reverse(ancestorsCopy);
        return ancestorsCopy.stream().filter(node ->  !(
                        node instanceof InlineFragment ||
                                node instanceof SelectionSet ||
                                node instanceof FragmentDefinition
                )
        ).findFirst();

    }
}
