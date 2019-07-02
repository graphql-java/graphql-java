package graphql.execution.nextgen.depgraph;

import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

                List<String> keys = FpKit.map(parentNodes, parentNode -> {
                    if (parentNode.getTypeConditions().isEmpty()) {
                        return parentNode.getResultKey();
                    } else {
                        return parentNode.getResultKey() + parentNode.getTypeConditions().toString();
                    }
                });
                String queryPath = String.join("/", keys);
                MergedFieldWTC mergedFieldWTC = context.thisNode();
                queryPath = "/" + queryPath + "/" + mergedFieldWTC.getResultKey();
                if (!mergedFieldWTC.getTypeConditions().isEmpty()) {
                    queryPath += mergedFieldWTC.getTypeConditions();
                }
//                System.out.println("-----------");
//                System.out.println("visited key:" + context.thisNode().getResultKey());
                System.out.println("query path: " + queryPath + " field type: " + context.thisNode().getFieldDefinition().getType() + " container: " + context.thisNode().getFieldsContainer().getName());
//                System.out.println("visited: " + context.thisNode());


                // TODO: only the most specific type condition is relevant
                List<String> typeConditions = mergedFieldWTC.getTypeConditions();
                for (String typeCondition : typeConditions) {
                    GraphQLType type = graphQLSchema.getType(typeCondition);
                }
                return TraversalControl.CONTINUE;
            }

        });

        return null;
    }


    public static void main(String[] args) {

    }
}
