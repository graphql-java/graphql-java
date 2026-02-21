package benchmark;

import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.SchemaGenerator;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SchemaTransformerBenchmark {

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        GraphQLSchema txSchema;

        GraphQLDirective infoDirective = GraphQLDirective.newDirective()
                .name("Info")
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .validLocation(DirectiveLocation.OBJECT)
                .build();
        GraphQLTypeVisitor directiveAdder = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                // add directive
                GraphQLFieldDefinition changedNode = node.transform(builder -> {
                    builder.withDirective(infoDirective);
                });
                return changeNode(context, changedNode);
            }

            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                // add directive info
                GraphQLObjectType changedNode = node.transform(builder -> {
                    builder.withDirective(infoDirective);
                });
                return changeNode(context, changedNode);
            }
        };

        GraphQLTypeVisitor directiveRemover = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                List<GraphQLDirective> filteredDirectives = node.getDirectives().stream()
                        .filter(d -> !d.getName().equals(infoDirective.getName()))
                        .collect(Collectors.toList());
                // remove directive info
                GraphQLFieldDefinition changedNode = node.transform(builder -> {
                    builder.replaceDirectives(filteredDirectives);
                });
                return changeNode(context, changedNode);
            }

            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                List<GraphQLDirective> filteredDirectives = node.getDirectives().stream()
                        .filter(d -> !d.getName().equals(infoDirective.getName()))
                        .collect(Collectors.toList());
                // remove directive info
                GraphQLObjectType changedNode = node.transform(builder -> {
                    builder.replaceDirectives(filteredDirectives);
                });
                return changeNode(context, changedNode);
            }
        };

        @Setup
        public void setup() {
            try {
                String schemaString = BenchmarkUtils.loadResource("large-schema-3.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);
                // Declare the Info directive on the schema so validation passes after transformation
                schema = schema.transform(builder -> builder.additionalDirective(infoDirective));
                txSchema = SchemaTransformer.transformSchema(schema, directiveAdder);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Benchmark
    public GraphQLSchema benchMarkSchemaTransformerAdd(MyState myState) {
        GraphQLSchema schema = myState.schema;
        return SchemaTransformer.transformSchema(schema, myState.directiveAdder);
    }


    @Benchmark
    public GraphQLSchema benchMarkSchemaTransformerRemove(MyState myState) {
        GraphQLSchema schema = myState.txSchema;
        return SchemaTransformer.transformSchema(schema, myState.directiveRemover);
    }
}
