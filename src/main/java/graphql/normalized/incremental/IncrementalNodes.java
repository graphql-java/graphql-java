package graphql.normalized.incremental;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.NodeUtil;
import graphql.language.TypeName;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static graphql.Directives.DeferDirective;

@Internal
public class IncrementalNodes {

    public DeferDeclaration getDeferExecution(
            Map<String, Object> variables,
            List<Directive> directives,
            @Nullable TypeName targetType
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

            String targetTypeName = targetType == null ? null : targetType.getName();

            if (label == null) {
                return new DeferDeclaration(null, targetTypeName);
            }

            Assert.assertTrue(label instanceof String, () -> String.format("The 'label' argument from the '%s' directive MUST contain a String value", DeferDirective.getName()));

            return new DeferDeclaration((String) label, targetTypeName);

        }

        return null;
    }
}
