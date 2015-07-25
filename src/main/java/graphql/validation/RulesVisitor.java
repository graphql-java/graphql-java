package graphql.validation;


import graphql.language.*;

import java.util.ArrayList;
import java.util.List;

public class RulesVisitor implements QueryLanguageVisitor {

    private final List<AbstractRule> rules = new ArrayList<>();
    private ValidationContext validationContext;

    public RulesVisitor(ValidationContext validationContext, List<AbstractRule> rules) {
        this.validationContext = validationContext;
        this.rules.addAll(rules);
    }

    @Override
    public void enter(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().enter(node, ancestors);
        if (node instanceof Argument) {
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
            checkFragmentSpread((FragmentSpread) node);
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

    private void checkArgument(Argument node) {
        for (AbstractRule rule : rules) {
            rule.checkArgument(node);
        }
    }

    private void checkTypeName(TypeName node) {
        for (AbstractRule rule : rules) {
            rule.checkTypeName(node);
        }
    }


    private void checkVariableDefinition(VariableDefinition variableDefinition) {
        for (AbstractRule rule : rules) {
            rule.checkVariableDefinition(variableDefinition);
        }
    }

    private void checkField(Field field) {
        for (AbstractRule rule : rules) {
            rule.checkField(field);
        }
    }

    private void checkInlineFragment(InlineFragment inlineFragment) {
        for (AbstractRule rule : rules) {
            rule.checkInlineFragment(inlineFragment);
        }
    }

    private void checkDirective(Directive directive, List<Node> ancestors) {
        for (AbstractRule rule : rules) {
            rule.checkDirective(directive, ancestors);
        }
    }

    private void checkFragmentSpread(FragmentSpread fragmentSpread) {
        for (AbstractRule rule : rules) {
            rule.checkFragmentSpread(fragmentSpread);
        }
    }

    private void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        for (AbstractRule rule : rules) {
            rule.checkFragmentDefinition(fragmentDefinition);
        }
    }

    private void checkOperationDefinition(OperationDefinition operationDefinition) {
        for (AbstractRule rule : rules) {
            rule.checkOperationDefinition(operationDefinition);
        }
    }

    private void checkSelectionSet(SelectionSet selectionSet) {
        for (AbstractRule rule : rules) {
            rule.checkSelectionSet(selectionSet);
        }
    }

    private void checkVariable(VariableReference variableReference) {
        for (AbstractRule rule : rules) {
            rule.checkVariable(variableReference);
        }
    }


    @Override
    public void leave(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().leave(node, ancestors);
        if (node instanceof Document) {
            documentFinished((Document) node);
        }

    }

    private void documentFinished(Document document) {
        for (AbstractRule rule : rules) {
            rule.documentFinished(document);
        }
    }
}
