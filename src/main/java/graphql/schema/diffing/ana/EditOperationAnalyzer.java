package graphql.schema.diffing.ana;

import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.Edge;
import graphql.schema.diffing.EditOperation;
import graphql.schema.diffing.Mapping;
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

    private Map<String, DirectiveDifference> directiveDifferences = new LinkedHashMap<>();

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

    public EditOperationAnalysisResult analyzeEdits(List<EditOperation> editOperations, Mapping mapping) {
        handleTypeVertexChanges(editOperations);

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
                    } else if (editOperation.getTargetVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentAdded(editOperation);
                    }
                    break;
                case DELETE_VERTEX:
                    if (editOperation.getSourceVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentDeleted(editOperation);
                    } else if (editOperation.getSourceVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldDeleted(editOperation);
                    }
            }
        }
        handleTypeChanges(editOperations, mapping);
        handleImplementsChanges(editOperations, mapping);
        handleUnionMemberChanges(editOperations, mapping);
        handleEnumValuesChanges(editOperations, mapping);
        handleArgumentChanges(editOperations, mapping);

        return new EditOperationAnalysisResult(
                objectDifferences,
                interfaceDifferences,
                unionDifferences,
                enumDifferences,
                inputObjectDifferences,
                scalarDifferences,
                directiveDifferences);
    }

    private void handleTypeChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            Edge newEdge = editOperation.getTargetEdge();
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    if (newEdge.getLabel().startsWith("type=")) {
                        typeEdgeInserted(editOperation, editOperations, mapping);
                    }
                    break;
                case CHANGE_EDGE:
                    if (newEdge.getLabel().startsWith("type=")) {
                        typeEdgeChanged(editOperation);
                    }
                    break;
            }
        }
    }

    private void handleUnionMemberChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    Edge newEdge = editOperation.getTargetEdge();
                    if (newEdge.getFrom().isOfType(SchemaGraph.UNION)) {
                        handleUnionMemberAdded(editOperation);
                    }
                    break;
                case DELETE_EDGE:
                    Edge oldEdge = editOperation.getSourceEdge();
                    if (oldEdge.getFrom().isOfType(SchemaGraph.UNION)) {
                        handleUnionMemberDeleted(editOperation);
                    }
                    break;
            }
        }
    }

    private void handleEnumValuesChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    Edge newEdge = editOperation.getTargetEdge();
                    if (newEdge.getFrom().isOfType(SchemaGraph.ENUM)) {
                        handleEnumValueAdded(editOperation);
                    }
                    break;
                case DELETE_EDGE:
                    Edge oldEdge = editOperation.getSourceEdge();
                    if (oldEdge.getFrom().isOfType(SchemaGraph.ENUM)) {
                        handleEnumValueDeleted(editOperation);
                    }
                    break;
            }
        }
    }

    private void handleArgumentChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case CHANGE_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        handleArgumentChange(editOperation);
                    }
            }
        }
    }

    private void handleArgumentChange(EditOperation editOperation) {
        Vertex argument = editOperation.getTargetVertex();
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE)) {
            Vertex directive = fieldOrDirective;
            DirectiveModification directiveModification = getDirectiveModification(directive.getName());
            String oldName = editOperation.getSourceVertex().getName();
            String newName = argument.getName();
            directiveModification.getDetails().add(new DirectiveArgumentRename(oldName, newName));

        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.FIELD));
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                ObjectModification objectModification = getObjectModification(object.getName());
                String oldName = editOperation.getSourceVertex().getName();
                String newName = argument.getName();
                objectModification.getDetails().add(new ObjectFieldArgumentRename(oldName, newName));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
                String oldName = editOperation.getSourceVertex().getName();
                String newName = argument.getName();
                interfaceModification.getDetails().add(new InterfaceFieldArgumentRename(oldName, newName));

            }
        }
    }

    private void handleImplementsChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    Edge newEdge = editOperation.getTargetEdge();
                    if (newEdge.getLabel().startsWith("implements ")) {
                        newInterfaceAddedToInterfaceOrObject(newEdge);
                    }
                    break;
                case DELETE_EDGE:
                    Edge oldEdge = editOperation.getSourceEdge();
                    if (oldEdge.getLabel().startsWith("implements ")) {
                        interfaceImplementationDeleted(oldEdge);
                    }
                    break;
            }
        }
    }

    private void handleUnionMemberAdded(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        Vertex union = newEdge.getFrom();
        if (isUnionAdded(union.getName())) {
            return;
        }
        Vertex newMemberObject = newEdge.getTo();
        UnionModification unionModification = getUnionModification(union.getName());
        unionModification.getDetails().add(new UnionMemberAddition(newMemberObject.getName()));
    }

    private void handleUnionMemberDeleted(EditOperation editOperation) {
        Edge deletedEdge = editOperation.getSourceEdge();
        Vertex union = deletedEdge.getFrom();
        if (isUnionDeleted(union.getName())) {
            return;
        }
        Vertex memberObject = deletedEdge.getTo();
        UnionModification unionModification = getUnionModification(union.getName());
        unionModification.getDetails().add(new UnionMemberDeletion(memberObject.getName()));
    }

    private void handleEnumValueAdded(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        Vertex enumVertex = newEdge.getFrom();
        if (isEnumAdded(enumVertex.getName())) {
            return;
        }
        Vertex newValue = newEdge.getTo();
        EnumModification enumModification = getEnumModification(enumVertex.getName());
        enumModification.getDetails().add(new EnumValueAddition(newValue.getName()));
    }

    private void handleEnumValueDeleted(EditOperation editOperation) {
        Edge deletedEdge = editOperation.getSourceEdge();
        Vertex enumVertex = deletedEdge.getFrom();
        if (isEnumDeleted(enumVertex.getName())) {
            return;
        }
        Vertex value = deletedEdge.getTo();
        EnumModification enumModification = getEnumModification(enumVertex.getName());
        enumModification.getDetails().add(new EnumValueDeletion(value.getName()));
    }


    private void fieldChanged(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = fieldsContainerForField;
            ObjectModification objectModification = getObjectModification(object.getName());
            String oldName = editOperation.getSourceVertex().getName();
            String newName = field.getName();
            objectModification.getDetails().add(new ObjectFieldRename(oldName, newName));
        } else {
            assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = fieldsContainerForField;
            InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
            String oldName = editOperation.getSourceVertex().getName();
            String newName = field.getName();
            interfaceModification.getDetails().add(new InterfaceFieldRename(oldName, newName));
        }
    }

    private void fieldAdded(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = fieldsContainerForField;
            if (isObjectAdded(object.getName())) {
                return;
            }
            ObjectModification objectModification = getObjectModification(object.getName());
            String name = field.getName();
            objectModification.getDetails().add(new ObjectFieldAddition(name));
        } else {
            assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = fieldsContainerForField;
            if (isInterfaceAdded(interfaze.getName())) {
                return;
            }
            InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
            String name = field.getName();
            interfaceModification.getDetails().add(new InterfaceFieldAddition(name));
        }
    }

    private void fieldDeleted(EditOperation editOperation) {
        Vertex deletedField = editOperation.getSourceVertex();
        Vertex fieldsContainerForField = oldSchemaGraph.getFieldsContainerForField(deletedField);
        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = fieldsContainerForField;
            if (isObjectDeleted(object.getName())) {
                return;
            }
            ObjectModification objectModification = getObjectModification(object.getName());
            String name = deletedField.getName();
            objectModification.getDetails().add(new ObjectFieldDeletion(name));
        } else {
            assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = fieldsContainerForField;
            if (isInterfaceDeleted(interfaze.getName())) {
                return;
            }
            InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
            String name = deletedField.getName();
            interfaceModification.getDetails().add(new InterfaceFieldDeletion(name));
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
            case SchemaGraph.DIRECTIVE:
                addedDirective(editOperation);
                break;
        }

    }

    private void deletedTypeVertex(EditOperation editOperation) {
        switch (editOperation.getSourceVertex().getType()) {
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
                deletedScalar(editOperation);
                break;
            case SchemaGraph.DIRECTIVE:
                deletedDirective(editOperation);
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
            case SchemaGraph.UNION:
                changedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                changedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                changedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                changedScalar(editOperation);
                break;
            case SchemaGraph.DIRECTIVE:
                changedDirective(editOperation);
                break;
        }

    }


    private void typeEdgeInserted(EditOperation editOperation, List<EditOperation> editOperations, Mapping mapping) {
        Edge newEdge = editOperation.getTargetEdge();
        Vertex from = newEdge.getFrom();
        if (from.isOfType(SchemaGraph.FIELD)) {
            typeEdgeInsertedForField(editOperation, editOperations, mapping);
        } else if (from.isOfType(SchemaGraph.ARGUMENT)) {
            typeEdgeInsertedForArgument(editOperation, editOperations, mapping);
        }

    }

    private void typeEdgeInsertedForArgument(EditOperation editOperation, List<EditOperation> editOperations, Mapping mapping) {
        Vertex argument = editOperation.getTargetEdge().getFrom();
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {

        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            if (isDirectiveAdded(directive.getName())) {
                return;
            }
            if (isArgumentNewForExistingDirective(directive.getName(), argument.getName())) {
                return;
            }
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(argument, editOperations, mapping);
            String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            DirectiveArgumentTypeModification directiveArgumentTypeModification = new DirectiveArgumentTypeModification(argument.getName(), oldType, newType);
            getDirectiveModification(directive.getName()).getDetails().add(directiveArgumentTypeModification);
        }

    }

    private void typeEdgeInsertedForField(EditOperation editOperation, List<EditOperation> editOperations, Mapping mapping) {
        Vertex field = editOperation.getTargetEdge().getFrom();
        Vertex objectOrInterface = newSchemaGraph.getFieldsContainerForField(field);
        if (objectOrInterface.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = objectOrInterface;
            // if the whole object is new we are done
            if (isObjectAdded(object.getName())) {
                return;
            }
            // if the field is new, we are done too
            if (isFieldNewForExistingObject(object.getName(), field.getName())) {
                return;
            }
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            // this means we have an existing object changed its type
            // and there must be a deleted edge with the old type information
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(field, editOperations, mapping);
            String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            ObjectFieldTypeModification objectFieldTypeModification = new ObjectFieldTypeModification(field.getName(), oldType, newType);
            getObjectModification(object.getName()).getDetails().add(objectFieldTypeModification);

        } else {
            assertTrue(objectOrInterface.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = objectOrInterface;
            if (isInterfaceAdded(interfaze.getName())) {
                return;
            }
            if (isFieldNewForExistingInterface(interfaze.getName(), field.getName())) {
                return;
            }
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            // this means we have an existing object changed its type
            // and there must be a deleted edge with the old type information
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(field, editOperations, mapping);
            String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            InterfaceFieldTypeModification interfaceFieldTypeModification = new InterfaceFieldTypeModification(field.getName(), oldType, newType);
            getInterfaceModification(interfaze.getName()).getDetails().add(interfaceFieldTypeModification);

        }
    }


    private EditOperation findDeletedEdge(Vertex targetVertexFrom, List<EditOperation> editOperations, Mapping mapping) {
        Vertex sourceVertexFrom = mapping.getSource(targetVertexFrom);
        for (EditOperation editOperation : editOperations) {
            if (editOperation.getOperation() == EditOperation.Operation.DELETE_EDGE) {
                Edge deletedEdge = editOperation.getSourceEdge();
                if (deletedEdge.getFrom() == sourceVertexFrom) {
                    return editOperation;
                }
            }
        }
        return Assert.assertShouldNeverHappen();
    }


    private void typeEdgeChanged(EditOperation editOperation) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex from = targetEdge.getFrom();
        Vertex to = targetEdge.getTo();
        if (from.isOfType(SchemaGraph.FIELD)) {
            fieldTypeChanged(editOperation);
        } else if (from.isOfType(SchemaGraph.ARGUMENT)) {
            argumentTypeOrDefaultValueChanged(editOperation);
        }
    }

    private void argumentTypeOrDefaultValueChanged(EditOperation editOperation) {
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
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;

            String oldDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getSourceEdge());
            String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
            if (!oldDefaultValue.equals(newDefaultValue)) {
                getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentDefaultValueModification(argument.getName(), oldDefaultValue, newDefaultValue));
            }

            String oldType = getTypeFromEdgeLabel(editOperation.getSourceEdge());
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());

            if (!oldType.equals(newType)) {
                getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentTypeModification(argument.getName(), oldType, newType));

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
        } else {
            assertTrue(container.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = container;
            InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
            String fieldName = field.getName();
            String oldType = getTypeFromEdgeLabel(editOperation.getSourceEdge());
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            interfaceModification.getDetails().add(new InterfaceFieldTypeModification(fieldName, oldType, newType));

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


    private void interfaceImplementationDeleted(Edge deletedEdge) {
        Vertex from = deletedEdge.getFrom();
        if (from.isOfType(SchemaGraph.OBJECT)) {
            if (isObjectDeleted(from.getName())) {
                return;
            }
            Vertex objectVertex = deletedEdge.getFrom();
            Vertex interfaceVertex = deletedEdge.getTo();
            ObjectInterfaceImplementationDeletion deletion = new ObjectInterfaceImplementationDeletion(interfaceVertex.getName());
            getObjectModification(objectVertex.getName()).getDetails().add(deletion);

        } else {
            assertTrue(from.isOfType(SchemaGraph.INTERFACE));
            if (isInterfaceDeleted(from.getName())) {
                return;
            }
            Vertex interfaceFromVertex = deletedEdge.getFrom();
            Vertex interfaceVertex = deletedEdge.getTo();
            InterfaceInterfaceImplementationDeletion deletion = new InterfaceInterfaceImplementationDeletion(interfaceVertex.getName());
            getInterfaceModification(interfaceFromVertex.getName()).getDetails().add(deletion);
        }

    }

    private void newInterfaceAddedToInterfaceOrObject(Edge newEdge) {
        Vertex from = newEdge.getFrom();
        if (from.isOfType(SchemaGraph.OBJECT)) {
            if (isObjectAdded(from.getName())) {
                return;
            }
            Vertex objectVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            ObjectInterfaceImplementationAddition objectInterfaceImplementationAddition = new ObjectInterfaceImplementationAddition(interfaceVertex.getName());
            getObjectModification(objectVertex.getName()).getDetails().add(objectInterfaceImplementationAddition);

        } else {
            assertTrue(from.isOfType(SchemaGraph.INTERFACE));
            if (isInterfaceAdded(from.getName())) {
                return;
            }
            Vertex interfaceFromVertex = newEdge.getFrom();
            Vertex interfaceVertex = newEdge.getTo();
            InterfaceInterfaceImplementationAddition addition = new InterfaceInterfaceImplementationAddition(interfaceVertex.getName());
            getInterfaceModification(interfaceFromVertex.getName()).getDetails().add(addition);
        }

    }

    private boolean isDirectiveAdded(String name) {
        return directiveDifferences.containsKey(name) && directiveDifferences.get(name) instanceof DirectiveAddition;
    }

    private boolean isObjectAdded(String name) {
        return objectDifferences.containsKey(name) && objectDifferences.get(name) instanceof ObjectAddition;
    }

    private boolean isUnionAdded(String name) {
        return unionDifferences.containsKey(name) && unionDifferences.get(name) instanceof UnionAddition;
    }

    private boolean isUnionDeleted(String name) {
        return unionDifferences.containsKey(name) && unionDifferences.get(name) instanceof UnionDeletion;
    }

    private boolean isEnumDeleted(String name) {
        return enumDifferences.containsKey(name) && enumDifferences.get(name) instanceof EnumDeletion;
    }

    private boolean isEnumAdded(String name) {
        return enumDifferences.containsKey(name) && enumDifferences.get(name) instanceof EnumAddition;
    }

    private boolean isArgumentNewForExistingDirective(String directiveName, String argumentName) {
        if (!directiveDifferences.containsKey(directiveName)) {
            return false;
        }
        if (!(directiveDifferences.get(directiveName) instanceof DirectiveModification)) {
            return false;
        }
        DirectiveModification directiveModification = (DirectiveModification) directiveDifferences.get(directiveName);
        List<DirectiveArgumentAddition> newArgs = directiveModification.getDetails(DirectiveArgumentAddition.class);
        return newArgs.stream().anyMatch(detail -> detail.getName().equals(argumentName));
    }

    private boolean isFieldNewForExistingObject(String objectName, String fieldName) {
        if (!objectDifferences.containsKey(objectName)) {
            return false;
        }
        if (!(objectDifferences.get(objectName) instanceof ObjectModification)) {
            return false;
        }
        ObjectModification objectModification = (ObjectModification) objectDifferences.get(objectName);
        List<ObjectFieldAddition> newFields = objectModification.getDetails(ObjectFieldAddition.class);
        return newFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
    }

    private boolean isFieldNewForExistingInterface(String interfaceName, String fieldName) {
        if (!interfaceDifferences.containsKey(interfaceName)) {
            return false;
        }
        if (!(interfaceDifferences.get(interfaceName) instanceof InterfaceModification)) {
            return false;
        }
        InterfaceModification interfaceModification = (InterfaceModification) interfaceDifferences.get(interfaceName);
        List<InterfaceFieldAddition> newFields = interfaceModification.getDetails(InterfaceFieldAddition.class);
        return newFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
    }

    private boolean isObjectDeleted(String name) {
        return objectDifferences.containsKey(name) && objectDifferences.get(name) instanceof ObjectDeletion;
    }

    private boolean isInterfaceDeleted(String name) {
        return interfaceDifferences.containsKey(name) && interfaceDifferences.get(name) instanceof InterfaceDeletion;
    }

    private boolean isInterfaceAdded(String name) {
        return interfaceDifferences.containsKey(name) && interfaceDifferences.get(name) instanceof InterfaceAddition;
    }

    private ObjectModification getObjectModification(String newName) {
        if (!objectDifferences.containsKey(newName)) {
            objectDifferences.put(newName, new ObjectModification(newName));
        }
        assertTrue(objectDifferences.get(newName) instanceof ObjectModification);
        return (ObjectModification) objectDifferences.get(newName);
    }

    private UnionModification getUnionModification(String newName) {
        if (!unionDifferences.containsKey(newName)) {
            unionDifferences.put(newName, new UnionModification(newName));
        }
        assertTrue(unionDifferences.get(newName) instanceof UnionModification);
        return (UnionModification) unionDifferences.get(newName);
    }

    private EnumModification getEnumModification(String newName) {
        if (!enumDifferences.containsKey(newName)) {
            enumDifferences.put(newName, new EnumModification(newName));
        }
        assertTrue(enumDifferences.get(newName) instanceof EnumModification);
        return (EnumModification) enumDifferences.get(newName);
    }

    private DirectiveModification getDirectiveModification(String newName) {
        if (!directiveDifferences.containsKey(newName)) {
            directiveDifferences.put(newName, new DirectiveModification(newName));
        }
        assertTrue(directiveDifferences.get(newName) instanceof DirectiveModification);
        return (DirectiveModification) directiveDifferences.get(newName);
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

    private void addedDirective(EditOperation editOperation) {
        String directiveName = editOperation.getTargetVertex().getName();

        DirectiveAddition addition = new DirectiveAddition(directiveName);
        directiveDifferences.put(directiveName, addition);
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

    private void deletedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getSourceVertex().getName();

        ScalarDeletion change = new ScalarDeletion(scalarName);
        scalarDifferences.put(scalarName, change);
    }

    private void deletedDirective(EditOperation editOperation) {
        String directiveName = editOperation.getSourceVertex().getName();

        DirectiveDeletion change = new DirectiveDeletion(directiveName);
        directiveDifferences.put(directiveName, change);
    }

    private void argumentDeleted(EditOperation editOperation) {
        Vertex deletedArgument = editOperation.getSourceVertex();
        Vertex fieldOrDirective = oldSchemaGraph.getFieldOrDirectiveForArgument(deletedArgument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = oldSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentDeletion(field.getName(), deletedArgument.getName()));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                getInterfaceModification(interfaze.getName()).getDetails().add(new InterfaceFieldArgumentDeletion(field.getName(), deletedArgument.getName()));
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentDeletion(deletedArgument.getName()));
        }

    }

    private void argumentAdded(EditOperation editOperation) {
        Vertex addedArgument = editOperation.getTargetVertex();
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(addedArgument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentAddition(field.getName(), addedArgument.getName()));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                getInterfaceModification(interfaze.getName()).getDetails().add(new InterfaceFieldArgumentAddition(field.getName(), addedArgument.getName()));
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentAddition(addedArgument.getName()));

        }

    }

    private void changedEnum(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        EnumModification modification = new EnumModification(oldName, newName);
        enumDifferences.put(oldName, modification);
        enumDifferences.put(newName, modification);
    }

    private void changedScalar(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        ScalarModification modification = new ScalarModification(oldName, newName);
        scalarDifferences.put(oldName, modification);
        scalarDifferences.put(newName, modification);
    }

    private void changedInputObject(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        InputObjectModification modification = new InputObjectModification(oldName, newName);
        inputObjectDifferences.put(oldName, modification);
        inputObjectDifferences.put(newName, modification);
    }

    private void changedDirective(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        DirectiveModification modification = new DirectiveModification(oldName, newName);
        directiveDifferences.put(oldName, modification);
        directiveDifferences.put(newName, modification);
    }

    private void changedObject(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        ObjectModification objectModification = new ObjectModification(oldName, newName);
        objectDifferences.put(oldName, objectModification);
        objectDifferences.put(newName, objectModification);
    }

    private void changedInterface(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        InterfaceModification interfaceModification = new InterfaceModification(oldName, newName);
        interfaceDifferences.put(oldName, interfaceModification);
        interfaceDifferences.put(newName, interfaceModification);
    }

    private void changedUnion(EditOperation editOperation) {
        String newUnionName = editOperation.getTargetVertex().getName();
        String oldUnionName = editOperation.getSourceVertex().getName();

        UnionModification objectModification = new UnionModification(oldUnionName, newUnionName);
        unionDifferences.put(oldUnionName, objectModification);
        unionDifferences.put(newUnionName, objectModification);
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
