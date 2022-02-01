package graphql.validation;


import graphql.Internal;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.rules.ArgumentsOfCorrectType;
import graphql.validation.rules.ExecutableDefinitions;
import graphql.validation.rules.FieldsOnCorrectType;
import graphql.validation.rules.FragmentsOnCompositeType;
import graphql.validation.rules.KnownArgumentNames;
import graphql.validation.rules.KnownDirectives;
import graphql.validation.rules.KnownFragmentNames;
import graphql.validation.rules.KnownTypeNames;
import graphql.validation.rules.LoneAnonymousOperation;
import graphql.validation.rules.NoFragmentCycles;
import graphql.validation.rules.NoUndefinedVariables;
import graphql.validation.rules.NoUnusedFragments;
import graphql.validation.rules.NoUnusedVariables;
import graphql.validation.rules.OverlappingFieldsCanBeMerged;
import graphql.validation.rules.PossibleFragmentSpreads;
import graphql.validation.rules.ProvidedNonNullArguments;
import graphql.validation.rules.ScalarLeafs;
import graphql.validation.rules.UniqueArgumentNamesRule;
import graphql.validation.rules.UniqueDirectiveNamesPerLocation;
import graphql.validation.rules.UniqueFragmentNames;
import graphql.validation.rules.UniqueOperationNames;
import graphql.validation.rules.UniqueVariableNamesRule;
import graphql.validation.rules.VariableDefaultValuesOfCorrectType;
import graphql.validation.rules.VariableTypesMatchRule;
import graphql.validation.rules.VariablesAreInputTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Internal
public class Validator {

    static int MAX_VALIDATION_ERRORS = 100;

    /**
     * `graphql-java` will stop validation after a maximum number of validation messages has been reached.  Attackers
     * can send pathologically invalid queries to induce a Denial of Service attack and fill memory with 10000s of errors
     * and burn CPU in process.
     *
     * By default, this is set to 100 errors.  You can set a new JVM wide value as the maximum allowed validation errors.
     *
     * @param maxValidationErrors the maximum validation errors allow JVM wide
     */
    public static void setMaxValidationErrors(int maxValidationErrors) {
        MAX_VALIDATION_ERRORS = maxValidationErrors;
    }

    public static int getMaxValidationErrors() {
        return MAX_VALIDATION_ERRORS;
    }

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document) {
        return validateDocument(schema, document, ruleClass -> true);
    }

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document, Predicate<Class<?>> applyRule) {
        ValidationContext validationContext = new ValidationContext(schema, document);

        ValidationErrorCollector validationErrorCollector = new ValidationErrorCollector(MAX_VALIDATION_ERRORS);
        List<AbstractRule> rules = createRules(validationContext, validationErrorCollector);
        // filter out any rules they don't want applied
        rules = rules.stream().filter(r -> applyRule.test(r.getClass())).collect(Collectors.toList());
        LanguageTraversal languageTraversal = new LanguageTraversal();
        try {
            languageTraversal.traverse(document, new RulesVisitor(validationContext, rules));
        } catch (ValidationErrorCollector.MaxValidationErrorsReached ignored) {
            // if we have generated enough errors, then we can shortcut out
        }

        return validationErrorCollector.getErrors();
    }

    public List<AbstractRule> createRules(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        List<AbstractRule> rules = new ArrayList<>();

        ExecutableDefinitions executableDefinitions = new ExecutableDefinitions(validationContext, validationErrorCollector);
        rules.add(executableDefinitions);

        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, validationErrorCollector);
        rules.add(argumentsOfCorrectType);

        FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext, validationErrorCollector);
        rules.add(fieldsOnCorrectType);
        FragmentsOnCompositeType fragmentsOnCompositeType = new FragmentsOnCompositeType(validationContext, validationErrorCollector);
        rules.add(fragmentsOnCompositeType);

        KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, validationErrorCollector);
        rules.add(knownArgumentNames);
        KnownDirectives knownDirectives = new KnownDirectives(validationContext, validationErrorCollector);
        rules.add(knownDirectives);
        KnownFragmentNames knownFragmentNames = new KnownFragmentNames(validationContext, validationErrorCollector);
        rules.add(knownFragmentNames);
        KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, validationErrorCollector);
        rules.add(knownTypeNames);

        NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, validationErrorCollector);
        rules.add(noFragmentCycles);
        NoUndefinedVariables noUndefinedVariables = new NoUndefinedVariables(validationContext, validationErrorCollector);
        rules.add(noUndefinedVariables);
        NoUnusedFragments noUnusedFragments = new NoUnusedFragments(validationContext, validationErrorCollector);
        rules.add(noUnusedFragments);
        NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext, validationErrorCollector);
        rules.add(noUnusedVariables);

        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext, validationErrorCollector);
        rules.add(overlappingFieldsCanBeMerged);

        PossibleFragmentSpreads possibleFragmentSpreads = new PossibleFragmentSpreads(validationContext, validationErrorCollector);
        rules.add(possibleFragmentSpreads);
        ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext, validationErrorCollector);
        rules.add(providedNonNullArguments);

        ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, validationErrorCollector);
        rules.add(scalarLeafs);

        VariableDefaultValuesOfCorrectType variableDefaultValuesOfCorrectType = new VariableDefaultValuesOfCorrectType(validationContext, validationErrorCollector);
        rules.add(variableDefaultValuesOfCorrectType);
        VariablesAreInputTypes variablesAreInputTypes = new VariablesAreInputTypes(validationContext, validationErrorCollector);
        rules.add(variablesAreInputTypes);
        VariableTypesMatchRule variableTypesMatchRule = new VariableTypesMatchRule(validationContext, validationErrorCollector);
        rules.add(variableTypesMatchRule);

        LoneAnonymousOperation loneAnonymousOperation = new LoneAnonymousOperation(validationContext, validationErrorCollector);
        rules.add(loneAnonymousOperation);

        UniqueOperationNames uniqueOperationNames = new UniqueOperationNames(validationContext, validationErrorCollector);
        rules.add(uniqueOperationNames);

        UniqueFragmentNames uniqueFragmentNames = new UniqueFragmentNames(validationContext, validationErrorCollector);
        rules.add(uniqueFragmentNames);

        UniqueDirectiveNamesPerLocation uniqueDirectiveNamesPerLocation = new UniqueDirectiveNamesPerLocation(validationContext, validationErrorCollector);
        rules.add(uniqueDirectiveNamesPerLocation);

        UniqueArgumentNamesRule uniqueArgumentNamesRule = new UniqueArgumentNamesRule(validationContext, validationErrorCollector);
        rules.add(uniqueArgumentNamesRule);

        UniqueVariableNamesRule uniqueVariableNamesRule = new UniqueVariableNamesRule(validationContext, validationErrorCollector);
        rules.add(uniqueVariableNamesRule);

        return rules;
    }
}
