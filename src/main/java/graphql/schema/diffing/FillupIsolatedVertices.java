package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import graphql.Assert;
import graphql.Internal;
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

import static graphql.schema.diffing.SchemaGraph.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraph.APPLIED_DIRECTIVE;
import static graphql.schema.diffing.SchemaGraph.ARGUMENT;
import static graphql.schema.diffing.SchemaGraph.DIRECTIVE;
import static graphql.schema.diffing.SchemaGraph.ENUM;
import static graphql.schema.diffing.SchemaGraph.ENUM_VALUE;
import static graphql.schema.diffing.SchemaGraph.FIELD;
import static graphql.schema.diffing.SchemaGraph.INPUT_FIELD;
import static graphql.schema.diffing.SchemaGraph.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraph.INTERFACE;
import static graphql.schema.diffing.SchemaGraph.OBJECT;
import static graphql.schema.diffing.SchemaGraph.SCALAR;
import static graphql.schema.diffing.SchemaGraph.SCHEMA;
import static graphql.schema.diffing.SchemaGraph.UNION;
import static graphql.util.FpKit.concat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Internal
public class FillupIsolatedVertices {
    private final SchemaDiffingRunningCheck runningCheck;

    private final SchemaGraph sourceGraph;
    private final SchemaGraph targetGraph;
    private final IsolatedVertices isolatedVertices;

    private final BiMap<Vertex, Vertex> toRemove = HashBiMap.create();

    final static Map<String, List<VertexContextSegment>> typeContexts = new LinkedHashMap<>();

    static {
        typeContexts.put(SCHEMA, schemaContext());
        typeContexts.put(FIELD, fieldContext());
        typeContexts.put(ARGUMENT, argumentsContexts());
        typeContexts.put(INPUT_FIELD, inputFieldContexts());
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
        VertexContextSegment inputObjectName = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex inputObject, SchemaGraph schemaGraph) {
                return inputObject.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(inputObject, inputObjectName);
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
                int appliedDirectiveIndex = schemaGraph.getAppliedDirectiveIndex(appliedDirective);
                return appliedDirectiveIndex + ":" + appliedDirective.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        VertexContextSegment appliedDirectiveContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                Vertex appliedDirectiveContainer = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                return appliedDirectiveContainer.getType() + "." + appliedDirectiveContainer.getName();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        VertexContextSegment parentOfContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                Vertex container = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                switch (container.getType()) {
                    case SCHEMA:
                        return SCHEMA;
                    case FIELD:
                        Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(container);
                        return fieldsContainer.getType() + "." + fieldsContainer.getName();
                    case OBJECT:
                        return OBJECT;
                    case INTERFACE:
                        return INTERFACE;
                    case INPUT_FIELD:
                        Vertex inputObject = schemaGraph.getInputObjectForInputField(container);
                        return inputObject.getType() + "." + inputObject.getName();
                    case ARGUMENT:
                        Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(container);
                        return fieldOrDirective.getType() + "." + fieldOrDirective.getName();
                    case INPUT_OBJECT:
                        return INPUT_OBJECT;
                    case ENUM:
                        return ENUM;
                    case UNION:
                        return UNION;
                    case SCALAR:
                        return SCALAR;
                    case ENUM_VALUE:
                        Vertex enumVertex = schemaGraph.getEnumForEnumValue(container);
                        return enumVertex.getType() + "." + enumVertex.getName();
                    default:
                        throw new IllegalStateException("Unexpected directive container type " + container.getType());
                }
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        VertexContextSegment parentOfParentOfContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                Vertex container = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                switch (container.getType()) {
                    case SCHEMA:
                    case FIELD:
                    case OBJECT:
                    case INTERFACE:
                    case INPUT_FIELD:
                    case INPUT_OBJECT:
                    case ENUM:
                    case ENUM_VALUE:
                    case UNION:
                    case SCALAR:
                        return "";
                    case ARGUMENT:
                        Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(container);
                        switch (fieldOrDirective.getType()) {
                            case FIELD:
                                Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(fieldOrDirective);
                                return fieldsContainer.getType() + "." + fieldsContainer.getName();
                            case DIRECTIVE:
                                return "";
                            default:
                                return Assert.assertShouldNeverHappen();
                        }
                    default:
                        throw new IllegalStateException("Unexpected directive container type " + container.getType());
                }
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(appliedDirectiveType, parentOfParentOfContainer, parentOfContainer, appliedDirectiveContainer, appliedDirectiveName);
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
                int appliedDirectiveIndex = schemaGraph.getAppliedDirectiveIndex(appliedDirective);
                return appliedDirectiveIndex + ":" + appliedDirective.getName();
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
        VertexContextSegment parentOfContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                Vertex appliedDirective = schemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
                Vertex container = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                switch (container.getType()) {
                    case SCHEMA:
                        return SCHEMA;
                    case FIELD:
                        Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(container);
                        return fieldsContainer.getType() + "." + fieldsContainer.getName();
                    case OBJECT:
                        return OBJECT;
                    case INTERFACE:
                        return INTERFACE;
                    case INPUT_FIELD:
                        Vertex inputObject = schemaGraph.getInputObjectForInputField(container);
                        return inputObject.getType() + "." + inputObject.getName();
                    case ARGUMENT:
                        Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(container);
                        return fieldOrDirective.getType() + "." + fieldOrDirective.getName();
                    case INPUT_OBJECT:
                        return INPUT_OBJECT;
                    case ENUM:
                        return ENUM;
                    case UNION:
                        return UNION;
                    case SCALAR:
                        return SCALAR;
                    case ENUM_VALUE:
                        Vertex enumVertex = schemaGraph.getEnumForEnumValue(container);
                        return enumVertex.getType() + "." + enumVertex.getName();
                    default:
                        throw new IllegalStateException("Unexpected directive container type " + container.getType());
                }
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return true;
            }
        };

        VertexContextSegment parentOfParentOfContainer = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedArgument, SchemaGraph schemaGraph) {
                Vertex appliedDirective = schemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
                Vertex container = schemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
                switch (container.getType()) {
                    case SCHEMA:
                    case FIELD:
                    case OBJECT:
                    case INTERFACE:
                    case INPUT_FIELD:
                    case INPUT_OBJECT:
                    case ENUM:
                    case ENUM_VALUE:
                    case UNION:
                    case SCALAR:
                        return "";
                    case ARGUMENT:
                        Vertex fieldOrDirective = schemaGraph.getFieldOrDirectiveForArgument(container);
                        switch (fieldOrDirective.getType()) {
                            case FIELD:
                                Vertex fieldsContainer = schemaGraph.getFieldsContainerForField(fieldOrDirective);
                                return fieldsContainer.getType() + "." + fieldsContainer.getName();
                            case DIRECTIVE:
                                return "";
                            default:
                                return Assert.assertShouldNeverHappen();
                        }
                    default:
                        throw new IllegalStateException("Unexpected directive container type " + container.getType());
                }
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
        List<VertexContextSegment> contexts = Arrays.asList(appliedArgumentType, parentOfParentOfContainer, parentOfContainer, appliedDirectiveContainer, appliedDirective, appliedArgumentName);
        return contexts;
    }

    private static List<VertexContextSegment> schemaContext() {
        VertexContextSegment schema = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return vertex.getType();
            }

            @Override
            public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
                return SCHEMA.equals(vertex.getType());
            }
        };
        return singletonList(schema);
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

    private static List<VertexContextSegment> argumentsContexts() {

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


    public FillupIsolatedVertices(SchemaGraph sourceGraph, SchemaGraph targetGraph, SchemaDiffingRunningCheck runningCheck) {
        this.runningCheck = runningCheck;
        this.sourceGraph = sourceGraph;
        this.targetGraph = targetGraph;
        this.isolatedVertices = new IsolatedVertices();
    }

    public void ensureGraphAreSameSize() {
        calcPossibleMappings(typeContexts.get(SCHEMA), SCHEMA);
        calcPossibleMappings(typeContexts.get(FIELD), FIELD);
        calcPossibleMappings(typeContexts.get(ARGUMENT), ARGUMENT);
        calcPossibleMappings(typeContexts.get(INPUT_FIELD), INPUT_FIELD);
//        calcPossibleMappings(typeContexts.get(DUMMY_TYPE_VERTEX), DUMMY_TYPE_VERTEX);
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

        public Set<Vertex> allIsolatedSource = new LinkedHashSet<>();
        public Set<Vertex> allIsolatedTarget = new LinkedHashSet<>();

        public Table<List<String>, Set<Vertex>, Set<Vertex>> contexts = HashBasedTable.create();

        public Multimap<Vertex, Vertex> possibleMappings = HashMultimap.create();
        public Mapping mapping = new Mapping();

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

        //
        public boolean mappingPossible(Vertex sourceVertex, Vertex targetVertex) {
            return possibleMappings.containsEntry(sourceVertex, targetVertex);
        }

        public void putContext(List<String> contextId, Set<Vertex> source, Set<Vertex> target) {
            if (contexts.containsRow(contextId)) {
                throw new IllegalArgumentException("Already context " + contextId);
            }
            Assert.assertTrue(source.size() == target.size());
            if (source.size() == 1) {
                mapping.add(source.iterator().next(), target.iterator().next());
            }
            contexts.put(contextId, source, target);
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
        runningCheck.check();

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
        Util.diffNamedList(sourceGroups.keySet(), targetGroups.keySet(), deletedContexts, insertedContexts, sameContexts);

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

            // make sure the current context is the same size
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
            if (notUsedSource.size() > 0) {
                isolatedVertices.putContext(currentContextId, notUsedSource, notUsedTarget);
            }
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
        // if there are only added or removed vertices in the current context, contextId might be empty
        if (possibleSourceVertices.size() > 0) {
            if (contextId.size() == 0) {
                contextId = singletonList(typeNameForDebug);
            }
            isolatedVertices.putContext(contextId, possibleSourceVertices, possibleTargetVertices);
        }
        isolatedVertices.putPossibleMappings(possibleSourceVertices, possibleTargetVertices);
    }

    public IsolatedVertices getIsolatedVertices() {
        return isolatedVertices;
    }
}
