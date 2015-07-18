package graphql.validation;


import graphql.language.*;
import graphql.validation.rules.*;

import java.util.ArrayList;
import java.util.List;

public class RulesVisitor implements QueryLanguageVisitor {

    private final List<AbstractRule> rules = new ArrayList<>();
    private ValidationContext validationContext;
    private ErrorCollector errorCollector;

    public RulesVisitor(ValidationContext validationContext, ErrorCollector errorCollector) {
        this.validationContext = validationContext;
        this.errorCollector = errorCollector;

        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector);
        rules.add(argumentsOfCorrectType);
        DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType(validationContext, errorCollector);
        rules.add(defaultValuesOfCorrectType);
        DirectivesOfCorrectType directivesOfCorrectType = new DirectivesOfCorrectType(validationContext, errorCollector);
        rules.add(directivesOfCorrectType);
        FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext, errorCollector);
        rules.add(fieldsOnCorrectType);
        FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext, errorCollector);
        rules.add(fragmentsOnCompositeType);
        KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, errorCollector);
        rules.add(knownArgumentNames);
        KnownFragmentNames knownFragmentNames = new KnownFragmentNames(validationContext, errorCollector);
        rules.add(knownFragmentNames);
        KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector);
        rules.add(knownTypeNames);
        NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, errorCollector);
        rules.add(noFragmentCycles);
        NoUndefinedVariables noUndefinedVariables = new NoUndefinedVariables(validationContext, errorCollector);
        rules.add(noUndefinedVariables);
        NoUnusedFragments noUnusedFragments = new NoUnusedFragments(validationContext, errorCollector);
        rules.add(noUnusedFragments);
        NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext, errorCollector);
        rules.add(noUnusedVariables);
        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext, errorCollector);
        rules.add(overlappingFieldsCanBeMerged);
        PossibleFragmentSpreads possibleFragmentSpreads = new PossibleFragmentSpreads(validationContext, errorCollector);
        rules.add(possibleFragmentSpreads);
        ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext, errorCollector);
        rules.add(providedNonNullArguments);
        ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, errorCollector);
        rules.add(scalarLeafs);
        VariablesAreInputTypes variablesAreInputTypes = new VariablesAreInputTypes(validationContext, errorCollector);
        rules.add(variablesAreInputTypes);
        VariablesInAllowedPosition variablesInAllowedPosition = new VariablesInAllowedPosition(validationContext, errorCollector);
        rules.add(variablesInAllowedPosition);
    }

    @Override
    public void enter(Node node) {
        validationContext.getTraversalContext().enter(node);
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
            checkDirective((Directive) node);
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

    private void checkDirective(Directive directive) {
        for (AbstractRule rule : rules) {
            rule.checkDirective(directive);
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
    public void leave(Node node) {
        validationContext.getTraversalContext().leave(node);
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
