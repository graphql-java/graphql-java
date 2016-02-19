package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;


/**
 * <p>ConditionalNodes class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ConditionalNodes {

    ValuesResolver valuesResolver;

    /**
     * <p>Constructor for ConditionalNodes.</p>
     */
    public ConditionalNodes() {
        valuesResolver = new ValuesResolver();
    }

    /**
     * <p>shouldInclude.</p>
     *
     * @param executionContext a {@link graphql.execution.ExecutionContext} object.
     * @param directives a {@link java.util.List} object.
     * @return a boolean.
     */
    public boolean shouldInclude(ExecutionContext executionContext, List<Directive> directives) {

        Directive skipDirective = findDirective(directives, SkipDirective.getName());
        if (skipDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), skipDirective.getArguments(), executionContext.getVariables());
            return !(Boolean) argumentValues.get("if");
        }


        Directive includeDirective = findDirective(directives, IncludeDirective.getName());
        if (includeDirective != null) {
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(IncludeDirective.getArguments(), includeDirective.getArguments(), executionContext.getVariables());
            return (Boolean) argumentValues.get("if");
        }

        return true;
    }

    private Directive findDirective(List<Directive> directives, String name) {
        for (Directive directive : directives) {
            if (directive.getName().equals(name)) return directive;
        }
        return null;
    }

}
