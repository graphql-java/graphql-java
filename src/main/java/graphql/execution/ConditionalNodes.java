package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import graphql.VisibleForTesting;
import graphql.language.Directive;
import graphql.language.NodeUtil;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;
import static graphql.collect.ImmutableKit.emptyList;


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
        List<Directive> foundDirectives = getDirectiveByName(directives, directiveName);
        if (!foundDirectives.isEmpty()) {
            Directive directive = foundDirectives.get(0);
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), directive.getArguments(), variables);
            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, () -> String.format("The '%s' directive MUST have a value for the 'if' argument", directiveName));
            return (Boolean) flag;
        }
        return defaultValue;
    }

    private List<Directive> getDirectiveByName(List<Directive> directives, String name) {
        return NodeUtil.allDirectivesByName(directives).getOrDefault(name, emptyList());
    }

}
