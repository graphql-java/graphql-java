package graphql.validation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;

@Internal
@SuppressWarnings("rawtypes")
public class RulesVisitor implements DocumentVisitor {
    private final ValidationContext validationContext;
    private final List<AbstractRule> allRules;
    private List<AbstractRule> currentRules;
    private final Set<String> visitedFragmentSpreads = new HashSet<>();
    private final List<AbstractRule> fragmentSpreadVisitRules;
    private final List<AbstractRule> nonFragmentSpreadRules;
    private boolean operationScope = false;
    private int fragmentSpreadVisitDepth = 0;

    public RulesVisitor(ValidationContext validationContext, List<AbstractRule> rules) {
        this.validationContext = validationContext;
        this.allRules = rules;
        this.currentRules = allRules;
        this.nonFragmentSpreadRules = filterRulesVisitingFragmentSpreads(allRules, false);
        this.fragmentSpreadVisitRules = filterRulesVisitingFragmentSpreads(allRules, true);
    }

    private List<AbstractRule> filterRulesVisitingFragmentSpreads(List<AbstractRule> rules, boolean isVisitFragmentSpreads) {
        Iterator<AbstractRule> itr = rules
            .stream()
            .filter(r -> r.isVisitFragmentSpreads() == isVisitFragmentSpreads)
            .iterator();
        return ImmutableList.copyOf(itr);
    }

    @Override
    public void enter(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().enter(node, ancestors);

        if (node instanceof Document){
            checkDocument((Document) node);
        } else if (node instanceof Argument) {
            checkArgument((Argument) node);
        } else if (node instanceof TypeName) {
            checkTypeName((TypeName) node);
        } else if (node instanceof VariableDefinition) {
            checkVariableDefinition((VariableDefinition) node);
        } else if (node instanceof Field) {
            checkField((Field) node);
        } else if (node instanceof InlineFragment) {
            checkInlineFragment((InlineFragment) node);
        } else if (node instanceof Directive) {
            checkDirective((Directive) node, ancestors);
        } else if (node instanceof FragmentSpread) {
            checkFragmentSpread((FragmentSpread) node, ancestors);
        } else if (node instanceof FragmentDefinition) {
            checkFragmentDefinition((FragmentDefinition) node);
        } else if (node instanceof OperationDefinition) {
            checkOperationDefinition((OperationDefinition) node);
        } else if (node instanceof VariableReference) {
            checkVariable((VariableReference) node);
        } else if (node instanceof SelectionSet) {
            checkSelectionSet((SelectionSet) node);
        }
    }

    private void checkDocument(Document node) {
        currentRules.forEach(r -> r.checkDocument(node));
    }

    private void checkArgument(Argument node) {
        currentRules.forEach(r -> r.checkArgument(node));
    }

    private void checkTypeName(TypeName node) {
        currentRules.forEach(r -> r.checkTypeName(node));
    }

    private void checkVariableDefinition(VariableDefinition node) {
        currentRules.forEach(r -> r.checkVariableDefinition(node));
    }

    private void checkField(Field node) {
        currentRules.forEach(r -> r.checkField(node));
    }

    private void checkInlineFragment(InlineFragment node) {
        currentRules.forEach(r -> r.checkInlineFragment(node));
    }

    private void checkDirective(Directive node, List<Node> ancestors) {
        currentRules.forEach(r -> r.checkDirective(node, ancestors));
    }

    private void checkFragmentSpread(FragmentSpread node, List<Node> ancestors) {
        currentRules.forEach(r -> r.checkFragmentSpread(node));

        if (operationScope) {
            FragmentDefinition fragment = validationContext.getFragment(node.getName());
            if (fragment != null && !visitedFragmentSpreads.contains(node.getName())) {
                // Manually traverse into the FragmentDefinition
                visitedFragmentSpreads.add(node.getName());
                List<AbstractRule> prevRules = currentRules;
                currentRules = fragmentSpreadVisitRules;
                fragmentSpreadVisitDepth++;
                new LanguageTraversal(ancestors).traverse(fragment, this);
                fragmentSpreadVisitDepth--;
                currentRules = prevRules;
            }
        }
    }

    private void checkFragmentDefinition(FragmentDefinition node) {
        // If we've encountered a FragmentDefinition and we got here without coming through
        // an OperationDefinition, then suspend all isVisitFragmentSpread rules for this subtree.
        // Expect these rules to be checked when the FragmentSpread is traversed
        if (fragmentSpreadVisitDepth == 0) {
            currentRules = nonFragmentSpreadRules;
        }

        currentRules.forEach(r -> r.checkFragmentDefinition(node));
    }

    private void checkOperationDefinition(OperationDefinition node) {
        operationScope = true;
        currentRules.forEach(r -> r.checkOperationDefinition(node));
    }

    private void checkSelectionSet(SelectionSet node) {
        currentRules.forEach(r -> r.checkSelectionSet(node));
    }

    private void checkVariable(VariableReference node) {
        currentRules.forEach(r -> r.checkVariable(node));
    }

    @Override
    public void leave(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().leave(node, ancestors);

        if (node instanceof Document) {
            documentFinished((Document) node);
        } else if (node instanceof OperationDefinition) {
            leaveOperationDefinition((OperationDefinition) node);
        } else if (node instanceof SelectionSet) {
            leaveSelectionSet((SelectionSet) node);
        } else if (node instanceof FragmentDefinition) {
            leaveFragmentDefinition((FragmentDefinition) node);
        }
    }

    private void leaveSelectionSet(SelectionSet node) {
        currentRules.forEach(r -> r.leaveSelectionSet(node));
    }

    private void leaveOperationDefinition(OperationDefinition node) {
        // fragments should be revisited for each operation
        visitedFragmentSpreads.clear();
        operationScope = false;
        currentRules.forEach(r -> r.leaveOperationDefinition(node));
    }

    private void documentFinished(Document node) {
        currentRules.forEach(r -> r.documentFinished(node));
    }

    private void leaveFragmentDefinition(FragmentDefinition node) {
        if (fragmentSpreadVisitDepth == 0) {
            currentRules = allRules;
        }
    }
}
