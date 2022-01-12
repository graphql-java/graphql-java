package graphql.schema.usage;

import graphql.PublicApi;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTraverser;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;

import static graphql.Assert.assertNotNull;
import static graphql.util.TraversalControl.CONTINUE;

@PublicApi
public class SchemaUsageSupport {

    /**
     * This builds out {@link SchemaUsage} statistics about the usage of types and directives within a schema
     *
     * @param schema the schema to check
     *
     * @return usage stats
     */
    public static SchemaUsage getSchemaUsage(GraphQLSchema schema) {

        assertNotNull(schema);

        SchemaUsage.Builder builder = new SchemaUsage.Builder();
        GraphQLTypeVisitor visitor = new GraphQLTypeVisitorStub() {

            private BiFunction<String, Integer, Integer> incCount() {
                return (k, v) -> v == null ? 1 : v + 1;
            }

            private void recordBackReference(GraphQLNamedSchemaElement referencedElement, GraphQLSchemaElement referencingElement) {
                String referencedElementName = referencedElement.getName();
                if (referencingElement instanceof GraphQLType) {
                    String typeName = (GraphQLTypeUtil.unwrapAll((GraphQLType) referencingElement)).getName();
                    builder.elementBackReferences.computeIfAbsent(referencedElementName, k -> new HashSet<>()).add(typeName);
                }
                if (referencingElement instanceof GraphQLDirective) {
                    String typeName = ((GraphQLDirective) referencingElement).getName();
                    builder.elementBackReferences.computeIfAbsent(referencedElementName, k -> new HashSet<>()).add(typeName);
                }
            }

            private void memberInterfaces(GraphQLNamedType containingType, List<GraphQLNamedOutputType> members) {
                for (GraphQLNamedOutputType member : members) {
                    builder.interfaceReferenceCount.compute(member.getName(), incCount());
                    builder.interfaceImplementors.computeIfAbsent(member.getName(), k -> new HashSet<>()).add(containingType.getName());

                    recordBackReference(containingType, member);
                }
            }


            @Override
            public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLNamedType inputType = GraphQLTypeUtil.unwrapAll(node.getType());
                builder.argReferenceCount.compute(inputType.getName(), incCount());

                GraphQLSchemaElement parentElement = context.getParentNode();
                if (parentElement instanceof GraphQLFieldDefinition) {
                    parentElement = context.getParentContext().getParentNode();
                }
                recordBackReference(inputType, parentElement);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLNamedType fieldType = GraphQLTypeUtil.unwrapAll(node.getType());
                builder.fieldReferenceCounts.compute(fieldType.getName(), incCount());
                builder.outputFieldReferenceCounts.compute(fieldType.getName(), incCount());

                recordBackReference(fieldType, context.getParentNode());

                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLNamedType fieldType = GraphQLTypeUtil.unwrapAll(node.getType());
                builder.fieldReferenceCounts.compute(fieldType.getName(), incCount());
                builder.inputFieldReferenceCounts.compute(fieldType.getName(), incCount());

                recordBackReference(fieldType, context.getParentNode());

                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLDirective(GraphQLDirective directive, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLSchemaElement parentElement = context.getParentNode();
                if (parentElement != null) {
                    // a null parent is a directive definition
                    // we record a count if the directive is applied to something - not just defined
                    builder.directiveReferenceCount.compute(directive.getName(), incCount());
                }
                if (parentElement instanceof GraphQLArgument) {
                    context = context.getParentContext();
                    parentElement = context.getParentNode();
                }
                if (parentElement instanceof GraphQLFieldDefinition) {
                    context = context.getParentContext();
                    parentElement = context.getParentNode();
                }
                if (parentElement instanceof GraphQLInputObjectField) {
                    context = context.getParentContext();
                    parentElement = context.getParentNode();
                }
                recordBackReference(directive, parentElement);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLUnionType(GraphQLUnionType unionType, TraverserContext<GraphQLSchemaElement> context) {
                List<GraphQLNamedOutputType> members = unionType.getTypes();
                for (GraphQLNamedOutputType member : members) {
                    builder.unionReferenceCount.compute(member.getName(), incCount());

                    recordBackReference(unionType, member);
                }
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType interfaceType, TraverserContext<GraphQLSchemaElement> context) {
                memberInterfaces(interfaceType, interfaceType.getInterfaces());
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                memberInterfaces(objectType, objectType.getInterfaces());
                return CONTINUE;
            }

        };
        new SchemaTraverser().depthFirstFullSchema(visitor, schema);
        return builder.build();
    }

}
