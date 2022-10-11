package graphql.validation.rules;

import com.google.common.collect.Sets;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.Node;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.List;
import java.util.Set;

import static graphql.validation.ValidationErrorType.DuplicateArgumentNames;


/**
 * Unique argument names
 *
 * A GraphQL field or directive is only valid if all supplied arguments are uniquely named.
 */
@Internal
public class UniqueArgumentNames extends AbstractRule {
    public UniqueArgumentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        if (field.getArguments() == null || field.getArguments().size() <= 1) {
            return;
        }

        Set<String> arguments = Sets.newHashSetWithExpectedSize(field.getArguments().size());

        for (Argument argument : field.getArguments()) {
            if (arguments.contains(argument.getName())) {
                String message = i18n(DuplicateArgumentNames, "UniqueArgumentNames.uniqueArgument", argument.getName());
                addError(DuplicateArgumentNames, field.getSourceLocation(), message);
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

        Set<String> arguments = Sets.newHashSetWithExpectedSize(directive.getArguments().size());

        for (Argument argument : directive.getArguments()) {
            if (arguments.contains(argument.getName())) {
                String message = i18n(DuplicateArgumentNames, "UniqueArgumentNames.uniqueArgument", argument.getName());
                addError(DuplicateArgumentNames, directive.getSourceLocation(), message);
            } else {
                arguments.add(argument.getName());
            }
        }

    }
}
