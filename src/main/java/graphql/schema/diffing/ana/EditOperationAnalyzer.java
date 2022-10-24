package graphql.schema.diffing.ana;

import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.Edge;
import graphql.schema.diffing.EditOperation;
import graphql.schema.diffing.SchemaGraph;
import graphql.schema.diffing.Vertex;
import graphql.schema.idl.ScalarInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.ana.SchemaDifference.*;
import static graphql.schema.diffing.ana.SchemaDifference.EnumDifference;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectDifference;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceDifference;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceModification;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectDifference;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectModification;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarDifference;
import static graphql.schema.diffing.ana.SchemaDifference.UnionDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.UnionDifference;

/**
 * Higher level GraphQL semantic assigned to
 */
@ExperimentalApi
public class EditOperationAnalyzer {

    private GraphQLSchema oldSchema;
    private GraphQLSchema newSchema;
    private SchemaGraph oldSchemaGraph;
    private SchemaGraph newSchemaGraph;

//    private List<SchemaChange> changes = new ArrayList<>();

    private Map<String, ObjectDifference> objectDifferences = new LinkedHashMap<>();
    private Map<String, InterfaceDifference> interfaceDifferences = new LinkedHashMap<>();
    private Map<String, UnionDifference> unionDifferences = new LinkedHashMap<>();
    private Map<String, EnumDifference> enumDifferences = new LinkedHashMap<>();
    private Map<String, InputObjectDifference> inputObjectDifferences = new LinkedHashMap<>();
    private Map<String, ScalarDifference> scalarDifferences = new LinkedHashMap<>();

    public EditOperationAnalyzer(GraphQLSchema oldSchema,
                                 GraphQLSchema newSchema,
                                 SchemaGraph oldSchemaGraph,
                                 SchemaGraph newSchemaGraph
    ) {
        this.oldSchema = oldSchema;
        this.newSchema = newSchema;
        this.oldSchemaGraph = oldSchemaGraph;
        this.newSchemaGraph = newSchemaGraph;
    }

    public EditOperationAnalysisResult analyzeEdits(List<EditOperation> editOperations) {
        handleTypeVertexChanges(editOperations);
        handleEdgeChanges(editOperations);
        handleOtherChanges(editOperations);
        return new EditOperationAnalysisResult(objectDifferences, interfaceDifferences, unionDifferences, enumDifferences, inputObjectDifferences, scalarDifferences);
    }

    private void handleOtherChanges(List<EditOperation> editOperations) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case CHANGE_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldChanged(editOperation);
                    }
                    break;
                case INSERT_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldAdded(editOperation);
                    }
                    break;
                case DELETE_VERTEX:
                    if (editOperation.getSourceVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        removedArgument(editOperation);
                    }
            }
        }

    }

    private void fieldChanged(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            ObjectModification objectModification = getObjectModification(fieldsContainerForField.getName());
            String oldName = editOperation.getSourceVertex().getName();
            String newName = field.getName();
            objectModification.getDetails().add(new ObjectFieldRename(oldName, newName));
        }
    }

    private void fieldAdded(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            if (isNewObject(fieldsContainerForField.getName())) {
                return;
            }
            ObjectModification objectModification = getObjectModification(fieldsContainerForField.getName());
            String name = field.getName();
            objectModification.getDetails().add(new ObjectFieldAddition(name));
        }
    }


    private void handleTypeVertexChanges(List<EditOperation> editOperations) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_VERTEX:
                    insertedTypeVertex(editOperation);
                    break;
                case DELETE_VERTEX:
                    deletedTypeVertex(editOperation);
                    break;
                case CHANGE_VERTEX:
                    changedTypeVertex(editOperation);
                    break;
            }
        }
    }

    private void insertedTypeVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                addedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                addedInterface(editOperation);
                break;
            case SchemaGraph.UNION:
                addedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                addedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                addedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                addedScalar(editOperation);
                break;
        }

    }

    private void deletedTypeVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                removedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                removedInterface(editOperation);
                break;
            case SchemaGraph.UNION:
                removedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                removedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                removedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                removedScalar(editOperation);
                break;
        }
    }

    private void changedTypeVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                changedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                changedInterface(editOperation);
                break;
//            case SchemaGraph.UNION:
//                changedUnion(editOperation);
//                break;
//            case SchemaGraph.INPUT_OBJECT:
//                changedInputObject(editOperation);
//                break;
//            case SchemaGraph.ENUM:
//                changedEnum(editOperation);
//                break;
//            case SchemaGraph.SCALAR:
//                changedScalar(editOperation);
//                break;
        }

    }

    private void handleEdgeChanges(List<EditOperation> editOperations) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    insertedEdge(editOperation);
                    break;
//                case DELETE_EDGE:
//                    deletedEdge(editOperation);
//                    break;
                case CHANGE_EDGE:
                    changedEdge(editOperation);
                    break;
            }
        }
    }

    private void insertedEdge(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        if (newEdge.getLabel().startsWith("implements ")) {
            newInterfaceAddedToInterfaceOrObject(newEdge);
        }
    }

    private void changedEdge(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        if (newEdge.getLabel().startsWith("type=")) {
            typeEdgeChanged(editOperation);
        }
    }

    private void typeEdgeChanged(EditOperation editOperation) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex from = targetEdge.getFrom();
        Vertex to = targetEdge.getTo();
        if (from.isOfType(SchemaGraph.FIELD)) {
            fieldTypeChanged(editOperation);
        } else if (from.isOfType(SchemaGraph.ARGUMENT)) {
            argumentTypeChanged(editOperation);
        }
    }

    private void argumentTypeChanged(EditOperation editOperation) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex argument = targetEdge.getFrom();
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex objectOrInterface = newSchemaGraph.getFieldsContainerForField(field);
            String oldDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getSourceEdge());
            String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
            if (objectOrInterface.isOfType(SchemaGraph.OBJECT)) {
                ObjectFieldArgumentDefaultValueModification defaultValueModification = new ObjectFieldArgumentDefaultValueModification(
                        field.getName(),
                        argument.getName(),
                        oldDefaultValue,
                        newDefaultValue);
                getObjectModification(objectOrInterface.getName()).getDetails().add(defaultValueModification);
            } else {
                assertTrue(objectOrInterface.isOfType(SchemaGraph.INTERFACE));
                InterfaceFieldArgumentDefaultValueModification defaultValueModification = new InterfaceFieldArgumentDefaultValueModification(
                        field.getName(),
                        argument.getName(),
                        oldDefaultValue,
                        newDefaultValue);
                getInterfaceModification(objectOrInterface.getName()).getDetails().add(defaultValueModification);
            }

        }
    }

    private void fieldTypeChanged(EditOperation editOperation) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex field = targetEdge.getFrom();
        Vertex container = newSchemaGraph.getFieldsContainerForField(field);
        if (container.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = container;
            ObjectModification objectModification = getObjectModification(object.getName());
            String fieldName = field.getName();
            String oldType = getTypeFromEdgeLabel(editOperation.getSourceEdge());
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            objectModification.getDetails().add(new ObjectFieldTypeModification(fieldName, oldType, newType));
        }

    }

    // TODO: this is not great, we should avoid parsing the label like that
    private String getTypeFromEdgeLabel(Edge edge) {
        String label = edge.getLabel();
        assertTrue(label.startsWith("type="));
        String type = label.substring("type=".length(), label.indexOf(";"));
        return type;
    }

    private String getDefaultValueFromEdgeLabel(Edge edge) {
        String label = edge.getLabel();
        assertTrue(label.startsWith("type="));
        String defaultValue = label.substring(label.indexOf(";defaultValue=") + ";defaultValue=".length());
        return defaultValue;
    }


    private void newInterfaceAddedToInterfaceOrObject(Edge newEdge) {
        Vertex from = newEdge.getFrom();
        if (from.isOfType(SchemaGraph.OBJECT)) {
            if (isNewObject(from.getName())) {
                return;
            }
            Vertex objectVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            ObjectInterfaceImplementationAddition objectInterfaceImplementationAddition = new ObjectInterfaceImplementationAddition(interfaceVertex.getName());
            getObjectModification(objectVertex.getName()).getDetails().add(objectInterfaceImplementationAddition);

        } else if (from.isOfType(SchemaGraph.INTERFACE)) {
            if (isNewInterface(from.getName())) {
                return;
            }
            Vertex interfaceFromVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            InterfaceInterfaceImplementationAddition addition = new InterfaceInterfaceImplementationAddition(interfaceVertex.getName());
            getInterfaceModification(interfaceFromVertex.getName()).getDetails().add(addition);
        } else {
            Assert.assertShouldNeverHappen("expected an implementation edge");
        }

    }

    private boolean isNewObject(String name) {
        return objectDifferences.containsKey(name) && objectDifferences.get(name) instanceof ObjectAddition;
    }

    private boolean isDeletionObject(String name) {
        return objectDifferences.containsKey(name) && objectDifferences.get(name) instanceof ObjectDeletion;
    }

    private boolean isNewInterface(String name) {
        return interfaceDifferences.containsKey(name) && interfaceDifferences.get(name) instanceof InterfaceAddition;
    }

    private ObjectModification getObjectModification(String newName) {
        if (!objectDifferences.containsKey(newName)) {
            objectDifferences.put(newName, new ObjectModification(newName));
        }
        assertTrue(objectDifferences.get(newName) instanceof ObjectModification);
        return (ObjectModification) objectDifferences.get(newName);
    }

    private InterfaceModification getInterfaceModification(String newName) {
        if (!interfaceDifferences.containsKey(newName)) {
            interfaceDifferences.put(newName, new InterfaceModification(newName));
        }
        assertTrue(interfaceDifferences.get(newName) instanceof InterfaceModification);
        return (InterfaceModification) interfaceDifferences.get(newName);
    }


    private void addedObject(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();
        ObjectAddition objectAddition = new ObjectAddition(objectName);
        objectDifferences.put(objectName, objectAddition);
    }

    private void addedInterface(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        InterfaceAddition interfaceAddition = new InterfaceAddition(objectName);
        interfaceDifferences.put(objectName, interfaceAddition);
    }

    private void addedUnion(EditOperation editOperation) {
        String unionName = editOperation.getTargetVertex().getName();

        UnionAddition addition = new UnionAddition(unionName);
        unionDifferences.put(unionName, addition);
    }

    private void addedInputObject(EditOperation editOperation) {
        String inputObjectName = editOperation.getTargetVertex().getName();

        InputObjectAddition addition = new InputObjectAddition(inputObjectName);
        inputObjectDifferences.put(inputObjectName, addition);
    }

    private void addedEnum(EditOperation editOperation) {
        String enumName = editOperation.getTargetVertex().getName();

        EnumAddition enumAddition = new EnumAddition(enumName);
        enumDifferences.put(enumName, enumAddition);
    }

    private void addedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getTargetVertex().getName();
        // build in scalars can appear as added when not used in the old schema, but
        // we don't want to register them as new Scalars
        if (ScalarInfo.isGraphqlSpecifiedScalar(scalarName)) {
            return;
        }

        ScalarAddition addition = new ScalarAddition(scalarName);
        scalarDifferences.put(scalarName, addition);
    }


    private void removedObject(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectDeletion change = new ObjectDeletion(objectName);
        objectDifferences.put(objectName, change);
    }

    private void removedInterface(EditOperation editOperation) {
        String interfaceName = editOperation.getSourceVertex().getName();

        InterfaceDeletion change = new InterfaceDeletion(interfaceName);
        interfaceDifferences.put(interfaceName, change);
    }

    private void removedUnion(EditOperation editOperation) {
        String unionName = editOperation.getSourceVertex().getName();

        UnionDeletion change = new UnionDeletion(unionName);
        unionDifferences.put(unionName, change);
    }

    private void removedInputObject(EditOperation editOperation) {
        String name = editOperation.getSourceVertex().getName();

        InputObjectDeletion change = new InputObjectDeletion(name);
        inputObjectDifferences.put(name, change);
    }

    private void removedEnum(EditOperation editOperation) {
        String enumName = editOperation.getSourceVertex().getName();

        EnumDeletion deletion = new EnumDeletion(enumName);
        enumDifferences.put(enumName, deletion);
    }

    private void removedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getSourceVertex().getName();

        ScalarDeletion change = new ScalarDeletion(scalarName);
        scalarDifferences.put(scalarName, change);
    }

    private void removedArgument(EditOperation editOperation) {
        Vertex removedArgument = editOperation.getSourceVertex();
        Vertex fieldOrDirective = oldSchemaGraph.getFieldOrDirectiveForArgument(removedArgument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = oldSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentDeletion(field.getName(), removedArgument.getName()));
            }
        }

    }

    private void changedObject(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();
//
        ObjectModification objectModification = new ObjectModification(objectName);
        objectDifferences.put(objectName, objectModification);
    }

    private void changedInterface(EditOperation editOperation) {
        String interfaceName = editOperation.getTargetVertex().getName();
        InterfaceModification interfaceModification = new InterfaceModification(interfaceName);
        // we store the modification against the new name
        interfaceDifferences.put(interfaceName, interfaceModification);
    }
//
//    private void changedUnion(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        String objectName = editOperation.getTargetVertex().getName();
//
//        ObjectAdded objectAdded = new ObjectAdded(objectName);
//        changes.add(objectAdded);
//    }
//
//    private void changedEnum(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        String objectName = editOperation.getTargetVertex().getName();
//
//        ObjectAdded objectAdded = new ObjectAdded(objectName);
//        changes.add(objectAdded);
//    }
//
//    private void changedInputObject(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        String objectName = editOperation.getTargetVertex().getName();
//
//        ObjectAdded objectAdded = new ObjectAdded(objectName);
//        changes.add(objectAdded);
//    }
//
//    private void changedScalar(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        String objectName = editOperation.getTargetVertex().getName();
//
//        ObjectAdded objectAdded = new ObjectAdded(objectName);
//        changes.add(objectAdded);
//    }
//
//    private void changedField(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        Vertex field = editOperation.getTargetVertex();
//        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
//
//        FieldModification objectAdded = new FieldModification(field.getName(), fieldsContainerForField.getName());
//        changes.add(objectAdded);
//    }
//
//    private void changedInputField(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
//        String objectName = editOperation.getTargetVertex().getName();
//
//        ObjectAdded objectAdded = new ObjectAdded(objectName);
//        changes.add(objectAdded);
//    }


}
