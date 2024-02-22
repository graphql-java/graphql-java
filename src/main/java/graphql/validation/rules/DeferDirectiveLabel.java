package graphql.validation.rules;

import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.validation.ValidationErrorType.DuplicateArgumentNames;
import static graphql.validation.ValidationErrorType.DuplicateIncrementalLabel;
import static graphql.validation.ValidationErrorType.VariableNotAllowed;
import static graphql.validation.ValidationErrorType.WrongType;

/**
 * Defer and stream directive labels are unique
 *
 * A GraphQL document is only valid if defer and stream directives' label argument is static and unique.
 *
 * See proposed spec:<a href="https://github.com/graphql/graphql-spec/pull/742">spec/Section 5 -- Validation.md ### ### Defer And Stream Directive Labels Are Unique</a>
 */
@ExperimentalApi
public class DeferDirectiveLabel extends AbstractRule {
    private Set<String> checkedLabels = new LinkedHashSet<>();
    public DeferDirectiveLabel(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        // ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT must be true
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) ||
                !Directives.DeferDirective.getName().equals(directive.getName()) ||
                directive.getArguments().size() == 0) {
            return;
        }

        Argument labelArgument = directive.getArgument("label");
        if (labelArgument == null || labelArgument.getValue() instanceof NullValue){
            return;
        }
        Value labelArgumentValue = labelArgument.getValue();

        if (!(labelArgumentValue instanceof StringValue)) {
            String message = i18n(WrongType, "DeferDirective.labelMustBeStaticString");
            addError(WrongType, directive.getSourceLocation(), message);
        } else {
          if (checkedLabels.contains(((StringValue) labelArgumentValue).getValue())) {
              String message = i18n(DuplicateIncrementalLabel, "IncrementalDirective.uniqueArgument", labelArgument.getName(), directive.getName());
              addError(DuplicateIncrementalLabel, directive.getSourceLocation(), message);
          } else {
              checkedLabels.add(((StringValue) labelArgumentValue).getValue());
          }
        }
    }



}