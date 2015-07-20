package graphql.validation;


import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.rules.*;

import java.util.ArrayList;
import java.util.List;

public class Validator {

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document) {
        ValidationContext validationContext = new ValidationContext(schema, document);


        ErrorCollector errorCollector = new ErrorCollector();
        List<AbstractRule> rules = createRules(validationContext, errorCollector);
        LanguageTraversal languageTraversal = new LanguageTraversal();
        languageTraversal.traverse(document, new RulesVisitor(validationContext, rules));

        return errorCollector.getErrors();
    }

    private List<AbstractRule> createRules(ValidationContext validationContext, ErrorCollector errorCollector) {
        List<AbstractRule> rules = new ArrayList<>();
        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector);
        rules.add(argumentsOfCorrectType);
        DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType(validationContext, errorCollector);
        rules.add(defaultValuesOfCorrectType);
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
        return rules;
    }
}
