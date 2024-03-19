package graphql.util;

import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.Edge;
import graphql.schema.diffing.SchemaGraph;
import graphql.schema.diffing.SchemaGraphFactory;
import graphql.schema.diffing.Vertex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds all cycles in a GraphQL Schema.
 * Cycles caused by built-in introspection types are filtered out.
 */
@ExperimentalApi
public class CyclicSchemaAnalyzer {

    public static class SchemaCycle {
        private final List<String> cycle;

        public SchemaCycle(List<String> cycle) {
            this.cycle = cycle;
        }

        public int size() {
            return cycle.size();
        }

        public List<String> getCycle() {
            return cycle;
        }

        @Override
        public String toString() {
            return cycle.toString();
        }
    }

    public static List<SchemaCycle> findCycles(GraphQLSchema schema) {
        return findCycles(schema, true);
    }

    public static List<SchemaCycle> findCycles(GraphQLSchema schema, boolean filterOutIntrospectionCycles) {
        FindCyclesImpl findCyclesImpl = new FindCyclesImpl(schema);
        findCyclesImpl.findAllSimpleCyclesImpl();
        List<List<Vertex>> vertexCycles = findCyclesImpl.foundCycles;
        if (filterOutIntrospectionCycles) {
            vertexCycles = vertexCycles.stream().filter(vertices -> {
                for (Vertex vertex : vertices) {
                    if (Introspection.isIntrospectionTypes(vertex.getName())) {
                        return false;
                    }
                }
                return true;
            }).collect(Collectors.toList());
        }
        List<SchemaCycle> result = new ArrayList<>();
        for (List<Vertex> vertexCycle : vertexCycles) {
            List<String> stringCycle = new ArrayList<>();
            for (Vertex vertex : vertexCycle) {
                if (vertex.isOfType(SchemaGraph.OBJECT) || vertex.isOfType(SchemaGraph.INTERFACE) || vertex.isOfType(SchemaGraph.UNION)) {
                    stringCycle.add(vertex.getName());
                } else if (vertex.isOfType(SchemaGraph.FIELD)) {
                    String fieldsContainerName = findCyclesImpl.graph.getFieldsContainerForField(vertex).getName();
                    stringCycle.add(fieldsContainerName + "." + vertex.getName());
                } else if (vertex.isOfType(SchemaGraph.INPUT_OBJECT)) {
                    stringCycle.add(vertex.getName());
                } else if (vertex.isOfType(SchemaGraph.INPUT_FIELD)) {
                    String inputFieldsContainerName = findCyclesImpl.graph.getFieldsContainerForField(vertex).getName();
                    stringCycle.add(inputFieldsContainerName + "." + vertex.getName());
                } else {
                    Assert.assertShouldNeverHappen("unexpected vertex in cycle found: " + vertex);
                }
            }
            result.add(new SchemaCycle(stringCycle));
        }
        return result;
    }

    private static class GraphAndIndex {
        final SchemaGraph graph;
        final int index;

        public GraphAndIndex(SchemaGraph graph, int index) {
            this.graph = graph;
            this.index = index;
        }
    }

    /**
     * This code was originally taken from https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/alg/cycle/JohnsonSimpleCycles.java
     * * (C) Copyright 2013-2023, by Nikolay Ognyanov and Contributors.
     * *
     * * JGraphT : a free Java graph-theory library
     * *
     * * See the CONTRIBUTORS.md file distributed with this work for additional
     * * information regarding copyright ownership.
     * *
     * * This program and the accompanying materials are made available under the
     * * terms of the Eclipse Public License 2.0 which is available at
     * * http://www.eclipse.org/legal/epl-2.0, or the
     * * GNU Lesser General Public License v2.1 or later
     * * which is available at
     * * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html.
     * *
     * * SPDX-License-Identifier: EPL-2.0 OR LGPL-2.1-or-later
     */
    private static class FindCyclesImpl {

        private final GraphQLSchema schema;
        private final SchemaGraph graph;

        // The main state of the algorithm.
        private Vertex[] iToV = null;
        private Map<Vertex, Integer> vToI = null;
        private Set<Vertex> blocked = null;
        private Map<Vertex, Set<Vertex>> bSets = null;
        private ArrayDeque<Vertex> stack = null;

        // The state of the embedded Tarjan SCC algorithm.
        private List<Set<Vertex>> foundSCCs = null;
        private int index = 0;
        private Map<Vertex, Integer> vIndex = null;
        private Map<Vertex, Integer> vLowlink = null;
        private ArrayDeque<Vertex> path = null;
        private Set<Vertex> pathSet = null;

        private List<List<Vertex>> foundCycles = new ArrayList<>();

        public FindCyclesImpl(GraphQLSchema schema) {
            this.schema = schema;
            SchemaGraphFactory schemaGraphFactory = new SchemaGraphFactory();
            this.graph = schemaGraphFactory.createGraph(schema);
            iToV = (Vertex[]) graph.getVertices().toArray(new Vertex[0]);
            vToI = new LinkedHashMap<>();
            blocked = new LinkedHashSet<>();
            bSets = new LinkedHashMap<>();
            stack = new ArrayDeque<>();

            for (int i = 0; i < iToV.length; i++) {
                vToI.put(iToV[i], i);
            }
        }

        public List<List<Vertex>> findAllSimpleCyclesImpl() {
            int startIndex = 0;

            int size = graph.getVertices().size();
            while (startIndex < size) {
                GraphAndIndex minSCCGResult = findMinSCSG(startIndex);
                if (minSCCGResult != null) {
                    startIndex = minSCCGResult.index;
                    SchemaGraph scg = minSCCGResult.graph;
                    Vertex startV = toV(startIndex);
                    for (Edge e : scg.getAdjacentEdges(startV)) {
                        Vertex v = e.getTo();
                        blocked.remove(v);
                        getBSet(v).clear();
                    }
                    findCyclesInSCG(startIndex, startIndex, scg);
                    startIndex++;
                } else {
                    break;
                }
            }
            return this.foundCycles;
        }

        private GraphAndIndex findMinSCSG(int startIndex) {
            /*
             * Per Johnson : "adjacency structure of strong component $K$ with least vertex in subgraph
             * of $G$ induced by $(s, s + 1, n)$". Or in contemporary terms: the strongly connected
             * component of the subgraph induced by $(v_1, \dotso ,v_n)$ which contains the minimum
             * (among those SCCs) vertex index. We return that index together with the graph.
             */
            initMinSCGState();

            List<Set<Vertex>> foundSCCs = findSCCS(startIndex);

            // find the SCC with the minimum index
            int minIndexFound = Integer.MAX_VALUE;
            Set<Vertex> minSCC = null;
            for (Set<Vertex> scc : foundSCCs) {
                for (Vertex v : scc) {
                    int t = toI(v);
                    if (t < minIndexFound) {
                        minIndexFound = t;
                        minSCC = scc;
                    }
                }
            }
            if (minSCC == null) {
                return null;
            }

            // build a graph for the SCC found
            SchemaGraph resultGraph = new SchemaGraph();
            for (Vertex v : minSCC) {
                resultGraph.addVertex(v);
            }
            for (Vertex v : minSCC) {
                for (Vertex w : minSCC) {
                    Edge edge = graph.getEdge(v, w);
                    if (edge != null) {
                        resultGraph.addEdge(edge);
                    }
                }
            }

            GraphAndIndex graphAndIndex = new GraphAndIndex(resultGraph, minIndexFound);
            clearMinSCCState();
            return graphAndIndex;
        }

        private List<Set<Vertex>> findSCCS(int startIndex) {
            // Find SCCs in the subgraph induced
            // by vertices startIndex and beyond.
            // A call to StrongConnectivityAlgorithm
            // would be too expensive because of the
            // need to materialize the subgraph.
            // So - do a local search by the Tarjan's
            // algorithm and pretend that vertices
            // with an index smaller than startIndex
            // do not exist.
            for (Vertex v : graph.getVertices()) {
                int vI = toI(v);
                if (vI < startIndex) {
                    continue;
                }
                if (!vIndex.containsKey(v)) {
                    getSCCs(startIndex, vI);
                }
            }
            List<Set<Vertex>> result = foundSCCs;
            foundSCCs = null;
            return result;
        }

        private void getSCCs(int startIndex, int vertexIndex) {
            Vertex vertex = toV(vertexIndex);
            vIndex.put(vertex, index);
            vLowlink.put(vertex, index);
            index++;
            path.push(vertex);
            pathSet.add(vertex);

            List<Edge> edges = graph.getAdjacentEdges(vertex);
            for (Edge e : edges) {
                Vertex successor = e.getTo();
                int successorIndex = toI(successor);
                if (successorIndex < startIndex) {
                    continue;
                }
                if (!vIndex.containsKey(successor)) {
                    getSCCs(startIndex, successorIndex);
                    vLowlink.put(vertex, Math.min(vLowlink.get(vertex), vLowlink.get(successor)));
                } else if (pathSet.contains(successor)) {
                    vLowlink.put(vertex, Math.min(vLowlink.get(vertex), vIndex.get(successor)));
                }
            }
            if (vLowlink.get(vertex).equals(vIndex.get(vertex))) {
                Set<Vertex> result = new LinkedHashSet<>();
                Vertex temp;
                do {
                    temp = path.pop();
                    pathSet.remove(temp);
                    result.add(temp);
                } while (!vertex.equals(temp));
                if (result.size() == 1) {
                    Vertex v = result.iterator().next();
                    if (graph.containsEdge(vertex, v)) {
                        foundSCCs.add(result);
                    }
                } else {
                    foundSCCs.add(result);
                }
            }
        }

        private boolean findCyclesInSCG(int startIndex, int vertexIndex, SchemaGraph scg) {
            /*
             * Find cycles in a strongly connected graph per Johnson.
             */
            boolean foundCycle = false;
            Vertex vertex = toV(vertexIndex);
            stack.push(vertex);
            blocked.add(vertex);

            for (Edge e : scg.getAdjacentEdges(vertex)) {
                Vertex successor = e.getTo();
                int successorIndex = toI(successor);
                if (successorIndex == startIndex) {
                    List<Vertex> cycle = new ArrayList<>(stack.size());
                    stack.descendingIterator().forEachRemaining(cycle::add);
                    this.foundCycles.add(cycle);
                    foundCycle = true;
                } else if (!blocked.contains(successor)) {
                    boolean gotCycle = findCyclesInSCG(startIndex, successorIndex, scg);
                    foundCycle = foundCycle || gotCycle;
                }
            }
            if (foundCycle) {
                unblock(vertex);
            } else {
                for (Edge ew : scg.getAdjacentEdges(vertex)) {
                    Vertex w = ew.getTo();
                    Set<Vertex> bSet = getBSet(w);
                    bSet.add(vertex);
                }
            }
            stack.pop();
            return foundCycle;
        }

        private void unblock(Vertex vertex) {
            blocked.remove(vertex);
            Set<Vertex> bSet = getBSet(vertex);
            while (bSet.size() > 0) {
                Vertex w = bSet.iterator().next();
                bSet.remove(w);
                if (blocked.contains(w)) {
                    unblock(w);
                }
            }
        }


        private void initMinSCGState() {
            index = 0;
            foundSCCs = new ArrayList<>();
            vIndex = new LinkedHashMap<>();
            vLowlink = new LinkedHashMap<>();
            path = new ArrayDeque<>();
            pathSet = new LinkedHashSet<>();
        }

        private void clearMinSCCState() {
            index = 0;
            foundSCCs = null;
            vIndex = null;
            vLowlink = null;
            path = null;
            pathSet = null;
        }

        private Integer toI(Vertex vertex) {
            return vToI.get(vertex);
        }

        private Vertex toV(Integer i) {
            return iToV[i];
        }

        private Set<Vertex> getBSet(Vertex v) {
            // B sets typically not all needed,
            // so instantiate lazily.
            return bSets.computeIfAbsent(v, k -> new LinkedHashSet<>());
        }


    }
}
