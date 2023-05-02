package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import graphql.Internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * This computes the initial set of parent restrictions based on the fixed portion of the mapping.
     * <p>
     * See {@link Mapping} for definition of fixed vs non-fixed.
     * <p>
     * If a {@link Vertex} is present in the output {@link Map} then the value is the parent the
     * vertex MUST map to.
     * <p>
     * e.g. for an output {collar -> Dog} then the collar vertex must be a child of Dog in the mapping.
     *
     * @param sourceGraph           source graph
     * @param sourceToTargetMapping the fixed mappings
     * @param needsFixing           the non fixed mappings that need restrictions
     * @return Map where key is any vertex, and the value is the parent that vertex must map to
     */
    public static Map<Vertex, Vertex> getFixedRestrictions(SchemaGraph sourceGraph,
                                                           BiMap<Vertex, Vertex> sourceToTargetMapping,
                                                           List<Vertex> needsFixing) {
        Map<Vertex, Vertex> restrictions = new LinkedHashMap<>();

        for (Vertex vertex : needsFixing) {
            if (isApplicableChildVertex(vertex)) {
                Vertex sourceParent = sourceGraph.getSingleAdjacentInverseVertex(vertex);
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


    /**
     * This computes the initial set of parent restrictions based on the given non-fixed mapping.
     * <p>
     * See {@link Mapping} for definition of fixed vs non-fixed.
     * <p>
     * If a {@link Vertex} is present in the output {@link Map} then the value is the parent the
     * vertex MUST map to.
     * <p>
     * e.g. for an output {collar -> Dog} then the collar vertex must be a child of Dog in the mapping.
     *
     * @param mapping             the mapping to get non-fixed parent restrictions for
     * @param completeSourceGraph source graph
     * @param completeTargetGraph target graph
     * @return Map where key is any vertex, and the value is the parent that vertex must map to
     */
    public static Map<Vertex, Vertex> getNonFixedRestrictions(Mapping mapping,
                                                              SchemaGraph completeSourceGraph,
                                                              SchemaGraph completeTargetGraph) {
        Map<Vertex, Vertex> restrictions = new LinkedHashMap<>();

        mapping.forEachNonFixedSourceAndTarget((source, target) -> {
            if (isApplicableParentVertex(source) && isApplicableParentVertex(target)) {
                for (Edge edge : completeSourceGraph.getAdjacentEdgesNonCopy(source)) {
                    Vertex child = edge.getTo();

                    if (isApplicableChildVertex(child)) {
                        restrictions.put(child, target);
                    }
                }
            } else if (isApplicableChildVertex(source) && isApplicableChildVertex(target)) {
                Vertex sourceParent = completeSourceGraph.getSingleAdjacentInverseVertex(source);
                Vertex targetParent = completeTargetGraph.getSingleAdjacentInverseVertex(target);

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
