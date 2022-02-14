package graphql.schema.diffing;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import graphql.Assert;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.diffing.SchemaDiffing.diffNamedList;
import static graphql.schema.diffing.SchemaGraph.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraph.APPLIED_DIRECTIVE;
import static graphql.schema.diffing.SchemaGraph.ARGUMENT;
import static graphql.schema.diffing.SchemaGraph.DIRECTIVE;
import static graphql.schema.diffing.SchemaGraph.DUMMY_TYPE_VERTEX;
import static graphql.schema.diffing.SchemaGraph.ENUM;
import static graphql.schema.diffing.SchemaGraph.ENUM_VALUE;
import static graphql.schema.diffing.SchemaGraph.FIELD;
import static graphql.schema.diffing.SchemaGraph.INPUT_FIELD;
import static graphql.schema.diffing.SchemaGraph.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraph.INTERFACE;
import static graphql.schema.diffing.SchemaGraph.OBJECT;
import static graphql.schema.diffing.SchemaGraph.SCALAR;
import static graphql.schema.diffing.SchemaGraph.UNION;
import static graphql.util.FpKit.concat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class FillupIsolatedVertices {

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;
    IsolatedVertices isolatedVertices;

    static Map<String, List<VertexContextSegment>> typeContexts = new LinkedHashMap<>();

    static {
        typeContexts.put(FIELD, fieldContext());
        typeContexts.put(ARGUMENT, argumentsForFieldsContexts());
        typeContexts.put(INPUT_FIELD, inputFieldContexts());
        typeContexts.put(DUMMY_TYPE_VERTEX, dummyTypeContext());
        typeContexts.put(OBJECT, objectContext());
        typeContexts.put(INTERFACE, interfaceContext());
        typeContexts.put(UNION, unionContext());
        typeContexts.put(INPUT_OBJECT, inputObjectContext());
        typeContexts.put(SCALAR, scalarContext());
        typeContexts.put(ENUM, enumContext());
        typeContexts.put(ENUM_VALUE, enumValueContext());
        typeContexts.put(APPLIED_DIRECTIVE, appliedDirectiveContext());
        typeContexts.put(APPLIED_ARGUMENT, appliedArgumentContext());
        typeContexts.put(DIRECTIVE, directiveContext());
    }

    private static List<VertexContextSegment> inputFieldContexts() {
        VertexContextSegment inputFieldType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INPUT_FIELD.equals(vertex.getType());
            }
        };
        VertexContextSegment inputObjectContext = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex inputField, SchemaGraph schemaGraph) {
                Vertex inputObject = schemaGraph.getInputObjectForInputField(inputField);
                return inputObject.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment inputFieldName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex inputField, SchemaGraph schemaGraph) {
                return inputField.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(inputFieldType, inputObjectContext, inputFieldName);
        return contexts;
    }


    private static List<VertexContextSegment> dummyTypeContext() {

        VertexContextSegment dummyType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DUMMY_TYPE_VERTEX.equals(vertex.getType());
            }
        };
        VertexContextSegment inputObjectOrFieldContainerContext = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex dummyType, SchemaGraph schemaGraph) {
                Vertex fieldOrInputField = schemaGraph.getFieldOrInputFieldForDummyType(dummyType);
                if (fieldOrInputField.getType().equals(FIELD)) {
                    Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(fieldOrInputField);
                    return fieldsContainer.getType() + "." + fieldsContainer.getName();
                } else {
                    return schemaGraph.getInputObjectForInputField(fieldOrInputField).getName();
                }
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment inputFieldOrFieldName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex dummyType, SchemaGraph schemaGraph) {
                Vertex fieldOrInputField = schemaGraph.getFieldOrInputFieldForDummyType(dummyType);
                return fieldOrInputField.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<VertexContextSegment> contexts = Arrays.asList(dummyType, inputObjectOrFieldContainerContext, inputFieldOrFieldName);
        return contexts;
    }

    private static List<VertexContextSegment> scalarContext() {
        VertexContextSegment scalar = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return SCALAR.equals(vertex.getType());
            }
        };
        VertexContextSegment scalarName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(scalar, scalarName);
        return contexts;
    }

    private static List<VertexContextSegment> inputObjectContext() {
        VertexContextSegment inputObject = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INPUT_OBJECT.equals(vertex.getType());
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(inputObject);
        return contexts;
    }

    private static List<VertexContextSegment> objectContext() {
        VertexContextSegment objectType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return OBJECT.equals(vertex.getType());
            }
        };

        VertexContextSegment objectName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex object, SchemaGraph schemaGraph) {
                return object.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(objectType, objectName);
        return contexts;
    }

    private static List<VertexContextSegment> enumContext() {
        VertexContextSegment enumCtxType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM.equals(vertex.getType());
            }
        };
        VertexContextSegment enumName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex enumVertex, SchemaGraph schemaGraph) {
                return enumVertex.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(enumCtxType, enumName);
        return contexts;
    }

    private static List<VertexContextSegment> enumValueContext() {
        VertexContextSegment enumValueType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM_VALUE.equals(vertex.getType());
            }
        };
        VertexContextSegment enumName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex enumValue, SchemaGraph schemaGraph) {
                return schemaGraph.getEnumForEnumValue(enumValue).getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment enumValueName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex enumValue, SchemaGraph schemaGraph) {
                return enumValue.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(enumValueType, enumName, enumValueName);
        return contexts;
    }

    private static List<VertexContextSegment> interfaceContext() {
        VertexContextSegment interfaceType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INTERFACE.equals(vertex.getType());
            }
        };
        VertexContextSegment interfaceName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex interfaceVertex, SchemaGraph schemaGraph) {
                return interfaceVertex.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<VertexContextSegment> contexts = Arrays.asList(interfaceType, interfaceName);
        return contexts;
    }

    private static List<VertexContextSegment> unionContext() {
        VertexContextSegment unionType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return UNION.equals(vertex.getType());
            }
        };
        VertexContextSegment unionName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex union, SchemaGraph schemaGraph) {
                return union.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<VertexContextSegment> contexts = Arrays.asList(unionType, unionName);
        return contexts;
    }

    private static List<VertexContextSegment> directiveContext() {
        VertexContextSegment directiveType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DIRECTIVE.equals(vertex.getType());
            }
        };
        VertexContextSegment directiveName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex directive, SchemaGraph schemaGraph) {
                return directive.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<VertexContextSegment> contexts = Arrays.asList(directiveType, directiveName);
        return contexts;
    }

    private static List<VertexContextSegment> appliedDirectiveContext() {
        VertexContextSegment appliedDirectiveType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_DIRECTIVE.equals(vertex.getType());
            }
        };
        VertexContextSegment appliedDirectiveName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                return appliedDirective.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<VertexContextSegment> contexts = Arrays.asList(appliedDirectiveType, appliedDirectiveName);
        return contexts;
    }

    private static List<VertexContextSegment> appliedArgumentContext() {
        VertexContextSegment appliedArgumentType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_ARGUMENT.equals(vertex.getType());
            }
        };
        VertexContextSegment appliedDirective = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                Vertex appliedDirective = schemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
                return appliedDirective.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment appliedDirectiveContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                Vertex appliedDirective = schemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
                Vertex appliedDirectiveContainer = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                return appliedDirectiveContainer.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment parentOfAppliedDirectiveContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                Vertex appliedDirective = schemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
                Vertex appliedDirectiveContainer = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                Vertex parent = schemaGraph.getParentSchemaElement(appliedDirectiveContainer);
                return parent.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment appliedArgumentName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                return appliedArgument.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(appliedArgumentType, parentOfAppliedDirectiveContainer, appliedDirectiveContainer, appliedDirective, appliedArgumentName);
        return contexts;
    }

    private static List<VertexContextSegment> fieldContext() {
        VertexContextSegment field = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return FIELD.equals(vertex.getType());
            }
        };
        VertexContextSegment container = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex field, SchemaGraph schemaGraph) {
                Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(field);
                return fieldsContainer.getType() + "." + fieldsContainer.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        VertexContextSegment fieldName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex field, SchemaGraph schemaGraph) {
                return field.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(field, container, fieldName);
        return contexts;
    }

    private static List<VertexContextSegment> argumentsForFieldsContexts() {

        VertexContextSegment argumentType = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ARGUMENT.equals(vertex.getType());
            }
        };

        VertexContextSegment fieldOrDirective = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(argument);
                return fieldOrDirective.getType() + "." + fieldOrDirective.getName();
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment containerOrDirectiveHolder = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(argument);
                if (fieldOrDirective.getType().equals(FIELD)) {
                    Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(fieldOrDirective);
                    // can be Interface or Object
                    return fieldsContainer.getType() + "." + fieldsContainer.getName();
                } else {
                    // a directive doesn't have further context
                    return "";
                }
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment argumentName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                return argument.getName();
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(argumentType, containerOrDirectiveHolder, fieldOrDirective, argumentName);
        return contexts;
    }


    public FillupIsolatedVertices(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        this.sourceGraph = sourceGraph;
        this.targetGraph = targetGraph;
        this.isolatedVertices = new IsolatedVertices();
    }

    public void ensureGraphAreSameSize() {
        calcPossibleMappings(typeContexts.get(FIELD), FIELD);
        calcPossibleMappings(typeContexts.get(ARGUMENT), ARGUMENT);
        calcPossibleMappings(typeContexts.get(INPUT_FIELD), INPUT_FIELD);
        calcPossibleMappings(typeContexts.get(DUMMY_TYPE_VERTEX), DUMMY_TYPE_VERTEX);
        calcPossibleMappings(typeContexts.get(OBJECT), OBJECT);
        calcPossibleMappings(typeContexts.get(INTERFACE), INTERFACE);
        calcPossibleMappings(typeContexts.get(UNION), UNION);
        calcPossibleMappings(typeContexts.get(INPUT_OBJECT), INPUT_OBJECT);
        calcPossibleMappings(typeContexts.get(SCALAR), SCALAR);
        calcPossibleMappings(typeContexts.get(ENUM), ENUM);
        calcPossibleMappings(typeContexts.get(ENUM_VALUE), ENUM_VALUE);
        calcPossibleMappings(typeContexts.get(APPLIED_DIRECTIVE), APPLIED_DIRECTIVE);
        calcPossibleMappings(typeContexts.get(APPLIED_ARGUMENT), APPLIED_ARGUMENT);
        calcPossibleMappings(typeContexts.get(DIRECTIVE), DIRECTIVE);


        sourceGraph.addVertices(isolatedVertices.allIsolatedSource);
        targetGraph.addVertices(isolatedVertices.allIsolatedTarget);

        Assert.assertTrue(sourceGraph.size() == targetGraph.size());
        for (Vertex vertex : isolatedVertices.possibleMappings.keySet()) {
            Collection<Vertex> vertices = isolatedVertices.possibleMappings.get(vertex);
            if (vertices.size() > 1) {
                System.out.println("multiple for " + vertex);
            }
        }
        System.out.println("done isolated");
//        if (sourceGraph.size() < targetGraph.size()) {
//            isolatedVertices.isolatedBuiltInSourceVertices.addAll(sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size(), "source-isolated-builtin-"));
//        } else if (sourceGraph.size() > targetGraph.size()) {
//            isolatedVertices.isolatedBuiltInTargetVertices.addAll(targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size(), "target-isolated-builtin-"));
//        }
    }


    public abstract static class VertexContextSegment {

        private List<VertexContextSegment> children;

        public VertexContextSegment(List<VertexContextSegment> children) {
            this.children = children;
        }

        public VertexContextSegment() {
            this.children = emptyList();
        }

        public VertexContextSegment(VertexContextSegment child) {
            this.children = singletonList(child);
        }

        public abstract String idForVertex(Vertex vertex, SchemaGraph schemaGraph);

        public abstract boolean filter(Vertex vertex, SchemaGraph schemaGraph);
    }

    public class IsolatedVertices {

        public Multimap<Object, Vertex> contextToIsolatedSourceVertices = HashMultimap.create();
        public Multimap<Object, Vertex> contextToIsolatedTargetVertices = HashMultimap.create();

        public Set<Vertex> allIsolatedSource = new LinkedHashSet<>();
        public Set<Vertex> allIsolatedTarget = new LinkedHashSet<>();

        public final Set<Vertex> isolatedBuiltInSourceVertices = new LinkedHashSet<>();
        public final Set<Vertex> isolatedBuiltInTargetVertices = new LinkedHashSet<>();

        // from source to target
        public Multimap<Vertex, Vertex> possibleMappings = HashMultimap.create();

        public void putPossibleMappings(Collection<Vertex> sourceVertices, Collection<Vertex> targetVertex) {
            for (Vertex sourceVertex : sourceVertices) {
                possibleMappings.putAll(sourceVertex, targetVertex);
            }
        }

        public void addIsolatedSource(Collection<Vertex> isolatedSource) {
            allIsolatedSource.addAll(isolatedSource);
        }

        public void addIsolatedTarget(Collection<Vertex> isolatedTarget) {
            allIsolatedTarget.addAll(isolatedTarget);
        }

        public void putSource(Object contextId, Collection<Vertex> isolatedSourcedVertices) {
            contextToIsolatedSourceVertices.putAll(contextId, isolatedSourcedVertices);
            allIsolatedSource.addAll(isolatedSourcedVertices);
        }

        public void putTarget(Object contextId, Collection<Vertex> isolatedTargetVertices) {
            contextToIsolatedTargetVertices.putAll(contextId, isolatedTargetVertices);
            allIsolatedTarget.addAll(isolatedTargetVertices);
        }

        public boolean mappingPossible(Vertex sourceVertex, Vertex targetVertex) {
            return possibleMappings.containsEntry(sourceVertex, targetVertex);
        }

//        public boolean mappingPossibleForIsolatedSource(Vertex isolatedSourceVertex, Vertex targetVertex) {
//            List<IsolatedVertexContext> contexts = typeContexts.get(targetVertex.getType());
//            Assert.assertNotNull(contexts);
//            List<String> contextForVertex = new ArrayList<>();
//            for (IsolatedVertexContext isolatedVertexContext : contexts) {
//                contextForVertex.add(isolatedVertexContext.idForVertex(targetVertex, targetGraph));
//            }
//            if (!targetVertex.getType().equals(DUMMY_TYPE_VERTEX)) {
//                contextForVertex.add(targetVertex.getName());
//            }
//            while (contextForVertex.size() > 0) {
//                if (isolatedVertices.contextToIsolatedSourceVertices.containsKey(contextForVertex)) {
//                    return isolatedVertices.contextToIsolatedSourceVertices.get(contextForVertex).contains(isolatedSourceVertex);
//                }
//                contextForVertex.remove(contextForVertex.size() - 1);
//            }
//            return false;
//        }
//
//        public boolean mappingPossibleForIsolatedTarget(Vertex sourceVertex, Vertex isolatedTargetVertex) {
//            List<IsolatedVertexContext> contexts = typeContexts.get(sourceVertex.getType());
//            Assert.assertNotNull(contexts);
//            List<String> contextForVertex = new ArrayList<>();
//            for (IsolatedVertexContext isolatedVertexContext : contexts) {
//                contextForVertex.add(isolatedVertexContext.idForVertex(sourceVertex, sourceGraph));
//            }
//            if (!sourceVertex.getType().equals(DUMMY_TYPE_VERTEX)) {
//                contextForVertex.add(sourceVertex.getName());
//            }
//            while (contextForVertex.size() > 0) {
//                if (isolatedVertices.contextToIsolatedTargetVertices.containsKey(contextForVertex)) {
//                    return isolatedVertices.contextToIsolatedTargetVertices.get(contextForVertex).contains(isolatedTargetVertex);
//                }
//                contextForVertex.remove(contextForVertex.size() - 1);
//            }
//            return false;
//
//        }
    }


    private void calcIsolatedVertices(List<VertexContextSegment> contexts, String typeNameForDebug) {
        Collection<Vertex> currentSourceVertices = sourceGraph.getVertices();
        Collection<Vertex> currentTargetVertices = targetGraph.getVertices();
        calcIsolatedVerticesImpl(currentSourceVertices, currentTargetVertices, Collections.emptyList(), 0, contexts, new LinkedHashSet<>(), new LinkedHashSet<>(), typeNameForDebug);
    }

    /**
     *
     */
    private void calcIsolatedVerticesImpl(
            Collection<Vertex> currentSourceVertices,
            Collection<Vertex> currentTargetVertices,
            List<String> currentContextId,
            int curContextSegmentIx,
            List<VertexContextSegment> contextSegments,
            Set<Vertex> usedSourceVertices,
            Set<Vertex> usedTargetVertices,
            String typeNameForDebug) {

        /**
         * the elements grouped by the current context segment.
         */

        VertexContextSegment finalCurrentContext = contextSegments.get(curContextSegmentIx);
        Map<String, ImmutableList<Vertex>> sourceGroups = FpKit.filterAndGroupingBy(currentSourceVertices,
                v -> finalCurrentContext.filter(v, sourceGraph),
                v -> finalCurrentContext.idForVertex(v, sourceGraph));
        Map<String, ImmutableList<Vertex>> targetGroups = FpKit.filterAndGroupingBy(currentTargetVertices,
                v -> finalCurrentContext.filter(v, targetGraph),
                v -> finalCurrentContext.idForVertex(v, targetGraph));

        // all of the relevant vertices will be handled


        if (curContextSegmentIx == 0) {
            if (sourceGroups.size() == 0 && targetGroups.size() == 1) {
                // we only have inserted elements
                String context = targetGroups.keySet().iterator().next();
                int count = targetGroups.get(context).size();
                Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(count, "source-isolated-" + typeNameForDebug + "-");
                isolatedVertices.putSource(Arrays.asList(context), newSourceVertices);
            } else if (sourceGroups.size() == 1 && targetGroups.size() == 0) {
                // we only have deleted elements
                String context = sourceGroups.keySet().iterator().next();
                int count = sourceGroups.get(context).size();
                Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(count, "target-isolated-" + typeNameForDebug + "-");
                isolatedVertices.putTarget(Arrays.asList(context), newTargetVertices);
            }
        }
        List<String> deletedContexts = new ArrayList<>();
        List<String> insertedContexts = new ArrayList<>();
        List<String> sameContexts = new ArrayList<>();
        diffNamedList(sourceGroups.keySet(), targetGroups.keySet(), deletedContexts, insertedContexts, sameContexts);
        for (String sameContext : sameContexts) {
            ImmutableList<Vertex> sourceVertices = sourceGroups.get(sameContext);
            ImmutableList<Vertex> targetVertices = targetGroups.get(sameContext);
            List<String> newContextId = concat(currentContextId, sameContext);
            if (contextSegments.size() > curContextSegmentIx + 1) {
                calcIsolatedVerticesImpl(sourceVertices, targetVertices, newContextId, curContextSegmentIx + 1, contextSegments, usedSourceVertices, usedTargetVertices, typeNameForDebug);
            }

            Set<Vertex> notUsedSource = new LinkedHashSet<>(sourceVertices);
            notUsedSource.removeAll(usedSourceVertices);
            Set<Vertex> notUsedTarget = new LinkedHashSet<>(targetVertices);
            notUsedTarget.removeAll(usedTargetVertices);

            /**
             * We know that the first context is just by type and we have all the remaining vertices of the same
             * type here.
             */
            if (curContextSegmentIx == 0) {
                if (notUsedSource.size() > notUsedTarget.size()) {
                    Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(notUsedSource.size() - notUsedTarget.size(), "target-isolated-" + typeNameForDebug + "-");
                    // all deleted vertices can map to all new TargetVertices
                    for (Vertex deletedVertex : notUsedSource) {
                        isolatedVertices.putTarget(Arrays.asList(sameContext), newTargetVertices);
                    }
                } else if (notUsedTarget.size() > notUsedSource.size()) {
                    Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(notUsedTarget.size() - notUsedSource.size(), "source-isolated-" + typeNameForDebug + "-");
                    // all inserted fields can map to all new source vertices
                    for (Vertex insertedVertex : notUsedTarget) {
                        isolatedVertices.putSource(Arrays.asList(sameContext), newSourceVertices);
                    }
                }
            } else {
                usedSourceVertices.addAll(sourceGroups.get(sameContext));
                usedTargetVertices.addAll(targetGroups.get(sameContext));
                if (notUsedSource.size() > notUsedTarget.size()) {
                    Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(notUsedSource.size() - notUsedTarget.size(), "target-isolated-" + typeNameForDebug + "-");
                    // all deleted vertices can map to all new TargetVertices
                    for (Vertex deletedVertex : notUsedSource) {
                        isolatedVertices.putTarget(concat(newContextId, deletedVertex.getName()), newTargetVertices);
                    }
                } else if (notUsedTarget.size() > notUsedSource.size()) {
                    Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(notUsedTarget.size() - notUsedSource.size(), "source-isolated-" + typeNameForDebug + "-");
                    // all inserted fields can map to all new source vertices
                    for (Vertex insertedVertex : notUsedTarget) {
                        isolatedVertices.putSource(concat(newContextId, insertedVertex.getName()), newSourceVertices);
                    }
                }
            }

        }

    }

    public void calcPossibleMappings(List<VertexContextSegment> contexts, String typeNameForDebug) {
        Collection<Vertex> currentSourceVertices = sourceGraph.getVertices();
        Collection<Vertex> currentTargetVertices = targetGraph.getVertices();
        calcPossibleMappingImpl(currentSourceVertices,
                currentTargetVertices,
                Collections.emptyList(),
                0,
                contexts,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                typeNameForDebug);
    }

    /**
     * calc for the provided context
     */
    private void calcPossibleMappingImpl(
            Collection<Vertex> currentSourceVertices,
            Collection<Vertex> currentTargetVertices,
            List<String> contextId,
            int contextIx,
            List<VertexContextSegment> contexts,
            Set<Vertex> usedSourceVertices,
            Set<Vertex> usedTargetVertices,
            String typeNameForDebug) {

        VertexContextSegment finalCurrentContext = contexts.get(contextIx);
        Map<String, ImmutableList<Vertex>> sourceGroups = FpKit.filterAndGroupingBy(currentSourceVertices,
                v -> finalCurrentContext.filter(v, sourceGraph),
                v -> finalCurrentContext.idForVertex(v, sourceGraph));
        Map<String, ImmutableList<Vertex>> targetGroups = FpKit.filterAndGroupingBy(currentTargetVertices,
                v -> finalCurrentContext.filter(v, targetGraph),
                v -> finalCurrentContext.idForVertex(v, targetGraph));


        List<String> deletedContexts = new ArrayList<>();
        List<String> insertedContexts = new ArrayList<>();
        List<String> sameContexts = new ArrayList<>();
        diffNamedList(sourceGroups.keySet(), targetGroups.keySet(), deletedContexts, insertedContexts, sameContexts);

        // for each unchanged context we descend recursively into
        for (String sameContext : sameContexts) {
            ImmutableList<Vertex> sourceVerticesInContext = sourceGroups.get(sameContext);
            ImmutableList<Vertex> targetVerticesInContext = targetGroups.get(sameContext);
            List<String> currentContextId = concat(contextId, sameContext);
            if (contexts.size() > contextIx + 1) {
                calcPossibleMappingImpl(sourceVerticesInContext, targetVerticesInContext, currentContextId, contextIx + 1, contexts, usedSourceVertices, usedTargetVertices, typeNameForDebug);
            }
            /**
             * Either there was no context segment left or not all vertices were relevant for
             * Either way: fill up with isolated vertices and record as possible mapping
             */
            Set<Vertex> notUsedSource = new LinkedHashSet<>(sourceVerticesInContext);
            notUsedSource.removeAll(usedSourceVertices);
            Set<Vertex> notUsedTarget = new LinkedHashSet<>(targetVerticesInContext);
            notUsedTarget.removeAll(usedTargetVertices);

            if (notUsedSource.size() > notUsedTarget.size()) {
                Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(notUsedSource.size() - notUsedTarget.size(), "target-isolated-" + typeNameForDebug + "-");
                isolatedVertices.addIsolatedTarget(newTargetVertices);
                notUsedTarget.addAll(newTargetVertices);
            } else if (notUsedTarget.size() > notUsedSource.size()) {
                Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(notUsedTarget.size() - notUsedSource.size(), "source-isolated-" + typeNameForDebug + "-");
                isolatedVertices.addIsolatedSource(newSourceVertices);
                notUsedSource.addAll(newSourceVertices);
            }
            isolatedVertices.putPossibleMappings(notUsedSource, notUsedTarget);
            usedSourceVertices.addAll(notUsedSource);
            usedTargetVertices.addAll(notUsedTarget);
        }

        Set<Vertex> possibleTargetVertices = new LinkedHashSet<>();
        for (String insertedContext : insertedContexts) {
            ImmutableList<Vertex> vertices = targetGroups.get(insertedContext);
            for (Vertex targetVertex : vertices) {
                if (!usedTargetVertices.contains(targetVertex)) {
                    possibleTargetVertices.add(targetVertex);
                }
            }
            usedTargetVertices.addAll(vertices);
        }

        Set<Vertex> possibleSourceVertices = new LinkedHashSet<>();
        for (String deletedContext : deletedContexts) {
            ImmutableList<Vertex> vertices = sourceGroups.get(deletedContext);
            for (Vertex sourceVertex : vertices) {
                if (!usedSourceVertices.contains(sourceVertex)) {
                    possibleSourceVertices.add(sourceVertex);
                }
            }
            usedSourceVertices.addAll(vertices);
        }

        if (possibleSourceVertices.size() > possibleTargetVertices.size()) {
            Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(possibleSourceVertices.size() - possibleTargetVertices.size(), "target-isolated-" + typeNameForDebug + "-");
            isolatedVertices.addIsolatedTarget(newTargetVertices);
            possibleTargetVertices.addAll(newTargetVertices);
        } else if (possibleTargetVertices.size() > possibleSourceVertices.size()) {
            Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(possibleTargetVertices.size() - possibleSourceVertices.size(), "source-isolated-" + typeNameForDebug + "-");
            isolatedVertices.addIsolatedSource(newSourceVertices);
            possibleSourceVertices.addAll(newSourceVertices);
        }
        isolatedVertices.putPossibleMappings(possibleSourceVertices, possibleTargetVertices);

    }


}
