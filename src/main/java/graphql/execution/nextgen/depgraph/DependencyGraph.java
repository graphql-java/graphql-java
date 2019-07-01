package graphql.execution.nextgen.depgraph;

import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
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
        List<MergedFieldWTC> roots = fieldCollector.collectFromOperation(parameters, operationDefinition);
        traverser.traverse(roots, new TraverserVisitorStub<MergedFieldWTC>() {
            @Override
            public TraversalControl enter(TraverserContext<MergedFieldWTC> context) {
                List<MergedFieldWTC> parentNodes = context.getParentNodes();
                Collections.reverse(parentNodes);
                List<String> keys = FpKit.map(parentNodes, MergedFieldWTC::getResultKey);
                String queryPath = String.join("/", keys);
                queryPath = "/" + queryPath + "/" + context.thisNode().getResultKey();
                System.out.println("-----------");
                System.out.println("visited key:" + context.thisNode().getResultKey());
                System.out.println("query path: " + queryPath);
                System.out.println("visited: " + context.thisNode());
                return TraversalControl.CONTINUE;
            }

        });

        return null;
    }


    public static void main(String[] args) {

    }
}
