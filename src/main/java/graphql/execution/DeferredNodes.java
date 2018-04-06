package graphql.execution;

import graphql.language.Directive;

import java.util.List;

import static graphql.Directives.DeferDirective;
import static graphql.execution.ConditionalNodes.getDirectiveByName;


public class DeferredNodes {

    public static boolean deferedNode(List<Directive> directives) {
        return getDirectiveResult(directives, DeferDirective.getName(), false);
    }

    private static boolean getDirectiveResult(List<Directive> directives, String directiveName, boolean defaultValue) {
        Directive directive = getDirectiveByName(directives, directiveName);
        if (directive != null) {
            return true;
        }

        return defaultValue;
    }

}
