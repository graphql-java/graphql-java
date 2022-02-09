package graphql.schema.diffing;

import com.google.common.collect.HashBiMap;
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
import static graphql.schema.diffing.SchemaDiffing.diffVertices;
import static graphql.schema.diffing.SchemaGraphFactory.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraphFactory.APPLIED_DIRECTIVE;
import static graphql.schema.diffing.SchemaGraphFactory.ARGUMENT;
import static graphql.schema.diffing.SchemaGraphFactory.DIRECTIVE;
import static graphql.schema.diffing.SchemaGraphFactory.DUMMY_TYPE_VERTEX;
import static graphql.schema.diffing.SchemaGraphFactory.ENUM;
import static graphql.schema.diffing.SchemaGraphFactory.ENUM_VALUE;
import static graphql.schema.diffing.SchemaGraphFactory.FIELD;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_FIELD;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.INTERFACE;
import static graphql.schema.diffing.SchemaGraphFactory.OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.SCALAR;
import static graphql.schema.diffing.SchemaGraphFactory.UNION;
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

    private static List<IsolatedVertexContext> dummyTypeContext() {
        IsolatedVertexContext dummyType = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DUMMY_TYPE_VERTEX.equals(vertex.getType()) && !vertex.isBuiltInType();
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
                return SCALAR.equals(vertex.getType()) && !vertex.isBuiltInType();
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
                return INPUT_OBJECT.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(inputObject);
        return contexts;
    }

    private static List<IsolatedVertexContext> objectContext() {
        IsolatedVertexContext object = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return OBJECT.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(object);
        return contexts;
    }

    private static List<IsolatedVertexContext> enumContext() {
        IsolatedVertexContext enumCtx = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(enumCtx);
        return contexts;
    }

    private static List<IsolatedVertexContext> enumValueContext() {
        IsolatedVertexContext enumValue = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ENUM_VALUE.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(enumValue);
        return contexts;
    }

    private static List<IsolatedVertexContext> interfaceContext() {
        IsolatedVertexContext interfaceContext = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INTERFACE.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(interfaceContext);
        return contexts;
    }

    private static List<IsolatedVertexContext> unionContext() {
        IsolatedVertexContext unionContext = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return UNION.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(unionContext);
        return contexts;
    }

    private static List<IsolatedVertexContext> directiveContext() {
        IsolatedVertexContext directiveContext = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return DIRECTIVE.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(directiveContext);
        return contexts;
    }

    private static List<IsolatedVertexContext> appliedDirectiveContext() {
        IsolatedVertexContext appliedDirective = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_DIRECTIVE.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(appliedDirective);
        return contexts;
    }

    private static List<IsolatedVertexContext> appliedArgumentContext() {
        IsolatedVertexContext appliedArgument = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return APPLIED_ARGUMENT.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(appliedArgument);
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
                return FIELD.equals(vertex.getType()) && !vertex.isBuiltInType();
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
        List<IsolatedVertexContext> contexts = Arrays.asList(field, container);
        return contexts;
    }

    private static List<IsolatedVertexContext> argumentsForFieldsContexts() {

        IsolatedVertexContext argument = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return ARGUMENT.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };

        IsolatedVertexContext field = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                Vertex field = schemaGraph.getFieldOrDirectiveForArgument(argument);
                return field.getName();
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(argument);
                return fieldOrDirective.getType().equals(FIELD);
            }
        };
        IsolatedVertexContext container = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex argument, SchemaGraph schemaGraph) {
                Vertex field = schemaGraph.getFieldOrDirectiveForArgument(argument);
                Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(field);
                // can be Interface or Object
                return fieldsContainer.getType() + "." + fieldsContainer.getName();
            }

            @Override
            public boolean filter(Vertex argument, SchemaGraph schemaGraph) {
                Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(argument);
                return fieldOrDirective.getType().equals(FIELD);
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(argument, container, field);
        return contexts;
    }


    public FillupIsolatedVertices(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        this.sourceGraph = sourceGraph;
        this.targetGraph = targetGraph;
        this.isolatedVertices = new IsolatedVertices();
    }

    public void ensureGraphAreSameSize() {
        calcIsolatedVertices(typeContexts.get(FIELD), FIELD);
        calcIsolatedVertices(typeContexts.get(ARGUMENT), ARGUMENT);
        calcIsolatedVertices(typeContexts.get(INPUT_FIELD), INPUT_FIELD);
        calcIsolatedVertices(typeContexts.get(DUMMY_TYPE_VERTEX), DUMMY_TYPE_VERTEX);
        calcIsolatedVertices(typeContexts.get(OBJECT), OBJECT);
        calcIsolatedVertices(typeContexts.get(INTERFACE), INTERFACE);
        calcIsolatedVertices(typeContexts.get(UNION), UNION);
        calcIsolatedVertices(typeContexts.get(INPUT_OBJECT), INPUT_OBJECT);
        calcIsolatedVertices(typeContexts.get(SCALAR), SCALAR);
        calcIsolatedVertices(typeContexts.get(ENUM), ENUM);
        calcIsolatedVertices(typeContexts.get(ENUM_VALUE), ENUM_VALUE);
        calcIsolatedVertices(typeContexts.get(APPLIED_DIRECTIVE), APPLIED_DIRECTIVE);
        calcIsolatedVertices(typeContexts.get(APPLIED_ARGUMENT), APPLIED_ARGUMENT);
        calcIsolatedVertices(typeContexts.get(DIRECTIVE), DIRECTIVE);


        sourceGraph.addVertices(isolatedVertices.allIsolatedSource);
        targetGraph.addVertices(isolatedVertices.allIsolatedTarget);

        if (sourceGraph.size() < targetGraph.size()) {
            isolatedVertices.isolatedBuiltInSourceVertices.addAll(sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size(), "source-isolated-builtin-"));
        } else if (sourceGraph.size() > targetGraph.size()) {
            isolatedVertices.isolatedBuiltInTargetVertices.addAll(targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size(), "target-isolated-builtin-"));
        }

        System.out.println(isolatedVertices);
    }

    private static List<IsolatedVertexContext> inputFieldContexts() {
        IsolatedVertexContext inputFieldContext = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return INPUT_FIELD.equals(vertex.getType()) && !vertex.isBuiltInType();
            }
        };
        IsolatedVertexContext inputObjectContext = new IsolatedVertexContext() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                Vertex inputObject = schemaGraph.getInputObjectForInputField(vertex);
                return inputObject.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<IsolatedVertexContext> contexts = Arrays.asList(inputFieldContext, inputObjectContext);
        return contexts;
    }

    public abstract static class IsolatedVertexContext {
//        // it is always by type first
//        final String type;
//        // then a list of names
//        List<String> subContextIds = new ArrayList<>();
//
//        public IsolatedVertexContext(String type) {
//            this.type = type;
//        }
//
//        public static IsolatedVertexContext newContext(String type) {
//            return new IsolatedVertexContext(type);
//        }
//
//        public static IsolatedVertexContext newContext(String type, String subContext1, String subContext2) {
//            IsolatedVertexContext result = new IsolatedVertexContext(type);
//            result.subContextIds.add(subContext1);
//            result.subContextIds.add(subContext2);
//            return result;
//        }
//
//        public static IsolatedVertexContext newContext(String type, String subContext1, String subContext2, String subContext3) {
//            IsolatedVertexContext result = new IsolatedVertexContext(type);
//            result.subContextIds.add(subContext1);
//            result.subContextIds.add(subContext2);
//            result.subContextIds.add(subContext3);
//            return result;
//        }
//
//        public static IsolatedVertexContext newContext(String type, String subContext1) {
//            IsolatedVertexContext result = new IsolatedVertexContext(type);
//            result.subContextIds.add(subContext1);
//            return result;
//        }

        public abstract String idForVertex(Vertex vertex, SchemaGraph schemaGraph);

        public abstract boolean filter(Vertex vertex, SchemaGraph schemaGraph);
    }

    /**
     * This is all about which vertices are allowed to map to which isolated vertices.
     * It maps a "context" to a list of isolated vertices.
     * Contexts are "InputField", "foo.InputObject.InputField"
     */
    public class IsolatedVertices {

        public Multimap<Object, Vertex> contextToIsolatedSourceVertices = HashMultimap.create();
        public Multimap<Object, Vertex> contextToIsolatedTargetVertices = HashMultimap.create();

        public Set<Vertex> allIsolatedSource = new LinkedHashSet<>();
        public Set<Vertex> allIsolatedTarget = new LinkedHashSet<>();

        public final Set<Vertex> isolatedBuiltInSourceVertices = new LinkedHashSet<>();
        public final Set<Vertex> isolatedBuiltInTargetVertices = new LinkedHashSet<>();


//        public void putSource(Object contextId, Vertex v) {
//            contextToIsolatedSourceVertices.put(contextId, v);
//
//        }

        public void putSource(Object contextId, Collection<Vertex> isolatedSourcedVertices) {
            contextToIsolatedSourceVertices.putAll(contextId, isolatedSourcedVertices);
            allIsolatedSource.addAll(isolatedSourcedVertices);
        }

//        public void putTarget(Object contextId, Vertex v) {
//            contextToIsolatedTargetVertices.put(contextId, v);
//        }

        public void putTarget(Object contextId, Collection<Vertex> isolatedTargetVertices) {
            contextToIsolatedTargetVertices.putAll(contextId, isolatedTargetVertices);
            allIsolatedTarget.addAll(isolatedTargetVertices);
        }

        public boolean mappingPossibleForIsolatedSource(Vertex isolatedSourceVertex, Vertex targetVertex) {
            List<IsolatedVertexContext> contexts = typeContexts.get(targetVertex.getType());
            Assert.assertNotNull(contexts);
            List<String> contextForVertex = new ArrayList<>();
            for (IsolatedVertexContext isolatedVertexContext : contexts) {
                contextForVertex.add(isolatedVertexContext.idForVertex(targetVertex, targetGraph));
            }
            if (!targetVertex.getType().equals(DUMMY_TYPE_VERTEX)) {
                contextForVertex.add(targetVertex.getName());
            }
            while (contextForVertex.size() > 0) {
                if (isolatedVertices.contextToIsolatedSourceVertices.containsKey(contextForVertex)) {
                    return isolatedVertices.contextToIsolatedSourceVertices.get(contextForVertex).contains(isolatedSourceVertex);
                }
                contextForVertex.remove(contextForVertex.size() - 1);
            }
            return false;
        }

        public boolean mappingPossibleForIsolatedTarget(Vertex sourceVertex, Vertex isolatedTargetVertex) {
            List<IsolatedVertexContext> contexts = typeContexts.get(sourceVertex.getType());
            Assert.assertNotNull(contexts);
            List<String> contextForVertex = new ArrayList<>();
            for (IsolatedVertexContext isolatedVertexContext : contexts) {
                contextForVertex.add(isolatedVertexContext.idForVertex(sourceVertex, sourceGraph));
            }
            if (!sourceVertex.getType().equals(DUMMY_TYPE_VERTEX)) {
                contextForVertex.add(sourceVertex.getName());
            }
            while (contextForVertex.size() > 0) {
                if (isolatedVertices.contextToIsolatedTargetVertices.containsKey(contextForVertex)) {
                    return isolatedVertices.contextToIsolatedTargetVertices.get(contextForVertex).contains(isolatedTargetVertex);
                }
                contextForVertex.remove(contextForVertex.size() - 1);
            }
            return false;

        }
    }


    public void calcIsolatedVertices(List<IsolatedVertexContext> contexts, String typeNameForDebug) {
        Collection<Vertex> currentSourceVertices = sourceGraph.getVertices();
        Collection<Vertex> currentTargetVertices = targetGraph.getVertices();
        calcImpl(currentSourceVertices, currentTargetVertices, Collections.emptyList(), 0, contexts, new LinkedHashSet<>(), new LinkedHashSet<>(), typeNameForDebug);
    }

    private void calcImpl(
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

        if (contextIx == 0) {
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
        diffNamedList(sourceGroups.keySet(), targetGroups.keySet(), deletedContexts, insertedContexts, sameContexts);
        for (String sameContext : sameContexts) {
            ImmutableList<Vertex> sourceVertices = sourceGroups.get(sameContext);
            ImmutableList<Vertex> targetVertices = targetGroups.get(sameContext);
            List<String> currentContextId = concat(contextId, sameContext);
            if (contexts.size() > contextIx + 1) {
                calcImpl(sourceVertices, targetVertices, currentContextId, contextIx + 1, contexts, usedSourceVertices, usedTargetVertices, typeNameForDebug);
            }

            Set<Vertex> notUsedSource = new LinkedHashSet<>(sourceVertices);
            notUsedSource.removeAll(usedSourceVertices);
            Set<Vertex> notUsedTarget = new LinkedHashSet<>(targetVertices);
            notUsedTarget.removeAll(usedTargetVertices);

            /**
             * We know that the first context is just by type and we have all the remaining vertices of the same
             * type here.
             */
            if (contextIx == 0) {
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

                ArrayList<Vertex> deletedVertices = new ArrayList<>();
                ArrayList<Vertex> insertedVertices = new ArrayList<>();
                HashBiMap<Vertex, Vertex> sameVertices = HashBiMap.create();
                diffVertices(notUsedSource, notUsedTarget, deletedVertices, insertedVertices, sameVertices, vertex -> {
                    return concat(currentContextId, vertex.getName());
                });
                usedSourceVertices.addAll(sourceGroups.get(sameContext));
                usedTargetVertices.addAll(targetGroups.get(sameContext));
                if (notUsedSource.size() > notUsedTarget.size()) {
                    Set<Vertex> newTargetVertices = Vertex.newIsolatedNodes(notUsedSource.size() - notUsedTarget.size(), "target-isolated-" + typeNameForDebug + "-");
                    // all deleted vertices can map to all new TargetVertices
                    for (Vertex deletedVertex : notUsedSource) {
                        isolatedVertices.putTarget(concat(currentContextId, deletedVertex.getName()), newTargetVertices);
                    }
                } else if (notUsedTarget.size() > notUsedSource.size()) {
                    Set<Vertex> newSourceVertices = Vertex.newIsolatedNodes(notUsedTarget.size() - notUsedSource.size(), "source-isolated-" + typeNameForDebug + "-");
                    // all inserted fields can map to all new source vertices
                    for (Vertex insertedVertex : notUsedTarget) {
                        isolatedVertices.putSource(concat(currentContextId, insertedVertex.getName()), newSourceVertices);
                    }
                }
            }

        }

    }


}
