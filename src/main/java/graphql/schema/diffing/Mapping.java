package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import graphql.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Mapping {
    private BiMap<Vertex, Vertex> map = HashBiMap.create();
    private List<Vertex> sourceList = new ArrayList<>();
    private List<Vertex> targetList = new ArrayList<>();

    private Mapping(BiMap<Vertex, Vertex> map, List<Vertex> sourceList, List<Vertex> targetList) {
        this.map = map;
        this.sourceList = sourceList;
        this.targetList = targetList;
    }

    public Mapping() {

    }

    public Vertex getSource(Vertex target) {
        return map.inverse().get(target);
    }

    public Vertex getTarget(Vertex source) {
        return map.get(source);
    }

    public Vertex getSource(int i) {
        return sourceList.get(i);
    }

    public Vertex getTarget(int i) {
        return targetList.get(i);
    }

    public List<Vertex> getTargets() {
        return targetList;
    }

    public List<Vertex> getSources() {
        return sourceList;
    }

    public boolean containsSource(Vertex sourceVertex) {
        return map.containsKey(sourceVertex);
    }

    public boolean containsTarget(Vertex targetVertex) {
        return map.containsValue(targetVertex);
    }

    public int size() {
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
        return new Mapping(newMap, newSourceList, newTargetList);
    }

    public Mapping copy() {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList);
        List<Vertex> newTargetList = new ArrayList<>(this.targetList);
        return new Mapping(newMap, newSourceList, newTargetList);
    }

    public Mapping extendMapping(Vertex source, Vertex target) {
        HashBiMap<Vertex, Vertex> newMap = HashBiMap.create(map);
        newMap.put(source, target);
        List<Vertex> newSourceList = new ArrayList<>(this.sourceList);
        newSourceList.add(source);
        List<Vertex> newTargetList = new ArrayList<>(this.targetList);
        newTargetList.add(target);
        return new Mapping(newMap, newSourceList, newTargetList);
    }

    public BiMap<Vertex, Vertex> getMap() {
        return map;
    }
}
