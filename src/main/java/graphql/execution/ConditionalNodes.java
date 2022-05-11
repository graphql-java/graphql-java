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

@Internal
public class ConditionalNodes {

    @VisibleForTesting
    ValuesResolver valuesResolver = new ValuesResolver();

    public boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
        // shortcut on no directives
        if (directives.isEmpty()) {
            return true;
        }
        boolean skip = getDirectiveResult(variables, directives, SkipDirective.getName(), false);
        if (skip) {
            return false;
        }

        return getDirectiveResult(variables, directives, IncludeDirective.getName(), true);
    }

    private boolean getDirectiveResult(Map<String, Object> variables, List<Directive> directives, String directiveName, boolean defaultValue) {
        Directive foundDirective = NodeUtil.findNodeByName(directives, directiveName);
        if (foundDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), foundDirective.getArguments(), variables);
            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, () -> String.format("The '%s' directive MUST have a value for the 'if' argument", directiveName));
            return (Boolean) flag;
        }
        return defaultValue;
    }

}
