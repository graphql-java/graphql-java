package benchmark;

import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTraverser;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark comparing built-in SchemaTraverser vs manual recursive traversal
 * for counting all schema elements.
 * 
 * Run with: ./gradlew jmh -Pjmh.includes='SchemaTraversalBenchmark'
 * 
 * For memory profiling, add: -Pjmh.profilers='gc'
 * 
 * To verify both approaches count the same elements, run the main method.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SchemaTraversalBenchmark {

    /**
     * Verification method - run this to compare element counts from both approaches.
     */
    public static void main(String[] args) {
        System.out.println("Loading schema...");
        String schemaString = BenchmarkUtils.loadResource("extra-large-schema-1.graphqls");
        GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaString);
        System.out.println("Schema loaded.\n");

        // Run built-in traversal
        System.out.println("=== BUILT-IN TRAVERSAL (SchemaTraverser) ===");
        CountingVisitor visitor = new CountingVisitor();
        new SchemaTraverser().depthFirstFullSchema(visitor, schema);
        ElementCounts builtInCounts = visitor.getCounts();
        System.out.println(builtInCounts);

        // Run manual traversal
        System.out.println("\n=== MANUAL RECURSIVE TRAVERSAL ===");
        ManualSchemaTraverser manualTraverser = new ManualSchemaTraverser();
        ElementCounts manualCounts = manualTraverser.traverse(schema);
        System.out.println(manualCounts);

        // Compare
        System.out.println("\n=== COMPARISON ===");
        System.out.println("Built-in total: " + builtInCounts.total());
        System.out.println("Manual total:   " + manualCounts.total());
        System.out.println();
        compareField("objectTypes", builtInCounts.objectTypes, manualCounts.objectTypes);
        compareField("interfaceTypes", builtInCounts.interfaceTypes, manualCounts.interfaceTypes);
        compareField("unionTypes", builtInCounts.unionTypes, manualCounts.unionTypes);
        compareField("enumTypes", builtInCounts.enumTypes, manualCounts.enumTypes);
        compareField("enumValues", builtInCounts.enumValues, manualCounts.enumValues);
        compareField("scalarTypes", builtInCounts.scalarTypes, manualCounts.scalarTypes);
        compareField("inputObjectTypes", builtInCounts.inputObjectTypes, manualCounts.inputObjectTypes);
        compareField("inputFields", builtInCounts.inputFields, manualCounts.inputFields);
        compareField("fields", builtInCounts.fields, manualCounts.fields);
        compareField("arguments", builtInCounts.arguments, manualCounts.arguments);
        compareField("directives", builtInCounts.directives, manualCounts.directives);
        compareField("appliedDirectives", builtInCounts.appliedDirectives, manualCounts.appliedDirectives);
        compareField("appliedDirectiveArgs", builtInCounts.appliedDirectiveArguments, manualCounts.appliedDirectiveArguments);
        compareField("listTypes", builtInCounts.listTypes, manualCounts.listTypes);
        compareField("nonNullTypes", builtInCounts.nonNullTypes, manualCounts.nonNullTypes);
        compareField("typeReferences", builtInCounts.typeReferences, manualCounts.typeReferences);
    }

    private static void compareField(String name, int builtIn, int manual) {
        String status = builtIn == manual ? "✓" : "✗ DIFF";
        System.out.printf("  %-20s built-in: %6d  manual: %6d  %s%n", name, builtIn, manual, status);
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        GraphQLSchema schema;

        @Setup
        public void setup() {
            try {
                String schemaString = BenchmarkUtils.loadResource("extra-large-schema-1.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ==================== BUILT-IN TRAVERSAL ====================

    /**
     * Uses the built-in SchemaTraverser with a counting visitor.
     * This is the standard graphql-java approach.
     */
    @Benchmark
    public ElementCounts builtInTraversal(BenchmarkState state, Blackhole blackhole) {
        CountingVisitor visitor = new CountingVisitor();
        new SchemaTraverser().depthFirstFullSchema(visitor, state.schema);
        blackhole.consume(visitor);
        return visitor.getCounts();
    }

    /**
     * Visitor that counts all schema elements during built-in traversal.
     */
    private static class CountingVisitor extends GraphQLTypeVisitorStub {
        private final ElementCounts counts = new ElementCounts();

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.objectTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.interfaceTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.unionTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.enumTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
            counts.enumValues++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.scalarTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
            counts.inputObjectTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
            counts.inputFields++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
            counts.fields++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
            counts.arguments++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
            counts.directives++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
            counts.appliedDirectives++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
            counts.appliedDirectiveArguments++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLSchemaElement> context) {
            counts.listTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLSchemaElement> context) {
            counts.nonNullTypes++;
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
            counts.typeReferences++;
            return TraversalControl.CONTINUE;
        }

        public ElementCounts getCounts() {
            return counts;
        }
    }

    // ==================== MANUAL RECURSIVE TRAVERSAL ====================

    /**
     * Manual recursive traversal that iterates over schema.getAllTypesAsList().
     * This mirrors what SchemaTraverser does but with direct iteration instead of visitor pattern.
     */
    @Benchmark
    public ElementCounts manualTraversal(BenchmarkState state, Blackhole blackhole) {
        ManualSchemaTraverser traverser = new ManualSchemaTraverser();
        ElementCounts counts = traverser.traverse(state.schema);
        blackhole.consume(traverser);
        return counts;
    }

    /**
     * Manual traversal that mirrors SchemaTraverser.depthFirstFullSchema behavior.
     */
    private static class ManualSchemaTraverser {
        private final ElementCounts counts = new ElementCounts();
        private final Set<String> visitedNamedTypes = new HashSet<>();

        public ElementCounts traverse(GraphQLSchema schema) {
            // Traverse ALL named types in the schema
            for (GraphQLNamedType type : schema.getAllTypesAsList()) {
                traverseNamedType(type);
            }

            // Traverse directive definitions
            for (GraphQLDirective directive : schema.getDirectives()) {
                traverseDirective(directive, true);
            }

            // Traverse schema-level directives (deprecated but still counted by SchemaTraverser)
            for (GraphQLDirective directive : schema.getSchemaDirectives()) {
                traverseDirective(directive, false);
            }

            // Traverse schema-level applied directives
            for (GraphQLAppliedDirective appliedDirective : schema.getSchemaAppliedDirectives()) {
                traverseAppliedDirective(appliedDirective);
            }

            return counts;
        }

        private void traverseType(GraphQLType type) {
            if (type instanceof GraphQLNonNull) {
                counts.nonNullTypes++;
                traverseType(((GraphQLNonNull) type).getWrappedType());
            } else if (type instanceof GraphQLList) {
                counts.listTypes++;
                traverseType(((GraphQLList) type).getWrappedType());
            } else if (type instanceof GraphQLTypeReference) {
                counts.typeReferences++;
            }
            // Named types are visited via getAllTypesAsList, not here
        }

        private void traverseNamedType(GraphQLNamedType type) {
            String typeName = type.getName();
            if (visitedNamedTypes.contains(typeName)) {
                return;
            }
            visitedNamedTypes.add(typeName);

            if (type instanceof GraphQLObjectType) {
                counts.objectTypes++;
                traverseObjectTypeContents((GraphQLObjectType) type);
            } else if (type instanceof GraphQLInterfaceType) {
                counts.interfaceTypes++;
                traverseInterfaceTypeContents((GraphQLInterfaceType) type);
            } else if (type instanceof GraphQLUnionType) {
                counts.unionTypes++;
                traverseUnionTypeContents((GraphQLUnionType) type);
            } else if (type instanceof GraphQLEnumType) {
                counts.enumTypes++;
                traverseEnumTypeContents((GraphQLEnumType) type);
            } else if (type instanceof GraphQLScalarType) {
                counts.scalarTypes++;
                traverseScalarTypeContents((GraphQLScalarType) type);
            } else if (type instanceof GraphQLInputObjectType) {
                counts.inputObjectTypes++;
                traverseInputObjectTypeContents((GraphQLInputObjectType) type);
            }
        }

        private void traverseObjectTypeContents(GraphQLObjectType type) {
            for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
                traverseFieldDefinition(field);
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseInterfaceTypeContents(GraphQLInterfaceType type) {
            for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
                traverseFieldDefinition(field);
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseUnionTypeContents(GraphQLUnionType type) {
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseEnumTypeContents(GraphQLEnumType type) {
            for (GraphQLEnumValueDefinition value : type.getValues()) {
                counts.enumValues++;
                for (GraphQLDirective directive : value.getDirectives()) {
                    traverseDirective(directive, false);
                }
                for (GraphQLAppliedDirective directive : value.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseScalarTypeContents(GraphQLScalarType type) {
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseInputObjectTypeContents(GraphQLInputObjectType type) {
            for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
                counts.inputFields++;
                traverseType(field.getType());
                for (GraphQLDirective directive : field.getDirectives()) {
                    traverseDirective(directive, false);
                }
                for (GraphQLAppliedDirective directive : field.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseFieldDefinition(GraphQLFieldDefinition field) {
            counts.fields++;
            traverseType(field.getType());

            for (GraphQLArgument argument : field.getArguments()) {
                counts.arguments++;
                traverseType(argument.getType());
                for (GraphQLDirective directive : argument.getDirectives()) {
                    traverseDirective(directive, false);
                }
                for (GraphQLAppliedDirective directive : argument.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : field.getDirectives()) {
                traverseDirective(directive, false);
            }
            for (GraphQLAppliedDirective directive : field.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseDirective(GraphQLDirective directive, boolean isDefinition) {
            counts.directives++;

            for (GraphQLArgument argument : directive.getArguments()) {
                counts.arguments++;
                if (isDefinition) {
                    traverseType(argument.getType());
                    for (GraphQLDirective argDirective : argument.getDirectives()) {
                        traverseDirective(argDirective, false);
                    }
                    for (GraphQLAppliedDirective argAppliedDirective : argument.getAppliedDirectives()) {
                        traverseAppliedDirective(argAppliedDirective);
                    }
                }
            }
        }

        private void traverseAppliedDirective(GraphQLAppliedDirective directive) {
            counts.appliedDirectives++;

            for (GraphQLAppliedDirectiveArgument argument : directive.getArguments()) {
                counts.appliedDirectiveArguments++;
            }
        }
    }

    // ==================== RESULT HOLDER ====================

    /**
     * Holds counts of all schema element types.
     */
    public static class ElementCounts {
        public int objectTypes = 0;
        public int interfaceTypes = 0;
        public int unionTypes = 0;
        public int enumTypes = 0;
        public int enumValues = 0;
        public int scalarTypes = 0;
        public int inputObjectTypes = 0;
        public int inputFields = 0;
        public int fields = 0;
        public int arguments = 0;
        public int directives = 0;
        public int appliedDirectives = 0;
        public int appliedDirectiveArguments = 0;
        public int listTypes = 0;
        public int nonNullTypes = 0;
        public int typeReferences = 0;

        public int total() {
            return objectTypes + interfaceTypes + unionTypes + enumTypes + enumValues +
                    scalarTypes + inputObjectTypes + inputFields + fields + arguments +
                    directives + appliedDirectives + appliedDirectiveArguments +
                    listTypes + nonNullTypes + typeReferences;
        }

        @Override
        public String toString() {
            return "ElementCounts{" +
                    "objectTypes=" + objectTypes +
                    ", interfaceTypes=" + interfaceTypes +
                    ", unionTypes=" + unionTypes +
                    ", enumTypes=" + enumTypes +
                    ", enumValues=" + enumValues +
                    ", scalarTypes=" + scalarTypes +
                    ", inputObjectTypes=" + inputObjectTypes +
                    ", inputFields=" + inputFields +
                    ", fields=" + fields +
                    ", arguments=" + arguments +
                    ", directives=" + directives +
                    ", appliedDirectives=" + appliedDirectives +
                    ", appliedDirectiveArguments=" + appliedDirectiveArguments +
                    ", listTypes=" + listTypes +
                    ", nonNullTypes=" + nonNullTypes +
                    ", typeReferences=" + typeReferences +
                    ", TOTAL=" + total() +
                    '}';
        }
    }
}
