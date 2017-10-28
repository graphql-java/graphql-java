package graphql.execution.instrumentation.export;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.Scalars;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.execution.instrumentation.export.ExportedVariablesCollectionEnvironment.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;

/**
 * This class is experimental and allows an graphql query to @export values as named objects, which would allow
 * you to capture them and feed them back into another query as variable input.
 *
 * For example give:
 *
 * <pre>
 * {@code
 *
 * query NewsFeed {
 *      feed {
 *          stories {
 *              id @export(as: "ids")
 *              actor
 *              message
 *          }
 *      }
 * }
 *
 * query StoryComments {
 *      stories(ids: $ids) {
 *          comments {
 *              actor
 *              message
 *          }
 *      }
 * }
 * }
 * </pre>
 *
 * The list of of id values would be exported as the variable named 'ids' ready to be used as input to another query
 */
@ExperimentalApi
public class ExportedVariablesInstrumentation extends NoOpInstrumentation {

    /**
     * A directive that represents the @export(as:"someName") directive
     */
    public static final GraphQLDirective AT_EXPORT = newDirective()
            .name("export")
            .argument(newArgument().name("as").type(Scalars.GraphQLString))
            .validLocations(Introspection.DirectiveLocation.FIELD)
            .build();


    private final Supplier<ExportedVariablesCollector> collectorSupplier;

    public ExportedVariablesInstrumentation() {
        this(null);
    }

    public ExportedVariablesInstrumentation(Supplier<ExportedVariablesCollector> collectorSupplier) {
        this.collectorSupplier = collectorSupplier;
    }

    @Override
    public InstrumentationState createState() {
        return collectorSupplier.get();
    }

    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        GraphQLDirective atExport = schema.getDirective(AT_EXPORT.getName());
        if (atExport == null) {
            // we add our export directive in for them when this use this instrumentation
            Set<GraphQLDirective> directives = new HashSet<>();
            directives.addAll(schema.getDirectives());
            directives.add(AT_EXPORT);

            schema = schema.transform(builder -> builder.additionalDirectives(directives));
        }
        return schema;
    }

    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        ExportedVariablesCollector collector = parameters.getInstrumentationState();
        Map<String, Object> collectedVariables = collector.getVariables();
        if (collectedVariables.size() > 0) {

            Map<String, Object> extraVariables = new HashMap<>();
            extraVariables.putAll(executionContext.getVariables());
            extraVariables.putAll(collectedVariables);

            executionContext = executionContext.transform(builder -> builder.variables(extraVariables));
        }
        return executionContext;
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteField(InstrumentationFieldCompleteParameters parameters) {
        ExportedVariablesCollector collector = parameters.getInstrumentationState();
        ExecutionStrategyParameters executionStrategyParameters = parameters.getExecutionStrategyParameters();
        return (result, throwable) -> {
            List<Field> fields = executionStrategyParameters.field();
            for (Field field : fields) {
                Directive exportDirective = field.getDirective("export");
                if (exportDirective == null) {
                    return;
                }
                Argument as = exportDirective.getArgument("as");
                if (as == null) {
                    return;
                }
                // we have an export directive - what do they want to export it as
                Value value = as.getValue();
                Assert.assertTrue(value instanceof StringValue, "The export directive MUST return a string value");
                final String exportAsName = ((StringValue) value).getValue();
                result.thenApply(executionResult -> {

                    ExportedVariablesCollectionEnvironment environment = newCollectionEnvironment()
                            .variableName(exportAsName)
                            .variableValue(executionResult.getData())
                            .executionStrategyParameters(executionStrategyParameters)
                            .build();

                    collector.collect(environment);
                    return executionResult;
                });
            }
        };
    }

}
