package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A partial mapping between source and target vertices.
 */
@Internal
public class Mapping {


    // the fixed mappings
    private final BiMap<Vertex, Vertex> fixedMappings;
    private final List<Vertex> fixedSourceList;
    private final List<Vertex> fixedTargetList;

    // partial mappings that are not fixed
    private final BiMap<Vertex, Vertex> map;
    private final List<Vertex> sourceList;
    private final List<Vertex> targetList;
    private final Multimap<Vertex, Vertex> possibleMappings;

    // each possible target vertices per source vertex that is not mapped yet
//    private final Multimap<Vertex,Vertex> possibleMappings;

    private Mapping(Multimap<Vertex, Vertex> possibleMappings,
                    BiMap<Vertex, Vertex> fixedMappings,
                    List<Vertex> fixedSourceList,
                    List<Vertex> fixedTargetList,
                    BiMap<Vertex, Vertex> map,
                    List<Vertex> sourceList,
                    List<Vertex> targetList) {
        this.possibleMappings = possibleMappings;
        this.fixedMappings = fixedMappings;
        this.fixedSourceList = fixedSourceList;
        this.fixedTargetList = fixedTargetList;
        this.map = map;
        this.sourceList = sourceList;
        this.targetList = targetList;
    }

    public static Mapping newMapping(Multimap<Vertex, Vertex> possibleMappings,
                                     BiMap<Vertex, Vertex> fixedMappings,
                                     List<Vertex> fixedSourceList,
                                     List<Vertex> fixedTargetList) {
        return new Mapping(
                possibleMappings,
                fixedMappings,
                fixedSourceList,
                fixedTargetList,
                HashBiMap.create(),
                Collections.emptyList(),
                Collections.emptyList());
    }


    public Vertex getSource(Vertex target) {
        if (fixedMappings.containsValue(target)) {
            return fixedMappings.inverse().get(target);
        }
        return map.inverse().get(target);
    }

    public Vertex getTarget(Vertex source) {
        if (fixedMappings.containsKey(source)) {
            return fixedMappings.get(source);
        }
        return map.get(source);
    }

    public Vertex getSource(int i) {
        if (i < fixedSourceList.size()) {
            return fixedSourceList.get(i);
        }
        return sourceList.get(i - fixedSourceList.size());
    }

    public Vertex getTarget(int i) {
        if (i < fixedTargetList.size()) {
            return fixedTargetList.get(i);
        }
        return targetList.get(i - fixedTargetList.size());
    }

    public boolean containsSource(Vertex sourceVertex) {
        if (fixedMappings.containsKey(sourceVertex)) {
            return true;
        }
        return map.containsKey(sourceVertex);
    }

    public boolean containsTarget(Vertex targetVertex) {
        if (fixedMappings.containsValue(targetVertex)) {
            return true;
        }
        return map.containsValue(targetVertex);
    }


    public boolean contains(Vertex vertex, boolean sourceOrTarget) {
        return sourceOrTarget ? containsSource(vertex) : containsTarget(vertex);
    }


    public int size() {
        return fixedMappings.size() + map.size();
    }

    public int fixedSize() {
        return fixedMappings.size();
    }

    public int nonFixedSize() {
        return map.size();
    }

    public boolean extendMapping(Vertex source, Vertex target, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        this.map.put(source, target);
        this.sourceList.add(source);
        this.targetList.add(target);
        return PossibleMappingsCalculator.extendMapping(map, possibleMappings, source, target, sourceGraph, targetGraph);
    }



    public Mapping copy() {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList);
        List<Vertex> newTargetList = new ArrayList<>(this.targetList);
        Multimap<Vertex, Vertex> possibleMappings = HashMultimap.create(this.possibleMappings);
        return new Mapping(possibleMappings, fixedMappings, fixedSourceList, fixedTargetList, newMap, newSourceList, newTargetList);
    }


    public void forEachTarget(Consumer<? super Vertex> action) {
        for (Vertex t : fixedTargetList) {
            action.accept(t);
        }
        for (Vertex t : targetList) {
            action.accept(t);
        }
    }

    public void forEachNonFixedTarget(Consumer<? super Vertex> action) {
        for (Vertex t : targetList) {
            action.accept(t);
        }
    }

    public void forEachNonFixedSourceAndTarget(BiConsumer<? super Vertex, ? super Vertex> consumer) {
        map.forEach(consumer);
    }

    public Mapping invert() {
        BiMap<Vertex, Vertex> invertedFixedMappings = HashBiMap.create();
        for (Vertex s : fixedMappings.keySet()) {
            Vertex t = fixedMappings.get(s);
            invertedFixedMappings.put(t, s);
        }
        BiMap<Vertex, Vertex> invertedMap = HashBiMap.create();
        for (Vertex s : map.keySet()) {
            Vertex t = map.get(s);
            invertedMap.put(t, s);
        }
        Multimap<Vertex, Vertex> invertedPossibleMappings = HashMultimap.create();
        Multimaps.invertFrom(possibleMappings, invertedPossibleMappings);
        return new Mapping(invertedPossibleMappings, invertedFixedMappings, fixedTargetList, fixedSourceList, invertedMap, targetList, sourceList);
    }
}
