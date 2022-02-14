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

public class FillupIsolatedVertices {

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;
    IsolatedVertices isolatedVertices;

    static Map<String, List<IsolatedVertexContext>> typeContexts = new LinkedHashMap<>();

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

    private static List<IsolatedVertexContext> inputFieldContexts() {
        IsolatedVertexContext inputFieldType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INPUT_FIELD.equals(vertex.getType());
            }
        };
        IsolatedVertexContext inputObjectContext = new IsolatedVertexContext() {
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
        IsolatedVertexContext inputFieldName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex inputField, SchemaGraph schemaGraph) {
                return inputField.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(inputFieldType, inputObjectContext, inputFieldName);
        return contexts;
    }


    private static List<IsolatedVertexContext> dummyTypeContext() {
        IsolatedVertexContext dummyType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DUMMY_TYPE_VERTEX.equals(vertex.getType());
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(dummyType);
        return contexts;
    }

    private static List<IsolatedVertexContext> scalarContext() {
        IsolatedVertexContext scalar = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return SCALAR.equals(vertex.getType());
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(scalar);
        return contexts;
    }

    private static List<IsolatedVertexContext> inputObjectContext() {
        IsolatedVertexContext inputObject = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INPUT_OBJECT.equals(vertex.getType());
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(inputObject);
        return contexts;
    }

    private static List<IsolatedVertexContext> objectContext() {
        IsolatedVertexContext objectType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return OBJECT.equals(vertex.getType());
            }
        };

        IsolatedVertexContext objectName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex object, SchemaGraph schemaGraph) {
                return object.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(objectType, objectName);
        return contexts;
    }

    private static List<IsolatedVertexContext> enumContext() {
        IsolatedVertexContext enumCtxType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM.equals(vertex.getType());
            }
        };
        IsolatedVertexContext enumName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex enumVertex, SchemaGraph schemaGraph) {
                return enumVertex.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(enumCtxType, enumName);
        return contexts;
    }

    private static List<IsolatedVertexContext> enumValueContext() {
        IsolatedVertexContext enumValueType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM_VALUE.equals(vertex.getType());
            }
        };
        IsolatedVertexContext enumValueName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex enumValue, SchemaGraph schemaGraph) {
                return enumValue.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(enumValueType, enumValueName);
        return contexts;
    }

    private static List<IsolatedVertexContext> interfaceContext() {
        IsolatedVertexContext interfaceType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INTERFACE.equals(vertex.getType());
            }
        };
        IsolatedVertexContext interfaceName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex interfaceVertex, SchemaGraph schemaGraph) {
                return interfaceVertex.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<IsolatedVertexContext> contexts = Arrays.asList(interfaceType, interfaceName);
        return contexts;
    }

    private static List<IsolatedVertexContext> unionContext() {
        IsolatedVertexContext unionType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return UNION.equals(vertex.getType());
            }
        };
        IsolatedVertexContext unionName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex union, SchemaGraph schemaGraph) {
                return union.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<IsolatedVertexContext> contexts = Arrays.asList(unionType, unionName);
        return contexts;
    }

    private static List<IsolatedVertexContext> directiveContext() {
        IsolatedVertexContext directiveType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DIRECTIVE.equals(vertex.getType());
            }
        };
        IsolatedVertexContext directiveName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex directive, SchemaGraph schemaGraph) {
                return directive.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<IsolatedVertexContext> contexts = Arrays.asList(directiveType);
        return contexts;
    }

    private static List<IsolatedVertexContext> appliedDirectiveContext() {
        IsolatedVertexContext appliedDirectiveType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_DIRECTIVE.equals(vertex.getType());
            }
        };
        IsolatedVertexContext appliedDirectiveName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                return appliedDirective.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        List<IsolatedVertexContext> contexts = Arrays.asList(appliedDirectiveType, appliedDirectiveName);
        return contexts;
    }

    private static List<IsolatedVertexContext> appliedArgumentContext() {
        IsolatedVertexContext appliedArgumentType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_ARGUMENT.equals(vertex.getType());
            }
        };
        IsolatedVertexContext appliedDirective = new IsolatedVertexContext() {
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
        IsolatedVertexContext appliedDirectiveContainer = new IsolatedVertexContext() {
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
        IsolatedVertexContext parentOfAppliedDirectiveContainer = new IsolatedVertexContext() {
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
        IsolatedVertexContext appliedArgumentName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                return appliedArgument.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(appliedArgumentType, parentOfAppliedDirectiveContainer, appliedDirectiveContainer, appliedDirective, appliedArgumentName);
        return contexts;
    }

    private static List<IsolatedVertexContext> fieldContext() {
        IsolatedVertexContext field = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return FIELD.equals(vertex.getType());
            }
        };
        IsolatedVertexContext container = new IsolatedVertexContext() {
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

        IsolatedVertexContext fieldName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex field, SchemaGraph schemaGraph) {
                return field.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(field, container, fieldName);
        return contexts;
    }

    private static List<IsolatedVertexContext> argumentsForFieldsContexts() {

        IsolatedVertexContext argumentType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ARGUMENT.equals(vertex.getType());
            }
        };

        IsolatedVertexContext fieldOrDirective = new IsolatedVertexContext() {
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
        IsolatedVertexContext containerOrDirectiveHolder = new IsolatedVertexContext() {
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
        IsolatedVertexContext argumentName = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                return argument.getName();
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(argumentType, containerOrDirectiveHolder, fieldOrDirective, argumentName);
        return contexts;
    }


    public FillupIsolatedVertices(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        this.sourceGraph = sourceGraph;
        this.targetGraph = targetGraph;
        this.isolatedVertices = new IsolatedVertices();
    }

    public void ensureGraphAreSameSize() {
//        calcIsolatedVertices(typeContexts.get(FIELD), FIELD);
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

        System.out.println("done isolated");
        Assert.assertTrue(sourceGraph.size() == targetGraph.size());
//        if (sourceGraph.size() < targetGraph.size()) {
//            isolatedVertices.isolatedBuiltInSourceVertices.addAll(sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size(), "source-isolated-builtin-"));
//        } else if (sourceGraph.size() > targetGraph.size()) {
//            isolatedVertices.isolatedBuiltInTargetVertices.addAll(targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size(), "target-isolated-builtin-"));
//        }
    }


    public abstract static class IsolatedVertexContext {

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


    public void calcIsolatedVertices(List<IsolatedVertexContext> contexts, String typeNameForDebug) {
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
            List<IsolatedVertexContext> contextSegments,
            Set<Vertex> usedSourceVertices,
            Set<Vertex> usedTargetVertices,
            String typeNameForDebug) {

        /**
         * the elements grouped by the current context segment.
         */

        IsolatedVertexContext finalCurrentContext = contextSegments.get(curContextSegmentIx);
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

    public void calcPossibleMappings(List<IsolatedVertexContext> contexts, String typeNameForDebug) {
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
            List<IsolatedVertexContext> contexts,
            Set<Vertex> usedSourceVertices,
            Set<Vertex> usedTargetVertices,
            String typeNameForDebug) {

        IsolatedVertexContext finalCurrentContext = contexts.get(contextIx);
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
