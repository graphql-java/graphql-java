package graphql.execution.conditional;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.language.NodeUtil;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;

@Internal
public class ConditionalNodes {


    public boolean shouldInclude(DirectivesContainer<?> element,
                                 Map<String, Object> variables,
                                 GraphQLSchema graphQLSchema,
                                 GraphQLContext graphQLContext
    ) {
        return shouldInclude(variables, element.getDirectives());
        // this was backported and additional code which allowed for custom Should include code was removed
    }

    private boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
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
            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(SkipDirective.getArguments(), foundDirective.getArguments(), CoercedVariables.of(variables), GraphQLContext.getDefault(), Locale.getDefault());
            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, "The '%s' directive MUST have a value for the 'if' argument", directiveName);
            return (Boolean) flag;
        }
        return defaultValue;
    }

}