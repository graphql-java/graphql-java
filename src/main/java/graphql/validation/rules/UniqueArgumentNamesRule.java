package graphql.validation.rules;

import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.Node;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unique argument names
 *
 * A GraphQL field or directive is only valid if all supplied arguments are uniquely named.
 */
@Internal
public class UniqueArgumentNamesRule extends AbstractRule {
    public UniqueArgumentNamesRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        if (field.getArguments() == null || field.getArguments().size() <= 1) {
            return;
        }

        Set<String> arguments = new HashSet<>();

        for (Argument argument : field.getArguments()) {
            if (arguments.contains(argument.getName())) {
                addError(ValidationErrorType.DuplicateArgumentNames, field.getSourceLocation(), duplicateArgumentNameMessage(argument.getName()));
            } else {
                arguments.add(argument.getName());
            }
        }
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        if (directive.getArguments() == null || directive.getArguments().size() <= 1) {
            return;
        }

        Set<String> arguments = new HashSet<>(directive.getArguments().size());

        for (Argument argument : directive.getArguments()) {
            if (arguments.contains(argument.getName())) {
                addError(ValidationErrorType.DuplicateArgumentNames, directive.getSourceLocation(), duplicateArgumentNameMessage(argument.getName()));
            } else {
                arguments.add(argument.getName());
            }
        }

    }

    static String duplicateArgumentNameMessage(String argumentName) {
        return String.format("There can be only one argument named '%s'", argumentName);
    }
}
