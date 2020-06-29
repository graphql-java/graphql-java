package graphql.validation.rules;

import graphql.Internal;
import graphql.VisibleForTesting;
import graphql.language.FragmentDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.LinkedHashSet;
import java.util.Set;

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
            addError(ValidationErrorType.DuplicateFragmentName, fragmentDefinition.getSourceLocation(), duplicateFragmentName(name));
        } else {
            fragmentNames.add(name);
        }
    }

    @VisibleForTesting
    static String duplicateFragmentName(String fragmentName) {
        return String.format("There can be only one fragment named '%s'", fragmentName);
    }
}
