package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import graphql.Assert;
import graphql.Internal;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import static java.util.Collections.singletonList;

/**
 * We don't want to allow arbitrary schema changes. For example changing an Object type into a Scalar
 * is not something we want to consider.
 * <p>
 * We do this to make SchemaDiffings better understandable, but also to improve the overall runtime of
 * the algorithm. By restricting the possible mappings the Schema diffing algo is actually able to
 * finish in a reasonable time for real life inputs.
 * <p>
 *
 * We restrict the algo by calculating which mappings are possible for given vertex. This is later used in
 * {@link DiffImpl#calcLowerBoundMappingCost}.
 * While doing this we need to also ensure that there are the same amount of vertices in the same "context":
 * for example if the source graph has 3 Objects, the target graph needs to have 3 Objects. We achieve this by
 * adding "isolated vertices" as needed.
 */
@Internal
public class PossibleMappingsCalculator {
    private final SchemaDiffingRunningCheck runningCheck;

    private final SchemaGraph sourceGraph;
    private final SchemaGraph targetGraph;
    private final PossibleMappings possibleMappings;

    public final static Map<String, List<VertexContextSegment>> typeContexts = new LinkedHashMap<>();

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
        VertexContextSegment appliedDirectiveIndex = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex appliedDirective, SchemaGraph schemaGraph) {
                int appliedDirectiveIndex = schemaGraph.getAppliedDirectiveIndex(appliedDirective);
                return Integer.toString(appliedDirectiveIndex);
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
        VertexContextSegment vertexContextSegment = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return parentOfParentOfContainer.idForVertex(vertex, schemaGraph) + "." +
                        parentOfContainer.idForVertex(vertex, schemaGraph) + "." +
                        appliedDirectiveContainer.idForVertex(vertex, schemaGraph) + "." +
                        appliedDirectiveName.idForVertex(vertex, schemaGraph);
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(appliedDirectiveType, vertexContextSegment, appliedDirectiveIndex);
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
        VertexContextSegment combined = new VertexContextSegment() {
            @Override
            public String idForVertex(Vertex vertex, SchemaGraph schemaGraph) {
                return parentOfContainer.idForVertex(vertex, schemaGraph) + "." +
                        parentOfContainer.idForVertex(vertex, schemaGraph) + "." +
                        appliedDirectiveContainer.idForVertex(vertex, schemaGraph) + "." +
                        appliedDirective.idForVertex(vertex, schemaGraph) + "." +
                        appliedArgumentName.idForVertex(vertex, schemaGraph);
            }
        };
        List<VertexContextSegment> contexts = Arrays.asList(appliedArgumentType, combined);
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


    public PossibleMappingsCalculator(SchemaGraph sourceGraph, SchemaGraph targetGraph, SchemaDiffingRunningCheck runningCheck) {
        this.runningCheck = runningCheck;
        this.sourceGraph = sourceGraph;
        this.targetGraph = targetGraph;
        this.possibleMappings = new PossibleMappings();
    }

    public PossibleMappings calculate() {
        calcPossibleMappings(typeContexts.get(SCHEMA), SCHEMA);
        calcPossibleMappings(typeContexts.get(FIELD), FIELD);
        calcPossibleMappings(typeContexts.get(ARGUMENT), ARGUMENT);
        calcPossibleMappings(typeContexts.get(INPUT_FIELD), INPUT_FIELD);
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


        sourceGraph.addVertices(possibleMappings.allIsolatedSource);
        targetGraph.addVertices(possibleMappings.allIsolatedTarget);

        Assert.assertTrue(sourceGraph.size() == targetGraph.size());
        Set<Vertex> vertices = possibleMappings.possibleMappings.keySet();
        for (Vertex vertex : vertices) {
            if (possibleMappings.possibleMappings.get(vertex).size() > 1) {
//                System.out.println("vertex with possible mappings: " + possibleMappings.possibleMappings.get(vertex).size());
//                System.out.println("vertex " + vertex);
//                System.out.println("-------------");
            }
        }
        return possibleMappings;
    }


    public abstract static class VertexContextSegment {


        public VertexContextSegment() {
        }

        public abstract String idForVertex(Vertex vertex, SchemaGraph schemaGraph);

        public boolean filter(Vertex vertex, SchemaGraph schemaGraph) {
            return true;
        }
    }

    public class PossibleMappings {

        public Set<Vertex> allIsolatedSource = new LinkedHashSet<>();
        public Set<Vertex> allIsolatedTarget = new LinkedHashSet<>();

//        public Table<List<String>, Set<Vertex>, Set<Vertex>> contexts = HashBasedTable.create();

        public Multimap<Vertex, Vertex> possibleMappings = HashMultimap.create();

        public BiMap<Vertex, Vertex> fixedOneToOneMappings = HashBiMap.create();
        public List<Vertex> fixedOneToOneSources = new ArrayList<>();
        public List<Vertex> fixedOneToOneTargets = new ArrayList<>();

        public void putPossibleMappings(List<String> contextId,
                                        Collection<Vertex> sourceVertices,
                                        Collection<Vertex> targetVertices,
                                        String typeName) {
            if (sourceVertices.isEmpty() && targetVertices.isEmpty()) {
                return;
            }
            if (sourceVertices.size() == 1 && targetVertices.size() == 1) {
                Vertex sourceVertex = sourceVertices.iterator().next();
                Vertex targetVertex = targetVertices.iterator().next();
                fixedOneToOneMappings.put(sourceVertex, targetVertex);
                fixedOneToOneSources.add(sourceVertex);
                fixedOneToOneTargets.add(targetVertex);
                return;
            }

            if (APPLIED_DIRECTIVE.equals(typeName) || APPLIED_ARGUMENT.equals(typeName)) {
                for (Vertex sourceVertex : sourceVertices) {
                    Vertex isolatedTarget = Vertex.newIsolatedNode("target-isolated-" + typeName);
                    allIsolatedTarget.add(isolatedTarget);
                    fixedOneToOneMappings.put(sourceVertex, isolatedTarget);
                    fixedOneToOneSources.add(sourceVertex);
                    fixedOneToOneTargets.add(isolatedTarget);
                }
                for (Vertex targetVertex : targetVertices) {
                    Vertex isolatedSource = Vertex.newIsolatedNode("source-isolated-" + typeName);
                    allIsolatedSource.add(isolatedSource);
                    fixedOneToOneMappings.put(isolatedSource, targetVertex);
                    fixedOneToOneSources.add(isolatedSource);
                    fixedOneToOneTargets.add(targetVertex);
                }
                return;
            }

            Set<Vertex> newIsolatedSource = Collections.emptySet();
            Set<Vertex> newIsolatedTarget = Collections.emptySet();
            if (sourceVertices.size() > targetVertices.size()) {
                newIsolatedTarget = Vertex.newIsolatedNodes(sourceVertices.size() - targetVertices.size(), "target-isolated-" + typeName + "-");
            } else if (targetVertices.size() > sourceVertices.size()) {
                newIsolatedSource = Vertex.newIsolatedNodes(targetVertices.size() - sourceVertices.size(), "source-isolated-" + typeName + "-");
            }
            this.allIsolatedSource.addAll(newIsolatedSource);
            this.allIsolatedTarget.addAll(newIsolatedTarget);

            if (sourceVertices.size() == 0) {
                Iterator<Vertex> iterator = newIsolatedSource.iterator();
                for (Vertex targetVertex : targetVertices) {
                    Vertex isolatedSourceVertex = iterator.next();
                    fixedOneToOneMappings.put(isolatedSourceVertex, targetVertex);
                    fixedOneToOneSources.add(isolatedSourceVertex);
                    fixedOneToOneTargets.add(targetVertex);
                }
                return;
            }
            if (targetVertices.size() == 0) {
                Iterator<Vertex> iterator = newIsolatedTarget.iterator();
                for (Vertex sourceVertex : sourceVertices) {
                    Vertex isolatedTargetVertex = iterator.next();
                    fixedOneToOneMappings.put(sourceVertex, isolatedTargetVertex);
                    fixedOneToOneSources.add(sourceVertex);
                    fixedOneToOneTargets.add(isolatedTargetVertex);
                }
                return;
            }

//            System.out.println("multiple mappings for context" + contextId + " overall size: " + (sourceVertices.size() + newIsolatedSource.size()));
//            List<VertexContextSegment> vertexContextSegments = typeContexts.get(typeName);
//            System.out.println("source ids: " + sourceVertices.size());
//            for (Vertex sourceVertex : sourceVertices) {
//                List<String> id = vertexContextSegments.stream().map(vertexContextSegment -> vertexContextSegment.idForVertex(sourceVertex, sourceGraph))
//                        .collect(Collectors.toList());
//                System.out.println("id: " + id);
//            }
//            System.out.println("target ids ==================: " + targetVertices.size());
//            for (Vertex targetVertex : targetVertices) {
//                List<String> id = vertexContextSegments.stream().map(vertexContextSegment -> vertexContextSegment.idForVertex(targetVertex, targetGraph))
//                        .collect(Collectors.toList());
//                System.out.println("id: " + id);
//            }
//            System.out.println("-------------------");
//            System.out.println("-------------------");


            for (Vertex sourceVertex : sourceVertices) {
                possibleMappings.putAll(sourceVertex, targetVertices);
                possibleMappings.putAll(sourceVertex, newIsolatedTarget);
            }
            for (Vertex sourceIsolatedVertex : newIsolatedSource) {
                possibleMappings.putAll(sourceIsolatedVertex, targetVertices);
                possibleMappings.putAll(sourceIsolatedVertex, newIsolatedTarget);
            }

        }

        //
        public boolean mappingPossible(Vertex sourceVertex, Vertex targetVertex) {
            return possibleMappings.containsEntry(sourceVertex, targetVertex);
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

            possibleMappings.putPossibleMappings(currentContextId, notUsedSource, notUsedTarget, typeNameForDebug);
            usedSourceVertices.addAll(notUsedSource);
            usedTargetVertices.addAll(notUsedTarget);
        }

        /**
         * update the used vertices with the deleted and inserted contexts
         */
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
        if (contextId.size() == 0) {
            contextId = singletonList(typeNameForDebug);
        }
        possibleMappings.putPossibleMappings(contextId, possibleSourceVertices, possibleTargetVertices, typeNameForDebug);
    }

    public PossibleMappings getIsolatedVertices() {
        return possibleMappings;
    }
}
