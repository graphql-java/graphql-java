package graphql.validation;


import graphql.language.Argument;
import graphql.language.Node;
import graphql.language.TypeName;
import graphql.validation.rules.ArgumentsOfCorrectType;
import graphql.validation.rules.DefaultValuesOfCorrectType;
import graphql.validation.rules.KnownTypeNames;
import graphql.validation.rules.ScalarLeafs;

import java.util.ArrayList;
import java.util.List;

public class RulesVisitor implements QueryLanguageVisitor {

    private final List<AbstractRule> rules = new ArrayList<>();
    private ValidationContext validationContext;

    public RulesVisitor(ValidationContext validationContext) {
        this.validationContext = validationContext;

        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType();
        argumentsOfCorrectType.setValidationContext(validationContext);
        rules.add(argumentsOfCorrectType);

        KnownTypeNames knownTypeNames = new KnownTypeNames();
        knownTypeNames.setValidationContext(validationContext);
        rules.add(knownTypeNames);

        DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType();
        defaultValuesOfCorrectType.setValidationContext(validationContext);
        rules.add(defaultValuesOfCorrectType);

        ScalarLeafs scalarLeafs = new ScalarLeafs();
        scalarLeafs.setValidationContext(validationContext);
        rules.add(scalarLeafs);
    }

    @Override
    public void enter(Node node) {
        validationContext.getTypeInfo().enter(node);
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
        validationContext.getTypeInfo().leave(node);
    }
}
