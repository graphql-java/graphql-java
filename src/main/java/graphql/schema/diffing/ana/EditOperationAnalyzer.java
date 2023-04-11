package graphql.schema.diffing.ana;

import graphql.Assert;
import graphql.Internal;
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
import java.util.function.Predicate;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveAddition;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentRename;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentValueModification;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDirectiveArgumentLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumValueLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectFieldLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldArgumentLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldArgumentLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveScalarLocation;
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveUnionLocation;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveAddition;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentAddition;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentDefaultValueModification;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentRename;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveDifference;
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveModification;
import static graphql.schema.diffing.ana.SchemaDifference.EnumAddition;
import static graphql.schema.diffing.ana.SchemaDifference.EnumDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.EnumDifference;
import static graphql.schema.diffing.ana.SchemaDifference.EnumModification;
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueAddition;
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueRenamed;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectDifference;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldDefaultValueModification;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldRename;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectModification;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceDifference;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentDefaultValueModification;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentRename;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldRename;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceInterfaceImplementationAddition;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceInterfaceImplementationDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceModification;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectAddition;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectDifference;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldAddition;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentAddition;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentDefaultValueModification;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentRename;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldRename;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldTypeModification;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectInterfaceImplementationAddition;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectInterfaceImplementationDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ObjectModification;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarAddition;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarDifference;
import static graphql.schema.diffing.ana.SchemaDifference.ScalarModification;
import static graphql.schema.diffing.ana.SchemaDifference.UnionAddition;
import static graphql.schema.diffing.ana.SchemaDifference.UnionDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.UnionDifference;
import static graphql.schema.diffing.ana.SchemaDifference.UnionMemberAddition;
import static graphql.schema.diffing.ana.SchemaDifference.UnionMemberDeletion;
import static graphql.schema.diffing.ana.SchemaDifference.UnionModification;

/**
 * Higher level GraphQL semantic assigned to
 */
@Internal
public class EditOperationAnalyzer {

    private final GraphQLSchema oldSchema;
    private final GraphQLSchema newSchema;
    private final SchemaGraph oldSchemaGraph;
    private final SchemaGraph newSchemaGraph;

    private final Map<String, ObjectDifference> objectDifferences = new LinkedHashMap<>();
    private final Map<String, InterfaceDifference> interfaceDifferences = new LinkedHashMap<>();
    private final Map<String, UnionDifference> unionDifferences = new LinkedHashMap<>();
    private final Map<String, EnumDifference> enumDifferences = new LinkedHashMap<>();
    private final Map<String, InputObjectDifference> inputObjectDifferences = new LinkedHashMap<>();
    private final Map<String, ScalarDifference> scalarDifferences = new LinkedHashMap<>();

    private final Map<String, DirectiveDifference> directiveDifferences = new LinkedHashMap<>();

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
                    } else if (editOperation.getTargetVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        handleArgumentChange(editOperation, mapping);
                    } else if (editOperation.getTargetVertex().isOfType(SchemaGraph.INPUT_FIELD)) {
                        handleInputFieldChange(editOperation);
                    }
                    break;
                case INSERT_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldAdded(editOperation);
                    } else if (editOperation.getTargetVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentAdded(editOperation);
                    } else if (editOperation.getTargetVertex().isOfType(SchemaGraph.INPUT_FIELD)) {
                        inputFieldAdded(editOperation);
                    }
                    break;
                case DELETE_VERTEX:
                    if (editOperation.getSourceVertex().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentDeleted(editOperation);
                    } else if (editOperation.getSourceVertex().isOfType(SchemaGraph.FIELD)) {
                        fieldDeleted(editOperation);
                    } else if (editOperation.getSourceVertex().isOfType(SchemaGraph.INPUT_FIELD)) {
                        inputFieldDeleted(editOperation);
                    }
            }
        }
        handleTypeChanges(editOperations, mapping);
        handleImplementsChanges(editOperations, mapping);
        handleUnionMemberChanges(editOperations, mapping);
        handleEnumValuesChanges(editOperations, mapping);
        handleAppliedDirectives(editOperations, mapping);
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

    private void handleArgumentChanges(List<EditOperation> editOperations, Mapping mapping) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_EDGE:
                    if (editOperation.getTargetEdge().getTo().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentAdded(editOperation);
                    }
                    break;
                case DELETE_EDGE:
                    if (editOperation.getSourceEdge().getTo().isOfType(SchemaGraph.ARGUMENT)) {
                        argumentDeleted(editOperation);
                    }
                    break;
            }
        }
    }


    private void handleAppliedDirectives(List<EditOperation> editOperations, Mapping mapping) {

        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.APPLIED_DIRECTIVE)) {
                        appliedDirectiveAdded(editOperation);
                    }
                    break;
                case CHANGE_VERTEX:
                    if (editOperation.getTargetVertex().isOfType(SchemaGraph.APPLIED_ARGUMENT)) {
                        appliedDirectiveArgumentChanged(editOperation);
                    }
                    break;
                case DELETE_VERTEX:
                    if (editOperation.getSourceVertex().isOfType(SchemaGraph.APPLIED_DIRECTIVE)) {
                        appliedDirectiveDeleted(editOperation);
                    } else if (editOperation.getSourceVertex().isOfType(SchemaGraph.APPLIED_ARGUMENT)) {
                        appliedDirectiveArgumentDeleted(editOperation);
                    }
                    break;

            }
        }

    }

    private void appliedDirectiveDeleted(EditOperation editOperation) {
        Vertex appliedDirective = editOperation.getSourceVertex();
        Vertex container = oldSchemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
        if (container.isOfType(SchemaGraph.FIELD)) {
            appliedDirectiveDeletedFromField(appliedDirective, container);
        } else if (container.isOfType(SchemaGraph.ARGUMENT)) {
            appliedDirectiveDeletedFromArgument(appliedDirective, container);
        } else if (container.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = container;
            if (isObjectDeleted(object.getName())) {
                return;
            }
            AppliedDirectiveObjectLocation location = new AppliedDirectiveObjectLocation(object.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getObjectModification(object.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.INTERFACE)) {
            Vertex interfaze = container;
            if (isInterfaceDeleted(interfaze.getName())) {
                return;
            }
            AppliedDirectiveInterfaceLocation location = new AppliedDirectiveInterfaceLocation(interfaze.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getInterfaceModification(interfaze.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.SCALAR)) {
            Vertex scalar = container;
            if (isScalarDeleted(scalar.getName())) {
                return;
            }
            AppliedDirectiveScalarLocation location = new AppliedDirectiveScalarLocation(scalar.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getScalarModification(scalar.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.ENUM)) {
            Vertex enumVertex = container;
            if (isEnumDeleted(enumVertex.getName())) {
                return;
            }
            AppliedDirectiveEnumLocation location = new AppliedDirectiveEnumLocation(enumVertex.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getEnumModification(enumVertex.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.ENUM_VALUE)) {
            Vertex enumValue = container;
            Vertex enumVertex = oldSchemaGraph.getEnumForEnumValue(enumValue);
            if (isEnumDeleted(enumVertex.getName())) {
                return;
            }
            if (isEnumValueDeletedFromExistingEnum(enumVertex.getName(), enumValue.getName())) {
                return;
            }
            AppliedDirectiveEnumValueLocation location = new AppliedDirectiveEnumValueLocation(enumVertex.getName(), enumValue.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getEnumModification(enumVertex.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.INPUT_OBJECT)) {
            Vertex inputObject = container;
            if (isInputObjectDeleted(inputObject.getName())) {
                return;
            }
            AppliedDirectiveInputObjectLocation location = new AppliedDirectiveInputObjectLocation(inputObject.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getInputObjectModification(inputObject.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.INPUT_FIELD)) {
            Vertex inputField = container;
            Vertex inputObject = oldSchemaGraph.getInputObjectForInputField(inputField);
            if (isInputObjectDeleted(inputObject.getName())) {
                return;
            }
            if (isInputFieldDeletedFromExistingInputObject(inputObject.getName(), inputField.getName())) {
                return;
            }
            AppliedDirectiveInputObjectFieldLocation location = new AppliedDirectiveInputObjectFieldLocation(inputObject.getName(), inputField.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getInputObjectModification(inputObject.getName()).getDetails().add(appliedDirectiveDeletion);
        } else if (container.isOfType(SchemaGraph.UNION)) {
            Vertex union = container;
            if (isUnionDeleted(union.getName())) {
                return;
            }
            AppliedDirectiveUnionLocation location = new AppliedDirectiveUnionLocation(union.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getUnionModification(union.getName()).getDetails().add(appliedDirectiveDeletion);
        }
    }

    private void appliedDirectiveArgumentDeleted(EditOperation editOperation) {
        Vertex deletedArgument = editOperation.getSourceVertex();
        Vertex appliedDirective = oldSchemaGraph.getAppliedDirectiveForAppliedArgument(deletedArgument);
        Vertex container = oldSchemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);

        if (container.isOfType(SchemaGraph.FIELD)) {
            Vertex field = container;
            Vertex interfaceOrObjective = oldSchemaGraph.getFieldsContainerForField(field);
            if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = interfaceOrObjective;
                if (isObjectDeleted(object.getName())) {
                    return;
                }
                AppliedDirectiveObjectFieldLocation location = new AppliedDirectiveObjectFieldLocation(object.getName(), field.getName());
                getObjectModification(object.getName()).getDetails().add(new AppliedDirectiveArgumentDeletion(location, deletedArgument.getName()));
            } else {
                assertTrue(interfaceOrObjective.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = interfaceOrObjective;
                if (isInterfaceDeleted(interfaze.getName())) {
                    return;
                }
                AppliedDirectiveInterfaceFieldLocation location = new AppliedDirectiveInterfaceFieldLocation(interfaze.getName(), field.getName());
                getInterfaceModification(interfaze.getName()).getDetails().add(new AppliedDirectiveArgumentDeletion(location, deletedArgument.getName()));
            }
        }
    }

    private void appliedDirectiveArgumentChanged(EditOperation editOperation) {
        Vertex appliedArgument = editOperation.getTargetVertex();
        String oldArgumentName = editOperation.getSourceVertex().getName();
        String newArgumentName = editOperation.getTargetVertex().getName();
        boolean nameChanged = !oldArgumentName.equals(newArgumentName);

        String oldValue = editOperation.getSourceVertex().get("value");
        String newValue = editOperation.getTargetVertex().get("value");
        boolean valueChanged = !oldValue.equals(newValue);


        Vertex appliedDirective = newSchemaGraph.getAppliedDirectiveForAppliedArgument(appliedArgument);
        Vertex container = newSchemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
        if (container.isOfType(SchemaGraph.FIELD)) {
            Vertex field = container;
            Vertex interfaceOrObjective = newSchemaGraph.getFieldsContainerForField(field);
            if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = interfaceOrObjective;
                AppliedDirectiveObjectFieldLocation location = new AppliedDirectiveObjectFieldLocation(object.getName(), field.getName());
                if (valueChanged) {
                    AppliedDirectiveArgumentValueModification argumentValueModification = new AppliedDirectiveArgumentValueModification(location, newArgumentName, oldValue, newValue);
                    getObjectModification(object.getName()).getDetails().add(argumentValueModification);
                }
                if (nameChanged) {
                    AppliedDirectiveArgumentRename argumentRename = new AppliedDirectiveArgumentRename(location, oldArgumentName, newArgumentName);
                    getObjectModification(object.getName()).getDetails().add(argumentRename);
                }
            }
        }
    }

    private void appliedDirectiveAdded(EditOperation editOperation) {
        Vertex appliedDirective = editOperation.getTargetVertex();
        Vertex container = newSchemaGraph.getAppliedDirectiveContainerForAppliedDirective(appliedDirective);
        if (container.isOfType(SchemaGraph.FIELD)) {
            appliedDirectiveAddedToField(appliedDirective, container);
        } else if (container.isOfType(SchemaGraph.ARGUMENT)) {
            appliedDirectiveAddedToArgument(appliedDirective, container);

        } else if (container.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = container;
            if (isObjectAdded(object.getName())) {
                return;
            }
            AppliedDirectiveObjectLocation location = new AppliedDirectiveObjectLocation(object.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getObjectModification(object.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.INTERFACE)) {
            Vertex interfaze = container;
            if (isInterfaceAdded(interfaze.getName())) {
                return;
            }
            AppliedDirectiveInterfaceLocation location = new AppliedDirectiveInterfaceLocation(interfaze.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getInterfaceModification(interfaze.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.SCALAR)) {
            Vertex scalar = container;
            if (isScalarAdded(scalar.getName())) {
                return;
            }
            AppliedDirectiveScalarLocation location = new AppliedDirectiveScalarLocation(scalar.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getScalarModification(scalar.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.ENUM)) {
            Vertex enumVertex = container;
            if (isEnumAdded(enumVertex.getName())) {
                return;
            }
            AppliedDirectiveEnumLocation location = new AppliedDirectiveEnumLocation(enumVertex.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getEnumModification(enumVertex.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.ENUM_VALUE)) {
            Vertex enumValue = container;
            Vertex enumVertex = newSchemaGraph.getEnumForEnumValue(enumValue);
            if (isEnumAdded(enumVertex.getName())) {
                return;
            }
            if (isNewEnumValueForExistingEnum(enumVertex.getName(), enumValue.getName())) {
                return;
            }
            AppliedDirectiveEnumValueLocation location = new AppliedDirectiveEnumValueLocation(enumVertex.getName(), enumValue.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getEnumModification(enumVertex.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.INPUT_OBJECT)) {
            Vertex inputObject = container;
            if (isInputObjectAdded(inputObject.getName())) {
                return;
            }
            AppliedDirectiveInputObjectLocation location = new AppliedDirectiveInputObjectLocation(inputObject.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getInputObjectModification(inputObject.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.INPUT_FIELD)) {
            Vertex inputField = container;
            Vertex inputObject = newSchemaGraph.getInputObjectForInputField(inputField);
            if (isInputObjectAdded(inputObject.getName())) {
                return;
            }
            if (isNewInputFieldExistingInputObject(inputObject.getName(), inputField.getName())) {
                return;
            }
            AppliedDirectiveInputObjectFieldLocation location = new AppliedDirectiveInputObjectFieldLocation(inputObject.getName(), inputField.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getInputObjectModification(inputObject.getName()).getDetails().add(appliedDirectiveAddition);
        } else if (container.isOfType(SchemaGraph.UNION)) {
            Vertex union = container;
            if (isUnionAdded(union.getName())) {
                return;
            }
            AppliedDirectiveUnionLocation location = new AppliedDirectiveUnionLocation(union.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getUnionModification(union.getName()).getDetails().add(appliedDirectiveAddition);
        }
    }

    private void appliedDirectiveDeletedFromField(Vertex appliedDirective, Vertex container) {
        Vertex field = container;
        Vertex interfaceOrObjective = oldSchemaGraph.getFieldsContainerForField(field);
        if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = interfaceOrObjective;

            if (isObjectDeleted(object.getName())) {
                return;
            }
            if (isFieldDeletedFromExistingObject(object.getName(), field.getName())) {
                return;
            }
            AppliedDirectiveObjectFieldLocation location = new AppliedDirectiveObjectFieldLocation(object.getName(), field.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getObjectModification(object.getName()).getDetails().add(appliedDirectiveDeletion);
        }
    }

    private void appliedDirectiveAddedToField(Vertex appliedDirective, Vertex container) {
        Vertex field = container;
        Vertex interfaceOrObjective = newSchemaGraph.getFieldsContainerForField(field);
        if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = interfaceOrObjective;

            if (isObjectAdded(object.getName())) {
                return;
            }
            if (isFieldNewForExistingObject(object.getName(), field.getName())) {
                return;
            }
            AppliedDirectiveObjectFieldLocation location = new AppliedDirectiveObjectFieldLocation(object.getName(), field.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getObjectModification(object.getName()).getDetails().add(appliedDirectiveAddition);
        }
    }

    private void appliedDirectiveDeletedFromArgument(Vertex appliedDirective, Vertex container) {
        Vertex argument = container;
        Vertex fieldOrDirective = oldSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex interfaceOrObjective = oldSchemaGraph.getFieldsContainerForField(field);
            if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = interfaceOrObjective;
                if (isObjectDeleted(object.getName())) {
                    return;
                }
                if (isFieldDeletedFromExistingObject(object.getName(), field.getName())) {
                    return;
                }
                if (isArgumentDeletedFromExistingObjectField(object.getName(), field.getName(), argument.getName())) {
                    return;
                }
                AppliedDirectiveObjectFieldArgumentLocation location = new AppliedDirectiveObjectFieldArgumentLocation(object.getName(), field.getName(), argument.getName());
                AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
                getObjectModification(object.getName()).getDetails().add(appliedDirectiveDeletion);
            } else {
                assertTrue(interfaceOrObjective.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = interfaceOrObjective;
                if (isInterfaceDeleted(interfaze.getName())) {
                    return;
                }
                if (isFieldDeletedFromExistingInterface(interfaze.getName(), field.getName())) {
                    return;
                }
                if (isArgumentDeletedFromExistingInterfaceField(interfaze.getName(), field.getName(), argument.getName())) {
                    return;
                }
                AppliedDirectiveInterfaceFieldArgumentLocation location = new AppliedDirectiveInterfaceFieldArgumentLocation(interfaze.getName(), field.getName(), argument.getName());
                AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
                getInterfaceModification(interfaze.getName()).getDetails().add(appliedDirectiveDeletion);
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            if (isDirectiveDeleted(directive.getName())) {
                return;
            }
            if (isArgumentDeletedFromExistingDirective(directive.getName(), argument.getName())) {
                return;
            }
            AppliedDirectiveDirectiveArgumentLocation location = new AppliedDirectiveDirectiveArgumentLocation(directive.getName(), argument.getName());
            AppliedDirectiveDeletion appliedDirectiveDeletion = new AppliedDirectiveDeletion(location, appliedDirective.getName());
            getDirectiveModification(directive.getName()).getDetails().add(appliedDirectiveDeletion);
        }
    }

    private void appliedDirectiveAddedToArgument(Vertex appliedDirective, Vertex container) {
        Vertex argument = container;
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex interfaceOrObjective = newSchemaGraph.getFieldsContainerForField(field);
            if (interfaceOrObjective.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = interfaceOrObjective;
                if (isObjectAdded(object.getName())) {
                    return;
                }
                if (isFieldNewForExistingObject(object.getName(), field.getName())) {
                    return;
                }
                if (isArgumentNewForExistingObjectField(object.getName(), field.getName(), argument.getName())) {
                    return;
                }
                AppliedDirectiveObjectFieldArgumentLocation location = new AppliedDirectiveObjectFieldArgumentLocation(object.getName(), field.getName(), argument.getName());
                AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
                getObjectModification(object.getName()).getDetails().add(appliedDirectiveAddition);
            } else {
                assertTrue(interfaceOrObjective.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = interfaceOrObjective;
                if (isInterfaceAdded(interfaze.getName())) {
                    return;
                }
                if (isFieldNewForExistingInterface(interfaze.getName(), field.getName())) {
                    return;
                }
                if (isArgumentNewForExistingInterfaceField(interfaze.getName(), field.getName(), argument.getName())) {
                    return;
                }
                AppliedDirectiveInterfaceFieldArgumentLocation location = new AppliedDirectiveInterfaceFieldArgumentLocation(interfaze.getName(), field.getName(), argument.getName());
                AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
                getInterfaceModification(interfaze.getName()).getDetails().add(appliedDirectiveAddition);
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            if (isDirectiveAdded(directive.getName())) {
                return;
            }
            if (isArgumentNewForExistingDirective(directive.getName(), argument.getName())) {
                return;
            }
            AppliedDirectiveDirectiveArgumentLocation location = new AppliedDirectiveDirectiveArgumentLocation(directive.getName(), argument.getName());
            AppliedDirectiveAddition appliedDirectiveAddition = new AppliedDirectiveAddition(location, appliedDirective.getName());
            getDirectiveModification(directive.getName()).getDetails().add(appliedDirectiveAddition);
        }
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
                        typeEdgeChanged(editOperation, mapping);
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
                    if (oldEdge.getFrom().isOfType(SchemaGraph.UNION) && !oldEdge.getTo().isOfType(SchemaGraph.APPLIED_DIRECTIVE)) {
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
                    if (newEdge.getFrom().isOfType(SchemaGraph.ENUM) && newEdge.getTo().isOfType(SchemaGraph.ENUM_VALUE)) {
                        handleEnumValueAdded(editOperation);
                    }
                    break;
                case DELETE_EDGE:
                    Edge oldEdge = editOperation.getSourceEdge();
                    if (oldEdge.getFrom().isOfType(SchemaGraph.ENUM) && oldEdge.getTo().isOfType(SchemaGraph.ENUM_VALUE)) {
                        handleEnumValueDeleted(editOperation);
                    }
                    break;
                case CHANGE_VERTEX:
                    if (editOperation.getSourceVertex().isOfType(SchemaGraph.ENUM_VALUE) && editOperation.getTargetVertex().isOfType(SchemaGraph.ENUM_VALUE)) {
                        handleEnumValueChanged(editOperation);
                    }
                    break;
            }
        }
    }

    private void handleInputFieldChange(EditOperation editOperation) {
        Vertex inputField = editOperation.getTargetVertex();
        Vertex inputObject = newSchemaGraph.getInputObjectForInputField(inputField);

        String oldName = editOperation.getSourceVertex().getName();
        String newName = inputField.getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        getInputObjectModification(inputObject.getName()).getDetails().add(new InputObjectFieldRename(oldName, newName));
    }

    private void handleArgumentChange(EditOperation editOperation, Mapping mapping) {
        Vertex oldArgument = editOperation.getSourceVertex();
        Vertex argument = editOperation.getTargetVertex();

        String oldName = oldArgument.getName();
        String newName = argument.getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        if (!doesArgumentChangeMakeSense(oldArgument, argument, mapping)) {
            return;
        }

        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE)) {
            Vertex directive = fieldOrDirective;
            DirectiveModification directiveModification = getDirectiveModification(directive.getName());
            directiveModification.getDetails().add(new DirectiveArgumentRename(oldName, newName));
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.FIELD));
            Vertex field = fieldOrDirective;
            String fieldName = field.getName();
            Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                ObjectModification objectModification = getObjectModification(object.getName());
                objectModification.getDetails().add(new ObjectFieldArgumentRename(fieldName, oldName, newName));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
                interfaceModification.getDetails().add(new InterfaceFieldArgumentRename(fieldName, oldName, newName));
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

    private void handleEnumValueChanged(EditOperation editOperation) {
        Vertex enumVertex = newSchemaGraph.getEnumForEnumValue(editOperation.getTargetVertex());
        EnumModification enumModification = getEnumModification(enumVertex.getName());
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();
        enumModification.getDetails().add(new EnumValueRenamed(oldName, newName));
    }

    private void fieldChanged(EditOperation editOperation) {
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);

        String oldName = editOperation.getSourceVertex().getName();
        String newName = field.getName();
        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
            Vertex object = fieldsContainerForField;
            ObjectModification objectModification = getObjectModification(object.getName());
            objectModification.getDetails().add(new ObjectFieldRename(oldName, newName));
        } else {
            assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
            Vertex interfaze = fieldsContainerForField;
            InterfaceModification interfaceModification = getInterfaceModification(interfaze.getName());
            interfaceModification.getDetails().add(new InterfaceFieldRename(oldName, newName));
        }
    }

    private void inputFieldAdded(EditOperation editOperation) {
        Vertex inputField = editOperation.getTargetVertex();
        Vertex inputObject = newSchemaGraph.getInputObjectForInputField(inputField);
        if (isInputObjectAdded(inputObject.getName())) {
            return;
        }
        InputObjectModification modification = getInputObjectModification(inputObject.getName());
        modification.getDetails().add(new InputObjectFieldAddition(inputField.getName()));
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

    private void inputFieldDeleted(EditOperation editOperation) {
        Vertex inputField = editOperation.getSourceVertex();
        Vertex inputObject = oldSchemaGraph.getInputObjectForInputField(inputField);
        if (isInputObjectDeleted(inputObject.getName())) {
            return;
        }
        getInputObjectModification(inputObject.getName()).getDetails().add(new InputObjectFieldDeletion(inputField.getName()));
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


    private void typeEdgeInserted(EditOperation editOperation, List<EditOperation> editOperations, Mapping
            mapping) {
        Edge newEdge = editOperation.getTargetEdge();
        Vertex from = newEdge.getFrom();
        if (from.isOfType(SchemaGraph.FIELD)) {
            typeEdgeInsertedForField(editOperation, editOperations, mapping);
        } else if (from.isOfType(SchemaGraph.ARGUMENT)) {
            typeEdgeInsertedForArgument(editOperation, editOperations, mapping);
        } else if (from.isOfType(SchemaGraph.INPUT_FIELD)) {
            typeEdgeInsertedForInputField(editOperation, editOperations, mapping);
        }

    }

    private void typeEdgeInsertedForInputField(EditOperation editOperation,
                                               List<EditOperation> editOperations,
                                               Mapping mapping) {
        Vertex inputField = editOperation.getTargetEdge().getFrom();
        Vertex inputObject = newSchemaGraph.getInputObjectForInputField(inputField);
        if (isInputObjectAdded(inputObject.getName())) {
            return;
        }
        if (isNewInputFieldExistingInputObject(inputObject.getName(), inputField.getName())) {
            return;
        }
        String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
        EditOperation deletedTypeEdgeOperation = findDeletedEdge(inputField, editOperations, mapping, this::isTypeEdge);
        String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
        InputObjectFieldTypeModification inputObjectFieldTypeModification = new InputObjectFieldTypeModification(inputField.getName(), oldType, newType);
        getInputObjectModification(inputObject.getName()).getDetails().add(inputObjectFieldTypeModification);
    }

    private void typeEdgeInsertedForArgument(EditOperation editOperation,
                                             List<EditOperation> editOperations,
                                             Mapping mapping) {
        Vertex argument = editOperation.getTargetEdge().getFrom();
        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
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
                // if the argument is new, we are done too
                if (isArgumentNewForExistingObjectField(object.getName(), field.getName(), argument.getName())) {
                    return;
                }

                String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
                // this means we have an existing object changed its type
                // and there must be a deleted edge with the old type information
                EditOperation deletedTypeEdgeOperation = findDeletedEdge(argument, editOperations, mapping, this::isTypeEdge);
                String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
                ObjectFieldArgumentTypeModification objectFieldArgumentTypeModification = new ObjectFieldArgumentTypeModification(field.getName(), argument.getName(), oldType, newType);
                getObjectModification(object.getName()).getDetails().add(objectFieldArgumentTypeModification);

                String oldDefaultValue = getDefaultValueFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
                String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
                if (!oldDefaultValue.equals(newDefaultValue)) {
                    getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentDefaultValueModification(field.getName(), argument.getName(), oldDefaultValue, newDefaultValue));
                }
            } else {
                assertTrue(objectOrInterface.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = objectOrInterface;

                // if the whole object is new we are done
                if (isInterfaceAdded(interfaze.getName())) {
                    return;
                }
                // if the field is new, we are done too
                if (isFieldNewForExistingInterface(interfaze.getName(), field.getName())) {
                    return;
                }
                // if the argument is new, we are done too
                if (isArgumentNewForExistingInterfaceField(interfaze.getName(), field.getName(), argument.getName())) {
                    return;
                }

                String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
                // this means we have an existing object changed its type
                // and there must be a deleted edge with the old type information
                EditOperation deletedTypeEdgeOperation = findDeletedEdge(argument, editOperations, mapping, this::isTypeEdge);
                String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
                InterfaceFieldArgumentTypeModification interfaceFieldArgumentTypeModification = new InterfaceFieldArgumentTypeModification(field.getName(), argument.getName(), oldType, newType);
                getInterfaceModification(interfaze.getName()).getDetails().add(interfaceFieldArgumentTypeModification);

                String oldDefaultValue = getDefaultValueFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
                String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
                if (!oldDefaultValue.equals(newDefaultValue)) {
                    getInterfaceModification(interfaze.getName()).getDetails().add(new InterfaceFieldArgumentDefaultValueModification(field.getName(), argument.getName(), oldDefaultValue, newDefaultValue));
                }
            }
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
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(argument, editOperations, mapping, this::isTypeEdge);
            String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            DirectiveArgumentTypeModification directiveArgumentTypeModification = new DirectiveArgumentTypeModification(argument.getName(), oldType, newType);
            getDirectiveModification(directive.getName()).getDetails().add(directiveArgumentTypeModification);

            String oldDefaultValue = getDefaultValueFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
            if (!oldDefaultValue.equals(newDefaultValue)) {
                getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentDefaultValueModification(argument.getName(), oldDefaultValue, newDefaultValue));
            }
        }
    }

    private void typeEdgeInsertedForField(EditOperation editOperation,
                                          List<EditOperation> editOperations,
                                          Mapping mapping) {
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
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(field, editOperations, mapping, this::isTypeEdge);
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
            EditOperation deletedTypeEdgeOperation = findDeletedEdge(field, editOperations, mapping, this::isTypeEdge);
            String oldType = getTypeFromEdgeLabel(deletedTypeEdgeOperation.getSourceEdge());
            InterfaceFieldTypeModification interfaceFieldTypeModification = new InterfaceFieldTypeModification(field.getName(), oldType, newType);
            getInterfaceModification(interfaze.getName()).getDetails().add(interfaceFieldTypeModification);
        }
    }


    private EditOperation findDeletedEdge(Vertex targetVertexFrom,
                                          List<EditOperation> editOperations,
                                          Mapping mapping,
                                          Predicate<Edge> edgePredicate) {
        Vertex sourceVertexFrom = mapping.getSource(targetVertexFrom);
        for (EditOperation editOperation : editOperations) {
            if (editOperation.getOperation() == EditOperation.Operation.DELETE_EDGE) {
                Edge deletedEdge = editOperation.getSourceEdge();
                if (deletedEdge.getFrom() == sourceVertexFrom && edgePredicate.test(deletedEdge)) {
                    return editOperation;
                }
            }
        }
        return Assert.assertShouldNeverHappen();
    }


    private void typeEdgeChanged(EditOperation editOperation, Mapping mapping) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex from = targetEdge.getFrom();
        if (from.isOfType(SchemaGraph.FIELD)) {
            outputFieldTypeChanged(editOperation);
        } else if (from.isOfType(SchemaGraph.ARGUMENT)) {
            argumentTypeOrDefaultValueChanged(editOperation, mapping);
        } else if (from.isOfType(SchemaGraph.INPUT_FIELD)) {
            inputFieldTypeOrDefaultValueChanged(editOperation);
        }
    }

    private void inputFieldTypeOrDefaultValueChanged(EditOperation editOperation) {
        Edge targetEdge = editOperation.getTargetEdge();
        Vertex inputField = targetEdge.getFrom();
        Vertex inputObject = newSchemaGraph.getInputObjectForInputField(inputField);
        String oldDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getSourceEdge());
        String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
        if (!oldDefaultValue.equals(newDefaultValue)) {
            InputObjectFieldDefaultValueModification modification = new InputObjectFieldDefaultValueModification(inputField.getName(), oldDefaultValue, newDefaultValue);
            getInputObjectModification(inputObject.getName()).getDetails().add(modification);
        }
        String oldType = getTypeFromEdgeLabel(editOperation.getSourceEdge());
        String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
        if (!oldType.equals(newType)) {
            InputObjectFieldTypeModification inputObjectFieldTypeModification = new InputObjectFieldTypeModification(inputField.getName(), oldType, newType);
            getInputObjectModification(inputObject.getName()).getDetails().add(inputObjectFieldTypeModification);
        }
    }

    private void argumentTypeOrDefaultValueChanged(EditOperation editOperation, Mapping mapping) {
        Vertex oldArgument = editOperation.getSourceEdge().getFrom();
        Vertex argument = editOperation.getTargetEdge().getFrom();

        if (!doesArgumentChangeMakeSense(oldArgument, argument, mapping)) {
            return;
        }

        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(argument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex objectOrInterface = newSchemaGraph.getFieldsContainerForField(field);

            String oldDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getSourceEdge());
            String newDefaultValue = getDefaultValueFromEdgeLabel(editOperation.getTargetEdge());
            if (!oldDefaultValue.equals(newDefaultValue)) {
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

            String oldType = getTypeFromEdgeLabel(editOperation.getSourceEdge());
            String newType = getTypeFromEdgeLabel(editOperation.getTargetEdge());
            if (!oldType.equals(newType)) {
                if (objectOrInterface.isOfType(SchemaGraph.OBJECT)) {
                    ObjectFieldArgumentTypeModification objectFieldArgumentTypeModification = new ObjectFieldArgumentTypeModification(field.getName(), argument.getName(), oldType, newType);
                    getObjectModification(objectOrInterface.getName()).getDetails().add(objectFieldArgumentTypeModification);
                } else {
                    assertTrue(objectOrInterface.isOfType(SchemaGraph.INTERFACE));
                    InterfaceFieldArgumentTypeModification interfaceFieldArgumentTypeModification = new InterfaceFieldArgumentTypeModification(field.getName(), argument.getName(), oldType, newType);
                    getInterfaceModification(objectOrInterface.getName()).getDetails().add(interfaceFieldArgumentTypeModification);
                }
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

    /**
     * Sometimes the diffing algorithm will give us an argument change when the argument container
     * changed i.e. the argument was "moved" around because the deleted and newly added arguments
     * look similar.
     * <p>
     * We only want to report argument type changes if it makes sense i.e. if the argument container was the same.
     */
    private boolean doesArgumentChangeMakeSense(Vertex oldArgument, Vertex newArgument, Mapping mapping) {
        // Container for an argument in this case should be a field or directive
        Vertex oldContainer = oldSchemaGraph.getFieldOrDirectiveForArgument(oldArgument);
        Vertex newContainer = newSchemaGraph.getFieldOrDirectiveForArgument(newArgument);

        // Make sure the container is the same
        return mapping.getTarget(oldContainer) == newContainer;
    }

    private void outputFieldTypeChanged(EditOperation editOperation) {
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

    private boolean isTypeEdge(Edge edge) {
        String label = edge.getLabel();
        return label.startsWith("type=");
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

    private boolean isDirectiveDeleted(String name) {
        return directiveDifferences.containsKey(name) && directiveDifferences.get(name) instanceof DirectiveDeletion;
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

    private boolean isInputObjectAdded(String name) {
        return inputObjectDifferences.containsKey(name) && inputObjectDifferences.get(name) instanceof InputObjectAddition;
    }

    private boolean isInputObjectDeleted(String name) {
        return inputObjectDifferences.containsKey(name) && inputObjectDifferences.get(name) instanceof InputObjectDeletion;
    }

    private boolean isInputFieldAdded(String name) {
        return inputObjectDifferences.containsKey(name) && inputObjectDifferences.get(name) instanceof InputObjectAddition;
    }

    private boolean isNewInputFieldExistingInputObject(String inputObjectName, String fieldName) {
        if (!inputObjectDifferences.containsKey(inputObjectName)) {
            return false;
        }
        if (!(inputObjectDifferences.get(inputObjectName) instanceof InputObjectModification)) {
            return false;
        }
        InputObjectModification modification = (InputObjectModification) inputObjectDifferences.get(inputObjectName);
        List<InputObjectFieldAddition> newFields = modification.getDetails(InputObjectFieldAddition.class);
        return newFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
    }

    private boolean isInputFieldDeletedFromExistingInputObject(String inputObjectName, String fieldName) {
        if (!inputObjectDifferences.containsKey(inputObjectName)) {
            return false;
        }
        if (!(inputObjectDifferences.get(inputObjectName) instanceof InputObjectModification)) {
            return false;
        }
        InputObjectModification modification = (InputObjectModification) inputObjectDifferences.get(inputObjectName);
        List<InputObjectFieldDeletion> deletedFields = modification.getDetails(InputObjectFieldDeletion.class);
        return deletedFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
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

    private boolean isArgumentDeletedFromExistingDirective(String directiveName, String argumentName) {
        if (!directiveDifferences.containsKey(directiveName)) {
            return false;
        }
        if (!(directiveDifferences.get(directiveName) instanceof DirectiveModification)) {
            return false;
        }
        DirectiveModification directiveModification = (DirectiveModification) directiveDifferences.get(directiveName);
        List<DirectiveArgumentDeletion> deletedArgs = directiveModification.getDetails(DirectiveArgumentDeletion.class);
        return deletedArgs.stream().anyMatch(detail -> detail.getName().equals(argumentName));
    }

    private boolean isArgumentNewForExistingObjectField(String objectName, String fieldName, String argumentName) {
        if (!objectDifferences.containsKey(objectName)) {
            return false;
        }
        if (!(objectDifferences.get(objectName) instanceof ObjectModification)) {
            return false;
        }
        // finding out if the field was just added
        ObjectModification objectModification = (ObjectModification) objectDifferences.get(objectName);
        List<ObjectFieldAddition> newFields = objectModification.getDetails(ObjectFieldAddition.class);
        boolean newField = newFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
        if (newField) {
            return false;
        }
        // now finding out if the argument is new
        List<ObjectFieldArgumentAddition> newArgs = objectModification.getDetails(ObjectFieldArgumentAddition.class);
        return newArgs.stream().anyMatch(detail -> detail.getFieldName().equals(fieldName) && detail.getName().equals(argumentName));
    }

    private boolean isArgumentDeletedFromExistingObjectField(String objectName, String fieldName, String argumentName) {
        if (!objectDifferences.containsKey(objectName)) {
            return false;
        }
        if (!(objectDifferences.get(objectName) instanceof ObjectModification)) {
            return false;
        }
        // finding out if the field was just added
        ObjectModification objectModification = (ObjectModification) objectDifferences.get(objectName);
        List<ObjectFieldDeletion> deletedFields = objectModification.getDetails(ObjectFieldDeletion.class);
        boolean deletedField = deletedFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
        if (deletedField) {
            return false;
        }
        // now finding out if the argument is deleted
        List<ObjectFieldArgumentDeletion> deletedArgs = objectModification.getDetails(ObjectFieldArgumentDeletion.class);
        return deletedArgs.stream().anyMatch(detail -> detail.getFieldName().equals(fieldName) && detail.getName().equals(argumentName));
    }

    private boolean isArgumentDeletedFromExistingInterfaceField(String interfaceName, String fieldName, String argumentName) {
        if (!interfaceDifferences.containsKey(interfaceName)) {
            return false;
        }
        if (!(interfaceDifferences.get(interfaceName) instanceof InterfaceModification)) {
            return false;
        }
        // finding out if the field was just added
        InterfaceModification interfaceModification = (InterfaceModification) interfaceDifferences.get(interfaceName);
        List<InterfaceFieldDeletion> deletedFields = interfaceModification.getDetails(InterfaceFieldDeletion.class);
        boolean deletedField = deletedFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
        if (deletedField) {
            return false;
        }
        // now finding out if the argument is deleted
        List<InterfaceFieldArgumentDeletion> deletedArgs = interfaceModification.getDetails(InterfaceFieldArgumentDeletion.class);
        return deletedArgs.stream().anyMatch(detail -> detail.getFieldName().equals(fieldName) && detail.getName().equals(argumentName));
    }

    private boolean isArgumentNewForExistingInterfaceField(String objectName, String fieldName, String argumentName) {
        if (!interfaceDifferences.containsKey(objectName)) {
            return false;
        }
        if (!(interfaceDifferences.get(objectName) instanceof InterfaceModification)) {
            return false;
        }
        // finding out if the field was just added
        InterfaceModification interfaceModification = (InterfaceModification) interfaceDifferences.get(objectName);
        List<InterfaceFieldAddition> newFields = interfaceModification.getDetails(InterfaceFieldAddition.class);
        boolean newField = newFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
        if (newField) {
            return false;
        }
        // now finding out if the argument is new
        List<InterfaceFieldArgumentAddition> newArgs = interfaceModification.getDetails(InterfaceFieldArgumentAddition.class);
        return newArgs.stream().anyMatch(detail -> detail.getFieldName().equals(fieldName) && detail.getName().equals(argumentName));
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

    private boolean isFieldDeletedFromExistingInterface(String interfaceName, String fieldName) {
        if (!interfaceDifferences.containsKey(interfaceName)) {
            return false;
        }
        if (!(interfaceDifferences.get(interfaceName) instanceof InterfaceModification)) {
            return false;
        }
        InterfaceModification interfaceModification = (InterfaceModification) interfaceDifferences.get(interfaceName);
        List<InterfaceFieldDeletion> deletedFields = interfaceModification.getDetails(InterfaceFieldDeletion.class);
        return deletedFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
    }

    private boolean isFieldDeletedFromExistingObject(String objectName, String fieldName) {
        if (!objectDifferences.containsKey(objectName)) {
            return false;
        }
        if (!(objectDifferences.get(objectName) instanceof ObjectModification)) {
            return false;
        }
        ObjectModification objectModification = (ObjectModification) objectDifferences.get(objectName);
        List<ObjectFieldDeletion> deletedFields = objectModification.getDetails(ObjectFieldDeletion.class);
        return deletedFields.stream().anyMatch(detail -> detail.getName().equals(fieldName));
    }

    private boolean isNewEnumValueForExistingEnum(String enumName, String valueName) {
        if (!enumDifferences.containsKey(enumName)) {
            return false;
        }
        if (!(enumDifferences.get(enumName) instanceof EnumModification)) {
            return false;
        }
        EnumModification enumModification = (EnumModification) enumDifferences.get(enumName);
        List<EnumValueAddition> newValues = enumModification.getDetails(EnumValueAddition.class);
        return newValues.stream().anyMatch(detail -> detail.getName().equals(valueName));
    }

    private boolean isEnumValueDeletedFromExistingEnum(String enumName, String valueName) {
        if (!enumDifferences.containsKey(enumName)) {
            return false;
        }
        if (!(enumDifferences.get(enumName) instanceof EnumModification)) {
            return false;
        }
        EnumModification enumModification = (EnumModification) enumDifferences.get(enumName);
        List<EnumValueDeletion> deletedValues = enumModification.getDetails(EnumValueDeletion.class);
        return deletedValues.stream().anyMatch(detail -> detail.getName().equals(valueName));
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

    private boolean isScalarAdded(String name) {
        return scalarDifferences.containsKey(name) && scalarDifferences.get(name) instanceof ScalarAddition;
    }

    private boolean isScalarDeleted(String name) {
        return scalarDifferences.containsKey(name) && scalarDifferences.get(name) instanceof ScalarDeletion;
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

    private InputObjectModification getInputObjectModification(String newName) {
        if (!inputObjectDifferences.containsKey(newName)) {
            inputObjectDifferences.put(newName, new InputObjectModification(newName));
        }
        assertTrue(inputObjectDifferences.get(newName) instanceof InputObjectModification);
        return (InputObjectModification) inputObjectDifferences.get(newName);
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

    private ScalarModification getScalarModification(String newName) {
        if (!scalarDifferences.containsKey(newName)) {
            scalarDifferences.put(newName, new ScalarModification(newName));
        }
        assertTrue(scalarDifferences.get(newName) instanceof ScalarModification);
        return (ScalarModification) scalarDifferences.get(newName);
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
        // Note: sometimes the edit operation is the argument vertex itself being deleted
        // Other times, it is the edge to the argument type being deleted
        Vertex deletedArgument = editOperation.getSourceVertex();
        if (deletedArgument == null) {
            deletedArgument = editOperation.getSourceEdge().getTo();
        }

        Vertex fieldOrDirective = oldSchemaGraph.getFieldOrDirectiveForArgument(deletedArgument);
        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = oldSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                if (isObjectDeleted(object.getName())) {
                    return;
                }
                if (isFieldDeletedFromExistingObject(object.getName(), field.getName())) {
                    return;
                }
                if (isArgumentDeletedFromExistingObjectField(object.getName(), field.getName(), deletedArgument.getName())) {
                    return;
                }
                getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentDeletion(field.getName(), deletedArgument.getName()));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                if (isInterfaceDeleted(interfaze.getName())) {
                    return;
                }
                if (isFieldDeletedFromExistingInterface(interfaze.getName(), field.getName())) {
                    return;
                }
                if (isArgumentDeletedFromExistingInterfaceField(interfaze.getName(), field.getName(), deletedArgument.getName())) {
                    return;
                }
                getInterfaceModification(interfaze.getName()).getDetails().add(new InterfaceFieldArgumentDeletion(field.getName(), deletedArgument.getName()));
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            if (isDirectiveDeleted(directive.getName())) {
                return;
            }
            if (isArgumentDeletedFromExistingDirective(directive.getName(), deletedArgument.getName())) {
                return;
            }
            getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentDeletion(deletedArgument.getName()));
        }
    }

    private void argumentAdded(EditOperation editOperation) {
        Vertex addedArgument = editOperation.getTargetVertex();
        if (addedArgument == null) {
            addedArgument = editOperation.getTargetEdge().getTo();
        }

        Vertex fieldOrDirective = newSchemaGraph.getFieldOrDirectiveForArgument(addedArgument);

        if (fieldOrDirective.isOfType(SchemaGraph.FIELD)) {
            Vertex field = fieldOrDirective;
            Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);
            if (fieldsContainerForField.isOfType(SchemaGraph.OBJECT)) {
                Vertex object = fieldsContainerForField;
                if (isObjectAdded(object.getName())) {
                    return;
                }
                if (isFieldNewForExistingObject(object.getName(), field.getName())) {
                    return;
                }
                if (isArgumentNewForExistingObjectField(object.getName(), field.getName(), addedArgument.getName())) {
                    return;
                }
                getObjectModification(object.getName()).getDetails().add(new ObjectFieldArgumentAddition(field.getName(), addedArgument.getName()));
            } else {
                assertTrue(fieldsContainerForField.isOfType(SchemaGraph.INTERFACE));
                Vertex interfaze = fieldsContainerForField;
                if (isInterfaceAdded(interfaze.getName())) {
                    return;
                }
                if (isFieldNewForExistingInterface(interfaze.getName(), field.getName())) {
                    return;
                }
                if (isArgumentNewForExistingInterfaceField(interfaze.getName(), field.getName(), addedArgument.getName())) {
                    return;
                }
                getInterfaceModification(interfaze.getName()).getDetails().add(new InterfaceFieldArgumentAddition(field.getName(), addedArgument.getName()));
            }
        } else {
            assertTrue(fieldOrDirective.isOfType(SchemaGraph.DIRECTIVE));
            Vertex directive = fieldOrDirective;
            if (isDirectiveAdded(directive.getName())) {
                return;
            }
            if (isArgumentNewForExistingDirective(directive.getName(), addedArgument.getName())) {
                return;
            }
            getDirectiveModification(directive.getName()).getDetails().add(new DirectiveArgumentAddition(addedArgument.getName()));
        }
    }

    private void changedEnum(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        EnumModification modification = new EnumModification(oldName, newName);
        enumDifferences.put(oldName, modification);
        enumDifferences.put(newName, modification);
    }

    private void changedScalar(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        ScalarModification modification = new ScalarModification(oldName, newName);
        scalarDifferences.put(oldName, modification);
        scalarDifferences.put(newName, modification);
    }

    private void changedInputObject(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        InputObjectModification modification = new InputObjectModification(oldName, newName);
        inputObjectDifferences.put(oldName, modification);
        inputObjectDifferences.put(newName, modification);
    }

    private void changedDirective(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        DirectiveModification modification = new DirectiveModification(oldName, newName);
        directiveDifferences.put(oldName, modification);
        directiveDifferences.put(newName, modification);
    }

    private void changedObject(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        ObjectModification objectModification = new ObjectModification(oldName, newName);
        objectDifferences.put(oldName, objectModification);
        objectDifferences.put(newName, objectModification);
    }

    private void changedInterface(EditOperation editOperation) {
        String oldName = editOperation.getSourceVertex().getName();
        String newName = editOperation.getTargetVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        InterfaceModification interfaceModification = new InterfaceModification(oldName, newName);
        interfaceDifferences.put(oldName, interfaceModification);
        interfaceDifferences.put(newName, interfaceModification);
    }

    private void changedUnion(EditOperation editOperation) {
        String newName = editOperation.getTargetVertex().getName();
        String oldName = editOperation.getSourceVertex().getName();

        if (oldName.equals(newName)) {
            // Something else like description could have changed
            return;
        }

        UnionModification objectModification = new UnionModification(oldName, newName);
        unionDifferences.put(oldName, objectModification);
        unionDifferences.put(newName, objectModification);
    }
}
