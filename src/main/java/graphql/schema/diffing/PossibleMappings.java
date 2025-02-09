package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import graphql.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.schema.diffing.SchemaGraph.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraph.APPLIED_DIRECTIVE;

public class PossibleMappings {

    // only used for debugging
    public Table<List<String>, Set<Vertex>, Set<Vertex>> contexts = HashBasedTable.create();

    public Multimap<Vertex, Vertex> possibleMappings = HashMultimap.create();


    // used when schema graph are created
    public Set<Vertex> allIsolatedSource = new LinkedHashSet<>();
    public Set<Vertex> allIsolatedTarget = new LinkedHashSet<>();

    public BiMap<Vertex, Vertex> fixedOneToOneMappings = HashBiMap.create();
    public List<Vertex> fixedOneToOneSources = new ArrayList<>();
    public List<Vertex> fixedOneToOneTargets = new ArrayList<>();

    public void putPossibleMappings(List<String> contextId,
                                    Collection<Vertex> sourceVertices,
                                    Collection<Vertex> targetVertices,
                                    String typeName) {
        if (sourceVertices.isEmpty() && targetVertices.isEmpty()) {
            return;
        }

        if (sourceVertices.size() == 1 && targetVertices.size() == 1) {
            Vertex sourceVertex = sourceVertices.iterator().next();
            Vertex targetVertex = targetVertices.iterator().next();
            fixedOneToOneMappings.put(sourceVertex, targetVertex);
            fixedOneToOneSources.add(sourceVertex);
            fixedOneToOneTargets.add(targetVertex);
            return;
        }


        // don't try find optimal mappings for these vertices: just delete or insert them
        if (APPLIED_DIRECTIVE.equals(typeName) || APPLIED_ARGUMENT.equals(typeName)) {
            for (Vertex sourceVertex : sourceVertices) {
                Vertex isolatedTarget = Vertex.newIsolatedNode("target-isolated-" + typeName);
                allIsolatedTarget.add(isolatedTarget);
                fixedOneToOneMappings.put(sourceVertex, isolatedTarget);
                fixedOneToOneSources.add(sourceVertex);
                fixedOneToOneTargets.add(isolatedTarget);
            }
            for (Vertex targetVertex : targetVertices) {
                Vertex isolatedSource = Vertex.newIsolatedNode("source-isolated-" + typeName);
                allIsolatedSource.add(isolatedSource);
                fixedOneToOneMappings.put(isolatedSource, targetVertex);
                fixedOneToOneSources.add(isolatedSource);
                fixedOneToOneTargets.add(targetVertex);
            }
            return;
        }

        Set<Vertex> newIsolatedSource = Vertex.newIsolatedNodes(targetVertices.size(), "source-isolated-" + typeName + "-");
        Set<Vertex> newIsolatedTarget = Vertex.newIsolatedNodes(sourceVertices.size(), "target-isolated-" + typeName + "-");
        this.allIsolatedSource.addAll(newIsolatedSource);
        this.allIsolatedTarget.addAll(newIsolatedTarget);

        if (sourceVertices.size() == 0) {
            Iterator<Vertex> iterator = newIsolatedSource.iterator();
            for (Vertex targetVertex : targetVertices) {
                Vertex isolatedSourceVertex = iterator.next();
                fixedOneToOneMappings.put(isolatedSourceVertex, targetVertex);
                fixedOneToOneSources.add(isolatedSourceVertex);
                fixedOneToOneTargets.add(targetVertex);
            }
            return;
        }
        if (targetVertices.size() == 0) {
            Iterator<Vertex> iterator = newIsolatedTarget.iterator();
            for (Vertex sourceVertex : sourceVertices) {
                Vertex isolatedTargetVertex = iterator.next();
                fixedOneToOneMappings.put(sourceVertex, isolatedTargetVertex);
                fixedOneToOneSources.add(sourceVertex);
                fixedOneToOneTargets.add(isolatedTargetVertex);
            }
            return;
        }

        Assert.assertFalse(contexts.containsRow(contextId));

//        Set<Vertex> allSource = new LinkedHashSet<>();
//        allSource.addAll(sourceVertices);
//        allSource.addAll(newIsolatedSource);
//        Set<Vertex> allTarget = new LinkedHashSet<>();
//        allTarget.addAll(targetVertices);
//        allTarget.addAll(newIsolatedTarget);
//        contexts.put(contextId, allSource, allTarget);

        // every source can be mapped to every target or can be deleted
        Iterator<Vertex> iteratorIsolatedTarget = newIsolatedTarget.iterator();
        for (
                Vertex sourceVertex : sourceVertices) {
            possibleMappings.putAll(sourceVertex, targetVertices);
            possibleMappings.put(sourceVertex, iteratorIsolatedTarget.next());
        }

        // every target can also be inserted
        Iterator<Vertex> iteratorIsolatedSource = newIsolatedSource.iterator();
        for (
                Vertex targetVertex : targetVertices) {
            possibleMappings.put(iteratorIsolatedSource.next(), targetVertex);
        }

        // all isolated source can map to all isolated target
        for (
                Vertex isolatedSource : newIsolatedSource) {
            possibleMappings.putAll(isolatedSource, newIsolatedTarget);
        }
    }

    public boolean mappingPossible(Vertex sourceVertex, Vertex targetVertex) {
        return possibleMappings.containsEntry(sourceVertex, targetVertex);
    }

    public Collection<Vertex> possibleTargets(Vertex sourceVertex) {
        return possibleMappings.get(sourceVertex);
    }
}

