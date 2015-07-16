package graphql.validation;


import graphql.language.Argument;
import graphql.language.Node;
import graphql.language.TypeName;
import graphql.validation.rules.*;

import java.util.ArrayList;
import java.util.List;

public class RulesVisitor implements QueryLanguageVisitor {

    private final List<AbstractRule> rules = new ArrayList<>();
    private ValidationContext validationContext;
    private ErrorCollector errorCollector;

    public RulesVisitor(ValidationContext validationContext,ErrorCollector errorCollector) {
        this.validationContext = validationContext;
        this.errorCollector = errorCollector;

        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector);
        rules.add(argumentsOfCorrectType);
        DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType(validationContext,errorCollector);
        rules.add(defaultValuesOfCorrectType);
        DirectivesOfCorrectType directivesOfCorrectType = new DirectivesOfCorrectType(validationContext,errorCollector);
        rules.add(directivesOfCorrectType);
        FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext,errorCollector);
        rules.add(fieldsOnCorrectType);
        FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext,errorCollector);
        rules.add(fragmentsOnCompositeType);
        KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext,errorCollector);
        rules.add(knownArgumentNames);
        KnownFragmentNames knownFragmentNames = new KnownFragmentNames(validationContext,errorCollector);
        rules.add(knownFragmentNames);
        KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector);
        rules.add(knownTypeNames);
        NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext,errorCollector);
        rules.add(noFragmentCycles);
        NoUndefinedVariables noUndefinedVariables = new NoUndefinedVariables(validationContext,errorCollector);
        rules.add(noUndefinedVariables);
        NoUnusedFragments noUnusedFragments = new NoUnusedFragments(validationContext,errorCollector);
        rules.add(noUnusedFragments);
        NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext,errorCollector);
        rules.add(noUnusedVariables);
        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext,errorCollector);
        rules.add(overlappingFieldsCanBeMerged);
        PossibleFragmentSpreads possibleFragmentSpreads = new PossibleFragmentSpreads(validationContext,errorCollector);
        rules.add(possibleFragmentSpreads);
        ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext,errorCollector);
        rules.add(providedNonNullArguments);
        ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, errorCollector);
        rules.add(scalarLeafs);
        VariablesAreInputTypes variablesAreInputTypes = new VariablesAreInputTypes(validationContext,errorCollector);
        rules.add(variablesAreInputTypes);
        VariablesInAllowedPosition variablesInAllowedPosition = new VariablesInAllowedPosition(validationContext,errorCollector);
        rules.add(variablesInAllowedPosition);
    }

    @Override
    public void enter(Node node) {
        validationContext.getTraversalContext().enter(node);
        if (node instanceof Argument) {
            for (AbstractRule rule : rules) {
                rule.checkArgument((Argument) node);
            }
        } else if (node instanceof TypeName) {
            for (AbstractRule rule : rules) {
                rule.checkTypeName((TypeName) node);
            }
        }
    }

    @Override
    public void leave(Node node) {
        validationContext.getTraversalContext().leave(node);
    }
}
