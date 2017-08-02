package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;
import static graphql.language.NodeUtil.directivesByName;


public class ConditionalNodes {

    ValuesResolver valuesResolver;

    public ConditionalNodes() {
        valuesResolver = new ValuesResolver();
    }

    public boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {

        Directive skipDirective = getDirectiveByName(directives, SkipDirective.getName());
        if (skipDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), skipDirective.getArguments(), variables);
            return !(Boolean) argumentValues.get("if");
        }


        Directive includeDirective = getDirectiveByName(directives, IncludeDirective.getName());
        if (includeDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(IncludeDirective.getArguments(), includeDirective.getArguments(), variables);
            return (Boolean) argumentValues.get("if");
        }

        return true;
    }

    private Directive getDirectiveByName(List<Directive> directives, String name) {
        if (directives.isEmpty()) {
            return null;
        }
        return directivesByName(directives).get(name);
    }

}
