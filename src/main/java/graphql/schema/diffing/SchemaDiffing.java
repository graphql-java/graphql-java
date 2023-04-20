package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
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

        Mapping startMapping = Mapping.newMapping(
                possibleMappings.fixedOneToOneMappings,
                possibleMappings.fixedOneToOneSources,
                possibleMappings.fixedOneToOneTargets);

        assertTrue(sourceGraph.size() == targetGraph.size());
        if (possibleMappings.fixedOneToOneMappings.size() == sourceGraph.size()) {
            return new DiffImpl.OptimalEdit(sourceGraph, targetGraph, startMapping, baseEditorialCostForMapping(startMapping, sourceGraph, targetGraph));
        }

        List<Vertex> nonMappedSource = new ArrayList<>(sourceGraph.getVertices());
        nonMappedSource.removeAll(possibleMappings.fixedOneToOneSources);

        List<Vertex> nonMappedTarget = new ArrayList<>(targetGraph.getVertices());
        nonMappedTarget.removeAll(possibleMappings.fixedOneToOneTargets);

        runningCheck.check();


//        System.out.println("sort by mapping count: ");
//        for (Vertex v : nonMappedSource) {
//            System.out.println(possibleMappings.possibleMappings.get(v).size() + " for " + v);
//        }
//        System.out.println("=------------");
//        ArrayList<Vertex> copy = new ArrayList<>(nonMappedSource);
//        sortSourceVerticesEdgeCountDescending(copy, possibleMappings);
//        System.out.println("sort by edge count ");
//        for (Vertex v : copy) {
//            System.out.println(sourceGraph.adjacentEdgesAndInverseCount(v) + " for " + v);
//        }
//        System.out.println("------------");
//
        // the non mapped vertices go to the end

        int isolatedSourceCount = (int) nonMappedSource.stream().filter(Vertex::isIsolated).count();
        int isolatedTargetCount = (int) nonMappedTarget.stream().filter(Vertex::isIsolated).count();
        if (isolatedTargetCount > isolatedSourceCount) {
            System.out.println("delete heavy ... invert source and target graph");
            // we flip source and target
            BiMap<Vertex, Vertex> fixedOneToOneInverted = HashBiMap.create();
            for (Vertex s : possibleMappings.fixedOneToOneMappings.keySet()) {
                Vertex t = possibleMappings.fixedOneToOneMappings.get(s);
                fixedOneToOneInverted.put(t, s);
            }
            Mapping startMappingInverted = Mapping.newMapping(
                    fixedOneToOneInverted,
                    possibleMappings.fixedOneToOneTargets,
                    possibleMappings.fixedOneToOneSources
            );
            HashMultimap<Vertex, Vertex> invertedPossibleOnes = HashMultimap.create();
            Multimaps.invertFrom(possibleMappings.possibleMappings, invertedPossibleOnes);
            possibleMappings.possibleMappings = invertedPossibleOnes;

            List<Vertex> sourceVertices = new ArrayList<>();
            sourceVertices.addAll(possibleMappings.fixedOneToOneSources);
            sourceVertices.addAll(nonMappedSource);

            List<Vertex> targetVertices = new ArrayList<>();
            targetVertices.addAll(possibleMappings.fixedOneToOneTargets);
            targetVertices.addAll(nonMappedTarget);

            sortVerticesEdgeCountDescending(nonMappedTarget, targetGraph);
            DiffImpl diffImpl = new DiffImpl(targetGraph, sourceGraph, possibleMappings, runningCheck);
            DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(startMappingInverted, targetVertices, sourceVertices);
            DiffImpl.OptimalEdit invertedBackOptimalEdit = new DiffImpl.OptimalEdit(sourceGraph, targetGraph, optimalEdit.mapping.invert(), optimalEdit.ged);
            return invertedBackOptimalEdit;

        } else {
            System.out.println("Insert heavy ... normal way");
            sortVerticesEdgeCountDescending(nonMappedSource, sourceGraph);

            List<Vertex> sourceVertices = new ArrayList<>();
            sourceVertices.addAll(possibleMappings.fixedOneToOneSources);
            sourceVertices.addAll(nonMappedSource);

            List<Vertex> targetVertices = new ArrayList<>();
            targetVertices.addAll(possibleMappings.fixedOneToOneTargets);
            targetVertices.addAll(nonMappedTarget);

            DiffImpl diffImpl = new DiffImpl(sourceGraph, targetGraph, possibleMappings, runningCheck);
            DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(startMapping, sourceVertices, targetVertices);
            return optimalEdit;
        }
    }

    private void sortSourceVertices(List<Vertex> sourceVertices, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
            int v1Count = possibleMappings.possibleMappings.get(v1).size();
            int v2Count = possibleMappings.possibleMappings.get(v2).size();
//            if (v1Count == v2Count) {
//                return Integer.compare(sourceGraph.adjacentEdgesAndInverseCount(v1), sourceGraph.adjacentEdgesAndInverseCount(v2));
//            }
            int result = Integer.compare(v1Count, v2Count);
            return result;
        });
    }

    private void sortSourceVerticesPossibleMappingDescending(List<Vertex> sourceVertices, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
            int v1Count = possibleMappings.possibleMappings.get(v1).size();
            int v2Count = possibleMappings.possibleMappings.get(v2).size();
            int result = Integer.compare(v2Count, v1Count);
            return result;
        });
    }

    private void sortVerticesEdgeCountDescending(List<Vertex> vertices, SchemaGraph schemaGraph) {
        Collections.sort(vertices, (v1, v2) ->
        {
            return Integer.compare(schemaGraph.adjacentEdgesAndInverseCount(v2), schemaGraph.adjacentEdgesAndInverseCount(v1));
        });
    }

//    private void sortSourceVerticesDeleteHeavy(List<Vertex> sourceVertices, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
//        Collections.sort(sourceVertices, (v1, v2) ->
//        {
//            int targetIsolated1 = (int) possibleMappings.possibleMappings.get(v1).stream().filter(Vertex::isIsolated).count();
//            int targetIsolated2 = (int) possibleMappings.possibleMappings.get(v2).stream().filter(Vertex::isIsolated).count();
//            return Integer.compare(targetIsolated1, targetIsolated2);
//        });
//    }


    private void sortSourceVerticesEdgeCountAscending(List<Vertex> sourceVertices, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
            return Integer.compare(sourceGraph.adjacentEdgesAndInverseCount(v1), sourceGraph.adjacentEdgesAndInverseCount(v2));
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
