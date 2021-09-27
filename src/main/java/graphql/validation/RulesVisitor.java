package graphql.validation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

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
    private int fragmentSpreadVisitDepth;
    private final Stack<Node> popRulesStackOnLeave = new Stack<>();
    private final Stack<ImmutableList<AbstractRule>> rulesStack = new Stack<>();
    private final Set<String> visitedFragmentSpreads = new HashSet<>();

    public RulesVisitor(ValidationContext validationContext, List<AbstractRule> rules) {
        this.validationContext = validationContext;
        this.rulesStack.push(ImmutableList.copyOf(rules));
        this.fragmentSpreadVisitDepth = 0;
    }

    private ImmutableList<AbstractRule> filterRulesVisitingFragmentSpreads(List<AbstractRule> rules, boolean isVisitFragmentSpreads) {
        Iterator<AbstractRule> itr = rulesStack.peek()
            .stream()
            .filter(r -> r.isVisitFragmentSpreads() == isVisitFragmentSpreads)
            .iterator();
        return ImmutableList.copyOf(itr);
    }

    @Override
    public void enter(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().enter(node, ancestors);
        ImmutableList<AbstractRule> rulesToConsider = rulesStack.peek();

        if (node instanceof Document){
            checkDocument((Document) node, rulesToConsider);
        } else if (node instanceof Argument) {
            checkArgument((Argument) node, rulesToConsider);
        } else if (node instanceof TypeName) {
            checkTypeName((TypeName) node, rulesToConsider);
        } else if (node instanceof VariableDefinition) {
            checkVariableDefinition((VariableDefinition) node, rulesToConsider);
        } else if (node instanceof Field) {
            checkField((Field) node, rulesToConsider);
        } else if (node instanceof InlineFragment) {
            checkInlineFragment((InlineFragment) node, rulesToConsider);
        } else if (node instanceof Directive) {
            checkDirective((Directive) node, ancestors, rulesToConsider);
        } else if (node instanceof FragmentSpread) {
            checkFragmentSpread((FragmentSpread) node, rulesToConsider, ancestors);
        } else if (node instanceof FragmentDefinition) {
            checkFragmentDefinition((FragmentDefinition) node, rulesToConsider);
        } else if (node instanceof OperationDefinition) {
            checkOperationDefinition((OperationDefinition) node, rulesToConsider);
        } else if (node instanceof VariableReference) {
            checkVariable((VariableReference) node, rulesToConsider);
        } else if (node instanceof SelectionSet) {
            checkSelectionSet((SelectionSet) node, rulesToConsider);
        }
    }

    private void checkDocument(Document node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkDocument(node));
    }

    private void checkArgument(Argument node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkArgument(node));
    }

    private void checkTypeName(TypeName node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkTypeName(node));
    }

    private void checkVariableDefinition(VariableDefinition node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkVariableDefinition(node));
    }

    private void checkField(Field node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkField(node));
    }

    private void checkInlineFragment(InlineFragment node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkInlineFragment(node));
    }

    private void checkDirective(Directive node, List<Node> ancestors, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkDirective(node, ancestors));
    }

    private void checkFragmentSpread(FragmentSpread node, List<AbstractRule> rules, List<Node> ancestors) {
        rules.forEach(r -> r.checkFragmentSpread(node));

        ImmutableList<AbstractRule> rulesVisitingFragmentSpreads = filterRulesVisitingFragmentSpreads(rules, true);
        if (rulesVisitingFragmentSpreads.size() > 0) {
            FragmentDefinition fragment = validationContext.getFragment(node.getName());
            if (fragment != null && !ancestors.contains(fragment) && !visitedFragmentSpreads.contains(node.getName())) {
                // Manually traverse into the FragmentDefinition
                visitedFragmentSpreads.add(node.getName());
                rulesStack.push(rulesVisitingFragmentSpreads);
                fragmentSpreadVisitDepth++;
                new LanguageTraversal(ancestors).traverse(fragment, this);
                fragmentSpreadVisitDepth--;
                rulesStack.pop();
            }
        }
    }

    private void checkFragmentDefinition(FragmentDefinition node, ImmutableList<AbstractRule> rules) {
        ImmutableList<AbstractRule> scopeRules = rules;

        // If we've encountered a FragmentDefinition and we got here without coming through a
        // FragmentSpread, then suspend all isVisitFragmentSpread rules for this subtree.
        // Expect these rules to be checked when when the FragmentSpread is traversed
        if (fragmentSpreadVisitDepth == 0) {
            scopeRules = filterRulesVisitingFragmentSpreads(rules, false);
            popRulesStackOnLeave.push(node);
            rulesStack.push(scopeRules);
        }

        scopeRules.forEach(r -> r.checkFragmentDefinition(node));
    }

    private void checkOperationDefinition(OperationDefinition node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkOperationDefinition(node));
    }

    private void checkSelectionSet(SelectionSet node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkSelectionSet(node));
    }

    private void checkVariable(VariableReference node, List<AbstractRule> rules) {
        rules.forEach(r -> r.checkVariable(node));
    }

    @Override
    public void leave(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().leave(node, ancestors);
        ImmutableList<AbstractRule> rules = rulesStack.peek();

        if (node instanceof Document) {
            documentFinished((Document) node, rules);
        } else if (node instanceof OperationDefinition) {
            leaveOperationDefinition((OperationDefinition) node, rules);
        } else if (node instanceof SelectionSet) {
            leaveSelectionSet((SelectionSet) node, rules);
        }

        if (!popRulesStackOnLeave.isEmpty() && popRulesStackOnLeave.peek() == node) {
            popRulesStackOnLeave.pop();
            rulesStack.pop();
        }
    }

    private void leaveSelectionSet(SelectionSet node, List<AbstractRule> rules) {
        rules.forEach(r -> r.leaveSelectionSet(node));
    }

    private void leaveOperationDefinition(OperationDefinition node, List<AbstractRule> rules) {
        rules.forEach(r -> r.leaveOperationDefinition(node));
    }

    private void documentFinished(Document node, List<AbstractRule> rules) {
        rules.forEach(r -> r.documentFinished(node));
    }
}
