package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import graphql.Internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertTrue;

@Internal
public class SchemaParentRestrictions {
    public static boolean isApplicableChildVertex(Vertex vertex) {
        return vertex.isOfType(SchemaGraph.FIELD)
                || vertex.isOfType(SchemaGraph.INPUT_FIELD)
                || vertex.isOfType(SchemaGraph.ENUM_VALUE)
                || vertex.isOfType(SchemaGraph.ARGUMENT);
    }

    public static boolean isApplicableParentVertex(Vertex vertex) {
        return vertex.isOfType(SchemaGraph.INPUT_OBJECT)
                || vertex.isOfType(SchemaGraph.OBJECT)
                || vertex.isOfType(SchemaGraph.ENUM);
    }

    public static Map<Vertex, Vertex> getFixedRestrictions(SchemaGraph sourceGraph,
                                                           BiMap<Vertex, Vertex> sourceToTargetMapping,
                                                           List<Vertex> needsFixing) {
        Map<Vertex, Vertex> restrictions = new LinkedHashMap<>();

        for (Vertex vertex : needsFixing) {
            if (isApplicableChildVertex(vertex)) {
                Vertex sourceParent = sourceGraph.getSingleParent(vertex);
                Vertex fixedTargetParent = sourceToTargetMapping.get(sourceParent);

                if (fixedTargetParent != null) {
                    for (Edge edge : sourceGraph.getAdjacentEdgesNonCopy(sourceParent)) {
                        Vertex sibling = edge.getTo();

                        if (isApplicableChildVertex(sibling)) {
                            restrictions.put(sibling, fixedTargetParent);
                        }
                    }
                }
            }
        }

        return restrictions;
    }

    public static Map<Vertex, Vertex> getNonFixedRestrictions(Mapping parentMapping,
                                                              SchemaGraph completeSourceGraph,
                                                              SchemaGraph completeTargetGraph) {
        Map<Vertex, Vertex> restrictions = new LinkedHashMap<>();

        parentMapping.forEachNonFixedSourceAndTarget((source, target) -> {
            if (isApplicableParentVertex(source) && isApplicableParentVertex(target)) {
                for (Edge edge : completeSourceGraph.getAdjacentEdgesNonCopy(source)) {
                    Vertex child = edge.getTo();

                    if (isApplicableChildVertex(child)) {
                        restrictions.put(child, target);
                    }
                }
            } else if (isApplicableChildVertex(source) && isApplicableChildVertex(target)) {
                Vertex sourceParent = completeSourceGraph.getSingleParent(source);
                Vertex targetParent = completeTargetGraph.getSingleParent(target);

                for (Edge edge : completeSourceGraph.getAdjacentEdgesNonCopy(sourceParent)) {
                    Vertex sibling = edge.getTo();

                    if (isApplicableChildVertex(sibling)) {
                        restrictions.put(sibling, targetParent);
                    }
                }
            }
        });

        return restrictions;
    }
}
