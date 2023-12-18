package graphql.normalized.incremental;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.NodeUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static graphql.Directives.DeferDirective;

@Internal
public class IncrementalNodes {

    public DeferLabel getDeferLabel(
            Map<String, Object> variables,
            List<Directive> directives
    ) {
        Directive deferDirective = NodeUtil.findNodeByName(directives, DeferDirective.getName());

        if (deferDirective != null) {
            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(DeferDirective.getArguments(), deferDirective.getArguments(), CoercedVariables.of(variables), GraphQLContext.getDefault(), Locale.getDefault());

            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, () -> String.format("The '%s' directive MUST have a value for the 'if' argument", DeferDirective.getName()));

            if (!((Boolean) flag)) {
                return null;
            }

            Object label = argumentValues.get("label");

            if (label == null) {
                return new DeferLabel(null);
            }

            Assert.assertTrue(label instanceof String, () -> String.format("The 'label' argument from the '%s' directive MUST contain a String value", DeferDirective.getName()));

            return new DeferLabel((String) label);

        }

        return null;
    }

    public DeferExecution getDeferExecution(
            Map<String, Object> variables,
            List<Directive> directives
    ) {
        Directive deferDirective = NodeUtil.findNodeByName(directives, DeferDirective.getName());

        if (deferDirective != null) {
            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(DeferDirective.getArguments(), deferDirective.getArguments(), CoercedVariables.of(variables), GraphQLContext.getDefault(), Locale.getDefault());

            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, () -> String.format("The '%s' directive MUST have a value for the 'if' argument", DeferDirective.getName()));

            if (!((Boolean) flag)) {
                return null;
            }

            Object label = argumentValues.get("label");

            if (label == null) {
                return new DeferExecution(null);
            }

            Assert.assertTrue(label instanceof String, () -> String.format("The 'label' argument from the '%s' directive MUST contain a String value", DeferDirective.getName()));

            return new DeferExecution((String) label);

        }

        return null;
    }
}
