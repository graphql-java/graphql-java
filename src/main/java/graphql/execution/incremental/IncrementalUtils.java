package graphql.execution.incremental;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.NodeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static graphql.Directives.DeferDirective;

@Internal
public class IncrementalUtils {
    private IncrementalUtils() {
    }

    public static @Nullable <T> T createDeferredExecution(
            Map<String, Object> variables,
            List<Directive> directives,
            Function<String, T> builderFunction
    ) {
        Directive deferDirective = NodeUtil.findNodeByName(directives, DeferDirective.getName());

        if (deferDirective != null) {
            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(DeferDirective.getArguments(), deferDirective.getArguments(), CoercedVariables.of(variables), GraphQLContext.getDefault(), Locale.getDefault());

            Object flag = argumentValues.get("if");
            Assert.assertTrue(flag instanceof Boolean, "The '%s' directive MUST have a value for the 'if' argument", DeferDirective.getName());

            if (!((Boolean) flag)) {
                return null;
            }

            Object label = argumentValues.get("label");

            if (label == null) {
                return builderFunction.apply(null);
            }

            Assert.assertTrue(label instanceof String, "The 'label' argument from the '%s' directive MUST contain a String value", DeferDirective.getName());

            return builderFunction.apply((String) label);
        }

        return null;
    }
}
