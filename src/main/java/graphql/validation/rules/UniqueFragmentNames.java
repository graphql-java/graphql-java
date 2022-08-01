package graphql.validation.rules;

import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashSet;
import java.util.Set;

import static graphql.validation.ValidationErrorType.DuplicateFragmentName;


@Internal
public class UniqueFragmentNames extends AbstractRule {


    private Set<String> fragmentNames = new LinkedHashSet<>();


    public UniqueFragmentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        String name = fragmentDefinition.getName();
        if (name == null) {
            return;
        }

        if (fragmentNames.contains(name)) {
            String message = i18n(DuplicateFragmentName, "UniqueFragmentNames.oneFragment", name);
            addError(DuplicateFragmentName, fragmentDefinition.getSourceLocation(), message);
        } else {
            fragmentNames.add(name);
        }
    }
}
