package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import graphql.VisibleForTesting;
import graphql.language.Directive;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;

@Internal
public class ConditionalNodes {

    @VisibleForTesting
    ValuesResolver valuesResolver = new ValuesResolver();

    public boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
        boolean skip = getDirectiveResult(variables, directives, SkipDirective.getName(), false);
        boolean include = getDirectiveResult(variables, directives, IncludeDirective.getName(), true);
        return !skip && include;
    }

    private boolean getDirectiveResult(Map<String, Object> variables, List<Directive> directives, String directiveName, boolean defaultValue) {
        Directive foundDirective = findDirectiveByName(directives, directiveName);
        if (foundDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), foundDirective.getArguments(), variables);
            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, () -> String.format("The '%s' directive MUST have a value for the 'if' argument", directiveName));
            return (Boolean) flag;
        }
        return defaultValue;
    }

    private Directive findDirectiveByName(List<Directive> directives, String name) {
        for (Directive directive : directives) {
            if (Objects.equals(directive.getName(), name)) {
                return directive;
            }
        }
        return null;
    }

}
