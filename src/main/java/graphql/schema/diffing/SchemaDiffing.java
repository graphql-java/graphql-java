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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        return diffImpl(sourceGraph, targetGraph, new AtomicInteger()).getListOfEditOperations();
    }

    public EditOperationAnalysisResult diffAndAnalyze(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        DiffImpl.OptimalEdit optimalEdit = diffImpl(sourceGraph, targetGraph, new AtomicInteger());
        EditOperationAnalyzer editOperationAnalyzer = new EditOperationAnalyzer(graphQLSchema1, graphQLSchema1, sourceGraph, targetGraph);
        return editOperationAnalyzer.analyzeEdits(optimalEdit.getListOfEditOperations(), optimalEdit.mapping);
    }

    public DiffImpl.OptimalEdit diffGraphQLSchemaAllEdits(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2, AtomicInteger algoIterationCount) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph, algoIterationCount);
    }


    private DiffImpl.OptimalEdit diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph, AtomicInteger algoIterationCount) throws Exception {
        PossibleMappingsCalculator possibleMappingsCalculator = new PossibleMappingsCalculator(sourceGraph, targetGraph, runningCheck);
        PossibleMappingsCalculator.PossibleMappings possibleMappings = possibleMappingsCalculator.calculate();

        Mapping startMapping = Mapping.newMapping(
                possibleMappingsCalculator.getFixedParentRestrictions(),
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

        int isolatedSourceCount = (int) nonMappedSource.stream().filter(Vertex::isIsolated).count();
        int isolatedTargetCount = (int) nonMappedTarget.stream().filter(Vertex::isIsolated).count();
        if (isolatedTargetCount > isolatedSourceCount) {
            // we flip source and target because the algo works much faster with
            // this way for delete heavy graphs
            BiMap<Vertex, Vertex> fixedOneToOneInverted = HashBiMap.create();
            for (Vertex s : possibleMappings.fixedOneToOneMappings.keySet()) {
                Vertex t = possibleMappings.fixedOneToOneMappings.get(s);
                fixedOneToOneInverted.put(t, s);
            }
            Mapping startMappingInverted = Mapping.newMapping(
                    possibleMappingsCalculator.getFixedParentRestrictionsInverse(fixedOneToOneInverted),
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

            sortVertices(nonMappedTarget, targetGraph, possibleMappings);

            DiffImpl diffImpl = new DiffImpl(possibleMappingsCalculator, targetGraph, sourceGraph, possibleMappings, runningCheck);
            DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(startMappingInverted, targetVertices, sourceVertices, algoIterationCount);
            DiffImpl.OptimalEdit invertedBackOptimalEdit = new DiffImpl.OptimalEdit(sourceGraph, targetGraph, optimalEdit.mapping.invert(), optimalEdit.ged);
            return invertedBackOptimalEdit;
        } else {
            sortVertices(nonMappedSource, sourceGraph, possibleMappings);

            List<Vertex> sourceVertices = new ArrayList<>();
            sourceVertices.addAll(possibleMappings.fixedOneToOneSources);
            sourceVertices.addAll(nonMappedSource);

            List<Vertex> targetVertices = new ArrayList<>();
            targetVertices.addAll(possibleMappings.fixedOneToOneTargets);
            targetVertices.addAll(nonMappedTarget);

            DiffImpl diffImpl = new DiffImpl(possibleMappingsCalculator, sourceGraph, targetGraph, possibleMappings, runningCheck);
            DiffImpl.OptimalEdit optimalEdit = diffImpl.diffImpl(startMapping, sourceVertices, targetVertices, algoIterationCount);
            return optimalEdit;
        }
    }


    private void sortVertices(List<Vertex> vertices, SchemaGraph schemaGraph, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
        Comparator<Vertex> vertexComparator = Comparator.comparing(schemaGraph::adjacentEdgesAndInverseCount).reversed();
        vertices.sort(vertexComparator);
    }
}
