package graphql.validation;


import graphql.language.Argument;
import graphql.language.Node;
import graphql.validation.rules.ArgumentsOfCorrectType;

import java.util.ArrayList;
import java.util.List;

public class RulesVisitor implements QueryLanguageVisitor {

    private final List<AbstractRule> rules = new ArrayList<>();
    private ValidationContext validationContext;

    public RulesVisitor(ValidationContext validationContext) {
        this.validationContext = validationContext;
        rules.add(new ArgumentsOfCorrectType(validationContext));
    }

    @Override
    public void enter(Node node) {
        validationContext.getTypeInfo().enter(node);
        if (node instanceof Argument) {
            for (AbstractRule rule : rules) {
                rule.checkArgument((Argument) node);
            }
        }
    }

    @Override
    public void leave(Node node) {
        validationContext.getTypeInfo().leave(node);
    }
}
