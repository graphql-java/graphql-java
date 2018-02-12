package graphql.execution.instrumentation.export;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.Scalars;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationPreExecutionState;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.Assert.assertTrue;
import static graphql.execution.instrumentation.export.ExportedVariablesCollectionEnvironment.newCollectionEnvironment;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;
import static java.util.Optional.ofNullable;

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
 * The list of id values would be exported as the variable named 'ids' ready to be used as input to another query
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

    /**
     * This will use the supplier to give it collectors each time a new query is run
     *
     * @param collectorSupplier the supplier of collectors
     */
    public ExportedVariablesInstrumentation(Supplier<ExportedVariablesCollector> collectorSupplier) {
        this.collectorSupplier = collectorSupplier;
    }

    @Override
    public InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
        return collectorSupplier.get();
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return parameters.getPreExecutionState();
    }

    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        ExportedVariablesCollector collector = parameters.getPreExecutionState();
        Map<String, Object> variables = ofNullable(executionInput.getVariables()).orElse(new LinkedHashMap<>());

        Map<String, Object> newVariables = new LinkedHashMap<>(variables);
        newVariables.putAll(collector.getVariables());

        return executionInput.transform(builder -> builder.variables(newVariables));
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
                assertTrue(value instanceof StringValue, "The export directive MUST return a string value");

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
