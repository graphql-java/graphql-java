package graphql.execution.incremental;

import graphql.Assert;
import graphql.DeferredExecutionResult;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ExecutionContext;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.defer.DeferredCall;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.NodeUtil;
import graphql.language.TypeName;
import graphql.schema.GraphQLObjectType;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import static graphql.Directives.DeferDirective;

@Internal
public class IncrementalUtils {
    private IncrementalUtils() {}

    // TODO: Refactor this to reduce duplication with IncrementalNodes
    public static DeferExecution createDeferExecution(
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
