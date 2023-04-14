package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A mapping (in the math sense) from a list of vertices to another list of
 * vertices.
 * A mapping can semantically mean a change, but doesn't have to: a vertex
 * can be mapped to the same vertex (semantically the same, Java object wise they are different).
 */
@Internal
public class Mapping {


    private final BiMap<Vertex, Vertex> fixedMappings;
    private final List<Vertex> fixedSourceList;
    private final List<Vertex> fixedTargetList;

    private final BiMap<Vertex, Vertex> map;
    private final List<Vertex> sourceList;
    private final List<Vertex> targetList;

    private Mapping(BiMap<Vertex, Vertex> fixedMappings,
                    List<Vertex> fixedSourceList,
                    List<Vertex> fixedTargetList,
                    BiMap<Vertex, Vertex> map,
                    List<Vertex> sourceList,
                    List<Vertex> targetList) {
        this.fixedMappings = fixedMappings;
        this.fixedSourceList = fixedSourceList;
        this.fixedTargetList = fixedTargetList;
        this.map = map;
        this.sourceList = sourceList;
        this.targetList = targetList;
    }

    public static Mapping newMapping(BiMap<Vertex, Vertex> fixedMappings, List<Vertex> fixedSourceList, List<Vertex> fixedTargetList) {
        return new Mapping(fixedMappings, fixedSourceList, fixedTargetList, HashBiMap.create(), Collections.emptyList(), Collections.emptyList());

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

    public int nonFixedSize() {
        return map.size();
    }

    public void add(Vertex source, Vertex target) {
        this.map.put(source, target);
        this.sourceList.add(source);
        this.targetList.add(target);
    }

    public Mapping removeLastElement() {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        newMap.remove(this.sourceList.get(this.sourceList.size() - 1));
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList.subList(0, this.sourceList.size() - 1));
        List<Vertex> newTargetList = new ArrayList<>(this.targetList.subList(0, this.targetList.size() - 1));
        return new Mapping(fixedMappings, fixedSourceList, fixedTargetList, newMap, newSourceList, newTargetList);
    }

    public Mapping copy() {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList);
        List<Vertex> newTargetList = new ArrayList<>(this.targetList);
        return new Mapping(fixedMappings, fixedSourceList, fixedTargetList, newMap, newSourceList, newTargetList);
    }

    public Mapping extendMapping(Vertex source, Vertex target) {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        newMap.put(source, target);
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList);
        newSourceList.add(source);
        List<Vertex> newTargetList = new ArrayList<>(this.targetList);
        newTargetList.add(target);
        return new Mapping(fixedMappings, fixedSourceList, fixedTargetList, newMap, newSourceList, newTargetList);
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
}
