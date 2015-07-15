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
    private ErrorCollector errorCollector;

    public RulesVisitor(ValidationContext validationContext,ErrorCollector errorCollector) {
        this.validationContext = validationContext;
        this.errorCollector = errorCollector;

        ArgumentsOfCorrectType argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector);
        rules.add(argumentsOfCorrectType);

        KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector);
        rules.add(knownTypeNames);

        DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType(validationContext, errorCollector);
        rules.add(defaultValuesOfCorrectType);

        ScalarLeafs scalarLeafs = new ScalarLeafs(validationContext, errorCollector);
        rules.add(scalarLeafs);
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
