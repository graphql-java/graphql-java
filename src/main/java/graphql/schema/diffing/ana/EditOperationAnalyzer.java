package graphql.schema.diffing.ana;

import graphql.Assert;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.Edge;
import graphql.schema.diffing.EditOperation;
import graphql.schema.diffing.SchemaGraph;
import graphql.schema.diffing.Vertex;
import graphql.schema.idl.ScalarInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.ana.SchemaChanges.*;

/**
 * Higher level GraphQL semantic assigned to
 */
public class EditOperationAnalyzer {

    private GraphQLSchema oldSchema;
    private GraphQLSchema newSchema;
    private SchemaGraph oldSchemaGraph;
    private SchemaGraph newSchemaGraph;

//    private List<SchemaChange> changes = new ArrayList<>();

    private Map<String, SchemaChange.ObjectChange> objectChanges = new LinkedHashMap<>();
    private Map<String, SchemaChange.InterfaceChange> interfaceChanges = new LinkedHashMap<>();
    private Map<String, SchemaChange.UnionChange> unionChanges = new LinkedHashMap<>();
    private Map<String, SchemaChange.EnumChange> enumChanges = new LinkedHashMap<>();
    private Map<String, SchemaChange.InputObjectChange> inputObjectChanges = new LinkedHashMap<>();
    private Map<String, SchemaChange.ScalarChange> scalarChanges = new LinkedHashMap<>();

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
        return new EditOperationAnalysisResult(objectChanges, interfaceChanges, unionChanges, enumChanges, inputObjectChanges, scalarChanges);
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
                    if(editOperation.getTargetVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldAdded(editOperation);
                    }
            }
        }

    }

    private void fieldChanged(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            ObjectModified objectModified = getObjectModified(fieldsContainerForField.getName());
            String oldName = editOperation.getSourceVertex().getName();
            String newName = field.getName();
            objectModified.getObjectModifiedDetails().add(new ObjectModified.FieldRenamed(oldName, newName));
        }
    }

    private void fieldAdded(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            if(isNewObject(fieldsContainerForField.getName())) {
                return;
            }
            ObjectModified objectModified = getObjectModified(fieldsContainerForField.getName());
            String name = field.getName();
            objectModified.getObjectModifiedDetails().add(new ObjectModified.FieldAdded(name));
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
                    modifiedTypeVertex(editOperation);
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

    private void modifiedTypeVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                modifiedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                modifiedInterface(editOperation);
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
//                case CHANGE_EDGE:
//                    changedEdge(editOperation);
//                    break;
            }
        }
    }

    private void insertedEdge(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        if (newEdge.getLabel().startsWith("implements ")) {
            newInterfaceAddedToInterfaceOrObject(newEdge);
        }
//        else if(newEdge)
    }

    private void newInterfaceAddedToInterfaceOrObject(Edge newEdge) {
        Vertex from = newEdge.getFrom();
        if (from.isOfType(SchemaGraph.OBJECT)) {
            if (isNewObject(from.getName())) {
                return;
            }
            Vertex objectVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            ObjectModified.AddedInterfaceToObjectDetail addedInterfaceToObjectDetail = new ObjectModified.AddedInterfaceToObjectDetail(interfaceVertex.getName());
            getObjectModified(objectVertex.getName()).getObjectModifiedDetails().add(addedInterfaceToObjectDetail);

        } else if (from.isOfType(SchemaGraph.INTERFACE)) {
            if (isNewInterface(from.getName())) {
                return;
            }
            Vertex interfaceFromVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            InterfaceModified.AddedInterfaceToInterfaceDetail addedInterfaceToObjectDetail = new InterfaceModified.AddedInterfaceToInterfaceDetail(interfaceVertex.getName());
            getInterfaceModified(interfaceFromVertex.getName()).getInterfaceChangeDetails().add(addedInterfaceToObjectDetail);
        } else {
            Assert.assertShouldNeverHappen("expected an implementation edge");
        }

    }

    private boolean isNewObject(String name) {
        return objectChanges.containsKey(name) && objectChanges.get(name) instanceof ObjectAdded;
    }

    private boolean isNewInterface(String name) {
        return interfaceChanges.containsKey(name) && interfaceChanges.get(name) instanceof InterfaceAdded;
    }

    private ObjectModified getObjectModified(String newName) {
        if (!objectChanges.containsKey(newName)) {
            objectChanges.put(newName, new ObjectModified(newName));
        }
        assertTrue(objectChanges.get(newName) instanceof ObjectModified);
        return (ObjectModified) objectChanges.get(newName);
    }

    private InterfaceModified getInterfaceModified(String newName) {
        if (!interfaceChanges.containsKey(newName)) {
            interfaceChanges.put(newName, new InterfaceModified(newName));
        }
        assertTrue(interfaceChanges.get(newName) instanceof InterfaceModified);
        return (InterfaceModified) interfaceChanges.get(newName);
    }



    private void addedObject(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();
        ObjectAdded objectAdded = new ObjectAdded(objectName);
        objectChanges.put(objectName, objectAdded);
    }

    private void addedInterface(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        InterfaceAdded interfacedAdded = new InterfaceAdded(objectName);
        interfaceChanges.put(objectName, interfacedAdded);
    }

    private void addedUnion(EditOperation editOperation) {
        String unionName = editOperation.getTargetVertex().getName();

        UnionAdded unionAdded = new UnionAdded(unionName);
        unionChanges.put(unionName, unionAdded);
    }

    private void addedInputObject(EditOperation editOperation) {
        String inputObjectName = editOperation.getTargetVertex().getName();

        InputObjectAdded added = new InputObjectAdded(inputObjectName);
        inputObjectChanges.put(inputObjectName, added);
    }

    private void addedEnum(EditOperation editOperation) {
        String enumName = editOperation.getTargetVertex().getName();

        EnumAdded enumAdded = new EnumAdded(enumName);
        enumChanges.put(enumName, enumAdded);
    }

    private void addedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getTargetVertex().getName();
        // build in scalars can appear as added when not used in the old schema, but
        // we don't want to register them as new Scalars
        if (ScalarInfo.isGraphqlSpecifiedScalar(scalarName)) {
            return;
        }

        ScalarAdded scalarAdded = new ScalarAdded(scalarName);
        scalarChanges.put(scalarName, scalarAdded);
    }



    private void removedObject(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        objectChanges.put(objectName, change);
    }

    private void removedInterface(EditOperation editOperation) {
        String interfaceName = editOperation.getSourceVertex().getName();

        InterfaceRemoved change = new InterfaceRemoved(interfaceName);
        interfaceChanges.put(interfaceName, change);
    }

    private void removedUnion(EditOperation editOperation) {
        String unionName = editOperation.getSourceVertex().getName();

        UnionRemoved change = new UnionRemoved(unionName);
        unionChanges.put(unionName, change);
    }

    private void removedInputObject(EditOperation editOperation) {
        String name = editOperation.getSourceVertex().getName();

        InputObjectRemoved change = new InputObjectRemoved(name);
        inputObjectChanges.put(name, change);
    }

    private void removedEnum(EditOperation editOperation) {
        String enumName = editOperation.getSourceVertex().getName();

        EnumRemoved change = new EnumRemoved(enumName);
        enumChanges.put(enumName, change);
    }

    private void removedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getSourceVertex().getName();

        ScalarRemoved change = new ScalarRemoved(scalarName);
        scalarChanges.put(scalarName, change);
    }

    private void modifiedObject(EditOperation editOperation) {
//        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();
//
        ObjectModified objectModified = new ObjectModified(objectName);
        objectChanges.put(objectName, objectModified);
    }

    private void modifiedInterface(EditOperation editOperation) {
        String interfaceName = editOperation.getTargetVertex().getName();
        InterfaceModified interfaceModified = new InterfaceModified(interfaceName);
        // we store the modification against the new name
        interfaceChanges.put(interfaceName, interfaceModified);
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
//        FieldModified objectAdded = new FieldModified(field.getName(), fieldsContainerForField.getName());
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
