package graphql.validation.rules;

import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * https://facebook.github.io/graphql/June2018/#sec-Directives-Are-Unique-Per-Location
 */
public class UniqueDirectiveNamesPerLocation extends AbstractRule {

    public UniqueDirectiveNamesPerLocation(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDocument(Document document) {
        super.checkDocument(document);
    }

    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        checkDirectivesUniqueness(inlineFragment, inlineFragment.getDirectives());
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        checkDirectivesUniqueness(fragmentDefinition, fragmentDefinition.getDirectives());
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        checkDirectivesUniqueness(fragmentSpread, fragmentSpread.getDirectives());
    }

    @Override
    public void checkField(Field field) {
        checkDirectivesUniqueness(field, field.getDirectives());
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        checkDirectivesUniqueness(operationDefinition, operationDefinition.getDirectives());
    }

    private void checkDirectivesUniqueness(Node<?> directivesContainer, List<Directive> directives) {
        Set<String> names = new LinkedHashSet<>();
        directives.forEach(directive -> {
            String name = directive.getName();
            if (names.contains(name)) {
                String message = i18n("UniqueDirectiveNamesPerLocation.uniqueDirectives", name, directivesContainer.getClass().getSimpleName());
                addError(ValidationErrorType.DuplicateDirectiveName,
                        directive.getSourceLocation(),
                        message);
            } else {
                names.add(name);
            }
        });
    }

}
