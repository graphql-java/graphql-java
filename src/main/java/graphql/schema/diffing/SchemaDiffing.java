package graphql.schema.diffing;

import graphql.Internal;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.ana.EditOperationAnalysisResult;
import graphql.schema.diffing.ana.EditOperationAnalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.EditorialCostForMapping.editorialCostForMapping;

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
        return diffImpl(sourceGraph, targetGraph).listOfEditOperations;
    }

    public EditOperationAnalysisResult diffAndAnalyze(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        DiffImpl.OptimalEdit optimalEdit = diffImpl(sourceGraph, targetGraph);
        EditOperationAnalyzer editOperationAnalyzer = new EditOperationAnalyzer(graphQLSchema1, graphQLSchema1, sourceGraph, targetGraph);
        return editOperationAnalyzer.analyzeEdits(optimalEdit.listOfEditOperations, optimalEdit.mapping);
    }

    public DiffImpl.OptimalEdit diffGraphQLSchemaAllEdits(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);
    }


    private DiffImpl.OptimalEdit diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        int sizeDiff = targetGraph.size() - sourceGraph.size();
        FillupIsolatedVertices fillupIsolatedVertices = new FillupIsolatedVertices(sourceGraph, targetGraph, runningCheck);
        fillupIsolatedVertices.ensureGraphAreSameSize();
        FillupIsolatedVertices.IsolatedVertices isolatedVertices = fillupIsolatedVertices.getIsolatedVertices();

        assertTrue(sourceGraph.size() == targetGraph.size());
//        if (sizeDiff != 0) {
//            SortSourceGraph.sortSourceGraph(sourceGraph, targetGraph, isolatedVertices);
//        }
        Mapping fixedMappings = isolatedVertices.mapping;
        if (fixedMappings.size() == sourceGraph.size()) {
            List<EditOperation> result = new ArrayList<>();
            editorialCostForMapping(fixedMappings, sourceGraph, targetGraph, result);
            return new DiffImpl.OptimalEdit(fixedMappings, result, result.size());
        }

        DiffImpl diffImpl = new DiffImpl(sourceGraph, targetGraph, isolatedVertices, runningCheck);
        List<Vertex> nonMappedSource = new ArrayList<>(sourceGraph.getVertices());
        nonMappedSource.removeAll(fixedMappings.getSources());

        List<Vertex> nonMappedTarget = new ArrayList<>(targetGraph.getVertices());
        nonMappedTarget.removeAll(fixedMappings.getTargets());

        runningCheck.check();
        sortListBasedOnPossibleMapping(nonMappedSource, isolatedVertices);

        // the non mapped vertices go to the end
        List<Vertex> sourceVertices = new ArrayList<>();
        sourceVertices.addAll(fixedMappings.getSources());
        sourceVertices.addAll(nonMappedSource);

        List<Vertex> targetGraphVertices = new ArrayList<>();
        targetGraphVertices.addAll(fixedMappings.getTargets());
        targetGraphVertices.addAll(nonMappedTarget);


        DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(fixedMappings, sourceVertices, targetGraphVertices);
        return optimalEdit;
    }

    private void sortListBasedOnPossibleMapping(List<Vertex> sourceVertices, FillupIsolatedVertices.IsolatedVertices isolatedVertices) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
            int v2Count = isolatedVertices.possibleMappings.get(v2).size();
            int v1Count = isolatedVertices.possibleMappings.get(v1).size();
            return Integer.compare(v2Count, v1Count);
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
