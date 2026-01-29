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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verification test to compare element counts between built-in SchemaTraverser
 * and manual recursive traversal.
 */
public class SchemaTraversalVerificationTest {

    private static GraphQLSchema schema;

    @BeforeAll
    static void setup() throws Exception {
        InputStream is = SchemaTraversalVerificationTest.class.getClassLoader()
                .getResourceAsStream("extra-large-schema-1.graphqls");
        String schemaString = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        schema = SchemaGenerator.createdMockedSchema(schemaString);
    }

    @Test
    void compareTraversalCounts() throws Exception {
        // Write output to file and stderr for visibility
        PrintStream out = new PrintStream(new FileOutputStream("build/traversal-comparison.txt"));
        PrintStream[] outputs = {System.err, out};
        
        // Run built-in traversal
        print(outputs, "\n=== BUILT-IN TRAVERSAL (SchemaTraverser) ===");
        CountingVisitor visitor = new CountingVisitor();
        new SchemaTraverser().depthFirstFullSchema(visitor, schema);
        ElementCounts builtInCounts = visitor.getCounts();
        print(outputs, builtInCounts.toString());

        // Run manual traversal
        print(outputs, "\n=== MANUAL RECURSIVE TRAVERSAL ===");
        ManualSchemaTraverser manualTraverser = new ManualSchemaTraverser();
        ElementCounts manualCounts = manualTraverser.traverse(schema);
        print(outputs, manualCounts.toString());
        print(outputs, "\nDEBUG INFO:");
        print(outputs, manualTraverser.getDebugInfo());

        // Compare and print
        print(outputs, "\n=== COMPARISON ===");
        print(outputs, "Built-in total: " + builtInCounts.total());
        print(outputs, "Manual total:   " + manualCounts.total());
        print(outputs, "");
        printComparison(outputs, "objectTypes", builtInCounts.objectTypes, manualCounts.objectTypes);
        printComparison(outputs, "interfaceTypes", builtInCounts.interfaceTypes, manualCounts.interfaceTypes);
        printComparison(outputs, "unionTypes", builtInCounts.unionTypes, manualCounts.unionTypes);
        printComparison(outputs, "enumTypes", builtInCounts.enumTypes, manualCounts.enumTypes);
        printComparison(outputs, "enumValues", builtInCounts.enumValues, manualCounts.enumValues);
        printComparison(outputs, "scalarTypes", builtInCounts.scalarTypes, manualCounts.scalarTypes);
        printComparison(outputs, "inputObjectTypes", builtInCounts.inputObjectTypes, manualCounts.inputObjectTypes);
        printComparison(outputs, "inputFields", builtInCounts.inputFields, manualCounts.inputFields);
        printComparison(outputs, "fields", builtInCounts.fields, manualCounts.fields);
        printComparison(outputs, "arguments", builtInCounts.arguments, manualCounts.arguments);
        printComparison(outputs, "directives", builtInCounts.directives, manualCounts.directives);
        printComparison(outputs, "appliedDirectives", builtInCounts.appliedDirectives, manualCounts.appliedDirectives);
        printComparison(outputs, "appliedDirectiveArgs", builtInCounts.appliedDirectiveArguments, manualCounts.appliedDirectiveArguments);
        printComparison(outputs, "listTypes", builtInCounts.listTypes, manualCounts.listTypes);
        printComparison(outputs, "nonNullTypes", builtInCounts.nonNullTypes, manualCounts.nonNullTypes);
        printComparison(outputs, "typeReferences", builtInCounts.typeReferences, manualCounts.typeReferences);
        
        out.close();
    }

    private void print(PrintStream[] outputs, String message) {
        for (PrintStream ps : outputs) {
            ps.println(message);
        }
    }

    private void printComparison(PrintStream[] outputs, String name, int builtIn, int manual) {
        String status = builtIn == manual ? "MATCH" : "DIFF";
        String line = String.format("  %-20s built-in: %6d  manual: %6d  %s", name, builtIn, manual, status);
        print(outputs, line);
    }

    // ==================== COUNTING VISITOR ====================

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

    // ==================== MANUAL TRAVERSER ====================

    /**
     * Manual traversal that mirrors what SchemaTraverser.depthFirstFullSchema does.
     * Key insight: SchemaTraverser visits ALL elements including wrapper types (List, NonNull)
     * each time they appear, but only visits named types once (tracks back-refs).
     */
    private static class ManualSchemaTraverser {
        private final ElementCounts counts = new ElementCounts();
        private final Set<String> visitedNamedTypes = new HashSet<>();

        public ElementCounts traverse(GraphQLSchema schema) {
            debugInfo = new StringBuilder();
            
            // Traverse ALL named types in the schema
            for (GraphQLNamedType type : schema.getAllTypesAsList()) {
                traverseNamedType(type);
            }

            // Traverse directive definitions
            debugInfo.append("schema.getDirectives().size() = ").append(schema.getDirectives().size()).append("\n");
            for (GraphQLDirective directive : schema.getDirectives()) {
                traverseDirective(directive, true);  // true = is definition, traverse argument types
            }

            // Traverse schema-level directives (deprecated but still counted by SchemaTraverser)
            debugInfo.append("schema.getSchemaDirectives().size() = ").append(schema.getSchemaDirectives().size()).append("\n");
            for (GraphQLDirective directive : schema.getSchemaDirectives()) {
                traverseDirective(directive);
            }

            // Traverse schema-level applied directives
            debugInfo.append("schema.getSchemaAppliedDirectives().size() = ").append(schema.getSchemaAppliedDirectives().size()).append("\n");
            for (GraphQLAppliedDirective appliedDirective : schema.getSchemaAppliedDirectives()) {
                traverseAppliedDirective(appliedDirective);
            }

            return counts;
        }
        
        public String getDebugInfo() {
            return debugInfo.toString();
        }
        
        private StringBuilder debugInfo;

        /**
         * Traverse wrapper types (NonNull, List) - these are counted each time they appear.
         * For named types, we only traverse them once (first visit).
         */
        private void traverseType(GraphQLType type) {
            if (type instanceof GraphQLNonNull) {
                counts.nonNullTypes++;
                traverseType(((GraphQLNonNull) type).getWrappedType());
            } else if (type instanceof GraphQLList) {
                counts.listTypes++;
                traverseType(((GraphQLList) type).getWrappedType());
            } else if (type instanceof GraphQLTypeReference) {
                counts.typeReferences++;
                // Don't follow references - the actual type is visited via getAllTypesAsList
            }
            // Named types are NOT traversed here - they're handled via getAllTypesAsList
        }

        private void traverseNamedType(GraphQLNamedType type) {
            String typeName = type.getName();
            if (visitedNamedTypes.contains(typeName)) {
                return; // Already visited this type definition
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
            // Traverse fields
            for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
                traverseFieldDefinition(field);
            }
            // Traverse interfaces - just the wrapper types, named types are visited separately
            for (GraphQLNamedType iface : type.getInterfaces()) {
                // Interface types are visited via getAllTypesAsList, not here
            }
            // Traverse BOTH deprecated directives AND applied directives (like built-in does)
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive);
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
                traverseDirective(directive);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseUnionTypeContents(GraphQLUnionType type) {
            // Member types are visited via getAllTypesAsList
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseEnumTypeContents(GraphQLEnumType type) {
            for (GraphQLEnumValueDefinition value : type.getValues()) {
                counts.enumValues++;
                for (GraphQLDirective directive : value.getDirectives()) {
                    traverseDirective(directive);
                }
                for (GraphQLAppliedDirective directive : value.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive);
            }
            for (GraphQLAppliedDirective directive : type.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        private void traverseScalarTypeContents(GraphQLScalarType type) {
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive);
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
                    traverseDirective(directive);
                }
                for (GraphQLAppliedDirective directive : field.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : type.getDirectives()) {
                traverseDirective(directive);
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
                    traverseDirective(directive);
                }
                for (GraphQLAppliedDirective directive : argument.getAppliedDirectives()) {
                    traverseAppliedDirective(directive);
                }
            }
            for (GraphQLDirective directive : field.getDirectives()) {
                traverseDirective(directive);
            }
            for (GraphQLAppliedDirective directive : field.getAppliedDirectives()) {
                traverseAppliedDirective(directive);
            }
        }

        /**
         * Traverse a directive definition OR an applied directive instance (deprecated GraphQLDirective).
         * The isDefinition flag controls whether we traverse argument types (to avoid double counting).
         */
        private void traverseDirective(GraphQLDirective directive, boolean isDefinition) {
            counts.directives++;

            for (GraphQLArgument argument : directive.getArguments()) {
                counts.arguments++;
                // Only traverse argument types for directive definitions
                // Applied instances share the same type structure, so we'd double count
                if (isDefinition) {
                    traverseType(argument.getType());
                    // Also traverse directives on the argument (for definitions only)
                    for (GraphQLDirective argDirective : argument.getDirectives()) {
                        traverseDirective(argDirective, false);
                    }
                    for (GraphQLAppliedDirective argAppliedDirective : argument.getAppliedDirectives()) {
                        traverseAppliedDirective(argAppliedDirective);
                    }
                }
            }
        }
        
        // Convenience method for applied directive instances
        private void traverseDirective(GraphQLDirective directive) {
            traverseDirective(directive, false);
        }

        private void traverseAppliedDirective(GraphQLAppliedDirective directive) {
            counts.appliedDirectives++;

            for (GraphQLAppliedDirectiveArgument argument : directive.getArguments()) {
                counts.appliedDirectiveArguments++;
            }
        }
    }

    // ==================== RESULT HOLDER ====================

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
