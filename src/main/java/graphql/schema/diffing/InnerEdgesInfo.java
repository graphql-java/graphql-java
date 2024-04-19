package graphql.schema.diffing;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class InnerEdgesInfo {
    private final Multimap<String, Vertex> innerEdgeLabelToHeadV;
    private final Multimap<String, Vertex> innerEdgeLabelToHeadU;
    private Set<SetsMapping> minimumCostConditions;

    public InnerEdgesInfo(Multimap<String, Vertex> innerEdgeLabelToHeadV, Multimap<String, Vertex> innerEdgeLabelToHeadU) {
        this.innerEdgeLabelToHeadV = innerEdgeLabelToHeadV;
        this.innerEdgeLabelToHeadU = innerEdgeLabelToHeadU;
        calcMinimumCostsConditions();
    }

    public Collection<Vertex> headsV() {
        return innerEdgeLabelToHeadV.values();
    }

    public Collection<Vertex> headsU() {
        return innerEdgeLabelToHeadU.values();
    }

    public int minimumCosts() {
        Multiset<String> intersection = Multisets.intersection(innerEdgeLabelToHeadV.keys(), innerEdgeLabelToHeadU.keys());
        int multiSetEditDistance = Math.max(innerEdgeLabelToHeadV.size(), innerEdgeLabelToHeadU.size()) - intersection.size();
        return multiSetEditDistance;
    }

    public int maxCosts() {
        return innerEdgeLabelToHeadV.size() + innerEdgeLabelToHeadU.size();
    }

    public Set<SetsMapping> getMinimumCostConditions() {
        return minimumCostConditions;
    }

    private void calcMinimumCostsConditions() {
        Multiset<String> intersection = Multisets.intersection(innerEdgeLabelToHeadV.keys(), innerEdgeLabelToHeadU.keys());
        this.minimumCostConditions = new LinkedHashSet<>();
        for (String label : intersection.elementSet()) {
            minimumCostConditions.add(new SetsMapping(new LinkedHashSet<>(innerEdgeLabelToHeadV.get(label)), new LinkedHashSet<>(innerEdgeLabelToHeadU.get(label)), true));
        }
        HashMultiset<String> uniqueVLabels = HashMultiset.create(innerEdgeLabelToHeadV.keys());
        Multisets.removeOccurrences(uniqueVLabels, intersection);
        Set<Vertex> vs = new LinkedHashSet<>();
        for (String label : uniqueVLabels) {
            vs.addAll(innerEdgeLabelToHeadV.get(label));
        }

        HashMultiset<String> uniqueULabels = HashMultiset.create(innerEdgeLabelToHeadU.keys());
        Multisets.removeOccurrences(uniqueULabels, intersection);
        Set<Vertex> us = new LinkedHashSet<>();
        for (String label : uniqueULabels) {
            us.addAll(innerEdgeLabelToHeadU.get(label));
        }

        SetsMapping nonMatchingLabelHeads = new SetsMapping(vs, us, false);
        minimumCostConditions.add(nonMatchingLabelHeads);

    }

    public Set<Vertex> relevantSourceVertices() {
        Set<Vertex> result = new LinkedHashSet<>();
        for (SetsMapping setsMapping : minimumCostConditions) {
            result.addAll(setsMapping.from);
        }
        return result;
    }

    public Set<Vertex> relevantTargetVertices() {
        Set<Vertex> result = new LinkedHashSet<>();
        for (SetsMapping setsMapping : minimumCostConditions) {
            result.addAll(setsMapping.to);
        }
        return result;
    }

    public static class SetsMapping {

        boolean sameLabels;
        Set<Vertex> from;
        Set<Vertex> to;

        public SetsMapping(Set<Vertex> from, Set<Vertex> to, boolean sameLabels) {
            this.from = from;
            this.to = to;
            this.sameLabels = sameLabels;
        }
    }
}
