package graphql.validation.rules;

import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.validation.ValidationErrorType.DuplicateArgumentNames;

@Internal
public class DeferDirectiveLabel extends AbstractRule {

    private Set<String> labels = new LinkedHashSet<>();
    public DeferDirectiveLabel(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        if (!directive.getName().equals("defer") || directive.getArguments().size() == 0) {
            return;
        }

        Argument labelArgument = directive.getArgument("label");
        // argument type is validated in DeferDirectiveArgumentType
        if (labelArgument != null && labelArgument.getValue() instanceof StringValue) {
            if (labels.contains(((StringValue) labelArgument.getValue()).getValue())) {
                String message = i18n(DuplicateArgumentNames, "UniqueArgumentNames.directiveUniqueArgument", labelArgument.getName(), directive.getName());
                addError(DuplicateArgumentNames, directive.getSourceLocation(), message);
            } else {
                labels.add(((StringValue) labelArgument.getValue()).getValue() );
            }
        }
    }

}