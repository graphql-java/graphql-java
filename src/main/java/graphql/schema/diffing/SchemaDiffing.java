package graphql.schema.diffing;

import graphql.Internal;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.ana.EditOperationAnalysisResult;
import graphql.schema.diffing.ana.EditOperationAnalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.EditorialCostForMapping.baseEditorialCostForMapping;

@Internal
public class SchemaDiffing {
    private final SchemaDiffingRunningCheck runningCheck = new SchemaDiffingRunningCheck();

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    /**
     * Tries to stop the algorithm from execution ASAP by throwing a
     * {@link SchemaDiffingCancelledException}.
     */
    public void stop() {
        runningCheck.stop();
    }

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph).getListOfEditOperations();
    }

    public EditOperationAnalysisResult diffAndAnalyze(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        DiffImpl.OptimalEdit optimalEdit = diffImpl(sourceGraph, targetGraph);
        EditOperationAnalyzer editOperationAnalyzer = new EditOperationAnalyzer(graphQLSchema1, graphQLSchema1, sourceGraph, targetGraph);
        return editOperationAnalyzer.analyzeEdits(optimalEdit.getListOfEditOperations(), optimalEdit.mapping);
    }

    public DiffImpl.OptimalEdit diffGraphQLSchemaAllEdits(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);
    }


    private DiffImpl.OptimalEdit diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        PossibleMappingsCalculator possibleMappingsCalculator = new PossibleMappingsCalculator(sourceGraph, targetGraph, runningCheck);
        PossibleMappingsCalculator.PossibleMappings possibleMappings = possibleMappingsCalculator.calculate();

        Mapping fixedMappings = Mapping.newMapping(
                possibleMappings.fixedOneToOneMappings,
                possibleMappings.fixedOneToOneSources,
                possibleMappings.fixedOneToOneTargets);

        assertTrue(sourceGraph.size() == targetGraph.size());
        if (possibleMappings.fixedOneToOneMappings.size() == sourceGraph.size()) {
            return new DiffImpl.OptimalEdit(sourceGraph, targetGraph, fixedMappings, baseEditorialCostForMapping(fixedMappings, sourceGraph, targetGraph));
        }

        DiffImpl diffImpl = new DiffImpl(sourceGraph, targetGraph, possibleMappings, runningCheck);
        List<Vertex> nonMappedSource = new ArrayList<>(sourceGraph.getVertices());
        nonMappedSource.removeAll(possibleMappings.fixedOneToOneSources);

        List<Vertex> nonMappedTarget = new ArrayList<>(targetGraph.getVertices());
        nonMappedTarget.removeAll(possibleMappings.fixedOneToOneTargets);


        runningCheck.check();
        sortSourceVertices(nonMappedSource, possibleMappings);

        // the non mapped vertices go to the end
        List<Vertex> sourceVertices = new ArrayList<>();
        sourceVertices.addAll(possibleMappings.fixedOneToOneSources);
        sourceVertices.addAll(nonMappedSource);

        List<Vertex> targetGraphVertices = new ArrayList<>();
        targetGraphVertices.addAll(possibleMappings.fixedOneToOneTargets);
        targetGraphVertices.addAll(nonMappedTarget);


        DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(fixedMappings, sourceVertices, targetGraphVertices);
        return optimalEdit;
    }

    private void sortSourceVertices(List<Vertex> sourceVertices, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
//            int v1Count = possibleMappings.possibleMappings.get(v1).size();
//            int v2Count = possibleMappings.possibleMappings.get(v2).size();
//            int result =  Integer.compare(v1Count, v2Count);
//            if(result == 0) {
            return Integer.compare(sourceGraph.adjacentEdgesAndInverseCount(v2), sourceGraph.adjacentEdgesAndInverseCount(v1));
//            }
//            return result;
        });
    }


    private List<EditOperation> calcEdgeOperations(Mapping mapping) {
        List<Edge> edges = sourceGraph.getEdges();
        List<EditOperation> result = new ArrayList<>();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            Vertex target1 = mapping.getTarget(sourceEdge.getFrom());
            Vertex target2 = mapping.getTarget(sourceEdge.getTo());
            Edge targetEdge = targetGraph.getEdge(target1, target2);
            if (targetEdge == null) {
                result.add(EditOperation.deleteEdge("Delete edge " + sourceEdge, sourceEdge));
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                result.add(EditOperation.changeEdge("Change " + sourceEdge + " to " + targetEdge, sourceEdge, targetEdge));
            }
        }

        //TODO: iterates over all edges in the target Graph
        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            Vertex sourceFrom = mapping.getSource(targetEdge.getFrom());
            Vertex sourceTo = mapping.getSource(targetEdge.getTo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                result.add(EditOperation.insertEdge("Insert edge " + targetEdge, targetEdge));
            }
        }
        return result;
    }
}
