package graphql.execution.nextgen.depgraph;

import graphql.Assert;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static graphql.util.FpKit.map;

public class DependencyGraph {


    public DependencyGraph createDependencyGraph(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {

        FieldCollectorWTC fieldCollector = new FieldCollectorWTC();
        FieldCollectorParameters parameters = FieldCollectorParameters
                .newParameters()
                .schema(graphQLSchema)
                .build();

        Function<MergedFieldWTC, List<MergedFieldWTC>> getChildren = mergedFieldWTC -> {
            return fieldCollector.collectFields(parameters, mergedFieldWTC);
        };

        Traverser<MergedFieldWTC> traverser = Traverser.depthFirst(getChildren);
        List<MergedFieldWTC> roots = fieldCollector.collectFromOperation(parameters, operationDefinition, graphQLSchema.getQueryType());
        traverser.traverse(roots, new TraverserVisitorStub<MergedFieldWTC>() {
            @Override
            public TraversalControl enter(TraverserContext<MergedFieldWTC> context) {
                List<MergedFieldWTC> parentNodes = context.getParentNodes();
                Collections.reverse(parentNodes);

                List<String> keys = map(parentNodes, parentNode -> {
                    return parentNode.getResultKey() + getPossibleObjectTypesString(parentNode, graphQLSchema);
                });
                String queryPath = String.join("/", keys);
                MergedFieldWTC mergedFieldWTC = context.thisNode();
                queryPath = "/" + queryPath + "/" + mergedFieldWTC.getResultKey() + getPossibleObjectTypesString(mergedFieldWTC, graphQLSchema);
//                System.out.println("-----------");
//                System.out.println("visited key:" + context.thisNode().getResultKey());
                System.out.println("query path: " + queryPath + " field type: " + context.thisNode().getFieldDefinition().getType() + " container: " + context.thisNode().getFieldsContainer().getName());
//                System.out.println("visited: " + context.thisNode());

                return TraversalControl.CONTINUE;
            }

        });

        return null;
    }

    private List<String> getPossibleObjectTypesString(MergedFieldWTC mergedFieldWTC, GraphQLSchema graphQLSchema) {
        return map(getPossibleObjectTypes(mergedFieldWTC, graphQLSchema), GraphQLObjectType::getName);
    }

    private List<GraphQLObjectType> getPossibleObjectTypes(MergedFieldWTC mergedFieldWTC, GraphQLSchema graphQLSchema) {
        List<GraphQLObjectType> possibleObjects = getImplicitObjects(graphQLSchema, mergedFieldWTC);

        if (mergedFieldWTC.getTypeConditions().isEmpty()) {
        } else {
            for (GraphQLCompositeType typeCondition : mergedFieldWTC.getTypeConditions()) {
                if (typeCondition instanceof GraphQLObjectType) {
                    possibleObjects.retainAll(Collections.singleton(typeCondition));
                } else if (typeCondition instanceof GraphQLInterfaceType) {
                    possibleObjects.retainAll(graphQLSchema.getImplementations((GraphQLInterfaceType) typeCondition));
                } else if (typeCondition instanceof GraphQLUnionType) {
                    possibleObjects.retainAll(((GraphQLUnionType) typeCondition).getTypes());
                }
            }
        }
        return possibleObjects;
    }

    private List<GraphQLObjectType> getImplicitObjects(GraphQLSchema graphQLSchema, MergedFieldWTC mergedFieldWTC) {
        List<GraphQLObjectType> result = new ArrayList<>();
        GraphQLFieldsContainer fieldsContainer = mergedFieldWTC.getFieldsContainer();
        if (fieldsContainer instanceof GraphQLInterfaceType) {
            result.addAll(graphQLSchema.getImplementations((GraphQLInterfaceType) fieldsContainer));
        } else if (fieldsContainer instanceof GraphQLObjectType) {
            result.add((GraphQLObjectType) fieldsContainer);
        } else {
            return Assert.assertShouldNeverHappen();
        }
        return result;
    }


    public static void main(String[] args) {

    }
}
