package graphql.schema.diffing.ana;

import graphql.ExperimentalApi;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Any kind of difference between two schemas is a SchemaDifference.
 *
 * Below that we have three different possible kind of differences:
 * - Addition
 * - Deletion
 * - Modification
 */
@ExperimentalApi
public interface SchemaDifference {

    interface SchemaAddition extends SchemaDifference {

    }

    interface SchemaDeletion extends SchemaDifference {

    }

    interface SchemaModification extends SchemaDifference {

    }

    interface SchemaModificationDetail extends SchemaDifference {

    }

    //------------ Object
    public
    interface ObjectDifference extends SchemaDifference {

    }

    class ObjectAddition implements SchemaAddition, ObjectDifference {
        private final String name;

        public ObjectAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectDeletion implements SchemaDeletion, ObjectDifference {
        private final String name;

        public ObjectDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectModification implements SchemaModification, ObjectDifference {
        private final String oldName;
        private final String newName;
        private final boolean renamed;
        private final List<ObjectModificationDetail> details = new ArrayList<>();

        public ObjectModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.renamed = oldName.equals(newName);
        }

        public ObjectModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.renamed = false;
        }

        public List<ObjectModificationDetail> getDetails() {
            return details;
        }

        public <T extends ObjectModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
        }

        public boolean isRenamed() {
            return renamed;
        }
    }

    interface ObjectModificationDetail {

    }

    class ObjectInterfaceImplementationAddition implements ObjectModificationDetail {
        private final String name;

        public ObjectInterfaceImplementationAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectInterfaceImplementationDeletion implements ObjectModificationDetail {
        private final String name;

        public ObjectInterfaceImplementationDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectFieldAddition implements ObjectModificationDetail {
        private final String name;

        public ObjectFieldAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectFieldDeletion implements ObjectModificationDetail {
        private final String name;

        public ObjectFieldDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectFieldRename implements ObjectModificationDetail {
        private final String oldName;
        private final String newName;

        public ObjectFieldRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }

    class ObjectFieldArgumentRename implements ObjectModificationDetail {
        private final String oldName;
        private final String newName;

        public ObjectFieldArgumentRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }

    class ObjectFieldTypeModification implements ObjectModificationDetail {
        private final String fieldName;
        private final String oldType;
        private final String newType;

        public ObjectFieldTypeModification(String fieldName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }
    }

    class ObjectFieldArgumentDeletion implements ObjectModificationDetail {
        private final String fieldName;
        private final String name;

        public ObjectFieldArgumentDeletion(String fieldName, String name) {
            this.fieldName = fieldName;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    class ObjectFieldArgumentAddition implements ObjectModificationDetail {
        private final String fieldName;
        private final String name;


        public ObjectFieldArgumentAddition(String fieldName, String name) {
            this.fieldName = fieldName;
            this.name = name;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectFieldArgumentTypeModification implements ObjectModificationDetail {
        private final String fieldName;
        private final String argumentName;
        private final String oldType;
        private final String newType;

        public ObjectFieldArgumentTypeModification(String fieldName, String argumentName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
        }
    }

    class ObjectFieldArgumentDefaultValueModification implements ObjectModificationDetail {
        private final String fieldName;
        private final String argumentName;
        private final String oldValue;
        private final String newValue;

        public ObjectFieldArgumentDefaultValueModification(String fieldName, String argumentName, String oldValue, String newValue) {
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
        }
    }

    //------------ Interface
    interface InterfaceDifference extends SchemaDifference {

    }

    class InterfaceAddition implements SchemaAddition, InterfaceDifference {
        private final String name;

        public InterfaceAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceDeletion implements SchemaDeletion, InterfaceDifference {
        private final String name;

        public InterfaceDeletion(String name) {
            this.name = name;
        }
    }

    class InterfaceModification implements SchemaModification, InterfaceDifference {
        private final String oldName;
        private final String newName;
        private final boolean renamed;
        private final List<InterfaceModificationDetail> details = new ArrayList<>();

        public InterfaceModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.renamed = oldName.equals(newName);
        }

        public InterfaceModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.renamed = false;
        }

        public List<InterfaceModificationDetail> getDetails() {
            return details;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }

        public boolean isRenamed() {
            return renamed;
        }

        public <T extends InterfaceModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }
    }

    interface InterfaceModificationDetail {

    }

    class InterfaceInterfaceImplementationAddition implements InterfaceModificationDetail {
        private final String name;

        public InterfaceInterfaceImplementationAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceInterfaceImplementationDeletion implements InterfaceModificationDetail {
        private final String name;

        public InterfaceInterfaceImplementationDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldAddition implements InterfaceModificationDetail {
        private final String name;

        public InterfaceFieldAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldDeletion implements InterfaceModificationDetail {
        private final String name;

        public InterfaceFieldDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldRename implements InterfaceModificationDetail {
        private final String oldName;
        private final String newName;

        public InterfaceFieldRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }


    class InterfaceFieldTypeModification implements InterfaceModificationDetail {
        private final String fieldName;
        private final String oldType;
        private final String newType;

        public InterfaceFieldTypeModification(String fieldName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }
    }

    class InterfaceFieldArgumentDeletion implements InterfaceModificationDetail {
        private final String fieldName;
        private final String name;


        public InterfaceFieldArgumentDeletion(String fieldName, String name) {
            this.fieldName = fieldName;
            this.name = name;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldArgumentAddition implements InterfaceModificationDetail {
        private final String fieldName;
        private final String name;

        public InterfaceFieldArgumentAddition(String fieldName, String name) {
            this.fieldName = fieldName;
            this.name = name;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldArgumentTypeModification implements InterfaceModificationDetail {

        private String fieldName;
        private final String argumentName;
        private final String oldType;
        private final String newType;

        public InterfaceFieldArgumentTypeModification(String fieldName, String argumentName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }

        public String getArgumentName() {
            return argumentName;
        }

    }

    class InterfaceFieldArgumentDefaultValueModification implements InterfaceModificationDetail {
        private final String fieldName;
        private final String argumentName;
        private final String oldValue;
        private final String newValue;


        public InterfaceFieldArgumentDefaultValueModification(String fieldName, String argumentName, String oldValue, String newValue) {
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
        }
    }

    class InterfaceFieldArgumentRename implements InterfaceModificationDetail {
        private final String oldName;
        private final String newName;

        public InterfaceFieldArgumentRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }


    // -----Union-----------
    interface UnionDifference extends SchemaDifference {

    }

    class UnionAddition implements SchemaAddition, UnionDifference {
        private final String name;

        public UnionAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class UnionDeletion implements SchemaDeletion, UnionDifference {
        private final String name;

        public UnionDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class UnionModification implements SchemaModification, UnionDifference {
        private final String oldName;
        private final String newName;
        private final boolean nameChanged;

        private final List<UnionModificationDetail> details = new ArrayList<>();

        public UnionModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.nameChanged = oldName.equals(newName);
        }

        public UnionModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.nameChanged = false;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }

        public List<UnionModificationDetail> getDetails() {
            return details;
        }

        public <T extends UnionModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }

        public boolean isNameChanged() {
            return nameChanged;
        }
    }

    interface UnionModificationDetail {

    }

    class UnionMemberAddition implements UnionModificationDetail {
        private final String name;

        public UnionMemberAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class UnionMemberDeletion implements UnionModificationDetail {
        private final String name;

        public UnionMemberDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    //--------InputObject

    interface InputObjectDifference extends SchemaDifference {

    }

    class InputObjectAddition implements SchemaAddition, InputObjectDifference {
        private final String name;

        public InputObjectAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InputObjectDeletion implements SchemaDeletion, InputObjectDifference {
        private final String name;

        public InputObjectDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InputObjectModification implements SchemaModification, InputObjectDifference {
        private final String oldName;
        private final String newName;
        private final boolean nameChanged;


        public InputObjectModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.nameChanged = oldName.equals(newName);
        }

        public boolean isNameChanged() {
            return nameChanged;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }

    //-------Enum
    interface EnumDifference extends SchemaDifference {

    }

    class EnumAddition implements SchemaAddition, EnumDifference {
        private final String name;

        public EnumAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class EnumDeletion implements SchemaDeletion, EnumDifference {
        private final String name;

        public EnumDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class EnumModification implements SchemaModification, EnumDifference {
        private final String oldName;
        private final String newName;

        private final boolean nameChanged;
        private final List<EnumModificationDetail> details = new ArrayList<>();

        public EnumModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.nameChanged = oldName.equals(newName);
        }

        public EnumModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.nameChanged = false;
        }

        public boolean isNameChanged() {
            return nameChanged;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }

        public List<EnumModificationDetail> getDetails() {
            return details;
        }

        public <T extends EnumModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }

    }

    interface EnumModificationDetail {

    }

    class EnumValueDeletion implements EnumModificationDetail {
        private final String name;

        public EnumValueDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class EnumValueAddition implements EnumModificationDetail {
        private final String name;

        public EnumValueAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    //--------Scalar
    interface ScalarDifference extends SchemaDifference {

    }

    class ScalarAddition implements SchemaAddition, ScalarDifference {
        private final String name;

        public ScalarAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ScalarDeletion implements SchemaDeletion, ScalarDifference {
        private final String name;

        public ScalarDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    interface ScalarModificationDetail {

    }

    class ScalarModification implements SchemaModification, ScalarDifference {
        private final String oldName;
        private final String newName;
        private final boolean nameChanged;
        private List<ScalarModificationDetail> details = new ArrayList<>();


        public ScalarModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.nameChanged = oldName.equals(newName);
        }
        public ScalarModification( String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.nameChanged = false;
        }


        public boolean isNameChanged() {
            return nameChanged;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
        public List<ScalarModificationDetail> getDetails() {
            return details;
        }

        public <T extends ScalarModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }

    }

    //------Directive
    interface DirectiveDifference extends SchemaDifference {

    }

    class DirectiveAddition implements SchemaAddition, DirectiveDifference {
        private final String name;

        public DirectiveAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class DirectiveDeletion implements SchemaDeletion, DirectiveDifference {
        private final String name;

        public DirectiveDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class DirectiveModification implements SchemaModification, DirectiveDifference {
        private final String oldName;
        private final String newName;
        private final boolean nameChanged;

        private final List<DirectiveModificationDetail> details = new ArrayList<>();

        public DirectiveModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.nameChanged = oldName.equals(newName);
        }

        public DirectiveModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.nameChanged = false;
        }

        public boolean isNameChanged() {
            return nameChanged;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }

        public List<DirectiveModificationDetail> getDetails() {
            return details;
        }

        public <T extends DirectiveModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
        }
    }

    interface DirectiveModificationDetail {

    }

    class DirectiveArgumentDeletion implements DirectiveModificationDetail {
        private final String name;

        public DirectiveArgumentDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

    }

    class DirectiveArgumentAddition implements DirectiveModificationDetail {
        private final String name;

        public DirectiveArgumentAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class DirectiveArgumentTypeModification implements DirectiveModificationDetail {
        private final String argumentName;
        private final String oldType;
        private final String newType;


        public DirectiveArgumentTypeModification(String argumentName, String oldType, String newType) {
            this.argumentName = argumentName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getArgumentName() {
            return argumentName;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }
    }

    class DirectiveArgumentDefaultValueModification implements DirectiveModificationDetail {
        private final String argumentName;
        private final String oldValue;
        private final String newValue;

        public DirectiveArgumentDefaultValueModification(String argumentName, String oldValue, String newValue) {
            this.argumentName = argumentName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public String getArgumentName() {
            return argumentName;
        }
    }

    class DirectiveArgumentRename implements DirectiveModificationDetail {
        private final String oldName;
        private final String newName;

        public DirectiveArgumentRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }

    //------Applied Directives
    interface AppliedDirectiveDifference {

    }

    /**
     * SCHEMA,
     * SCALAR,
     * OBJECT,
     * FIELD_DEFINITION,
     * ARGUMENT_DEFINITION,
     * INTERFACE,
     * UNION,
     * ENUM,
     * ENUM_VALUE,
     * INPUT_OBJECT,
     * INPUT_FIELD_DEFINITION
     */

    interface AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveObjectFieldLocation implements AppliedDirectiveLocationDetail {
        private final String objectName;
        private final String fieldName;

        public AppliedDirectiveObjectFieldLocation(String objectName, String fieldName) {
            this.objectName = objectName;
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getObjectName() {
            return objectName;
        }
    }

    class AppliedDirectiveInterfaceFieldLocation implements AppliedDirectiveLocationDetail {
        private final String interfaceName;
        private final String fieldName;

        public AppliedDirectiveInterfaceFieldLocation(String interfaceName, String fieldName) {
            this.interfaceName = interfaceName;
            this.fieldName = fieldName;
        }
    }

    class AppliedDirectiveScalarLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveScalarLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveSchemaLocation implements AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveObjectLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveObjectLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveInterfaceLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveInterfaceLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveArgumentLocation implements AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveUnionLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveUnionLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveEnumLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveEnumLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveEnumValueLocation implements AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveInputObjectLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        public AppliedDirectiveInputObjectLocation(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    class AppliedDirectiveInputObjectFieldLocation implements AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveAddition implements ObjectModificationDetail, InterfaceModificationDetail, ScalarModificationDetail, EnumModificationDetail {
        private final AppliedDirectiveLocationDetail locationDetail;
        private final String name;

        public AppliedDirectiveAddition(AppliedDirectiveLocationDetail locationDetail, String name) {
            this.locationDetail = locationDetail;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public AppliedDirectiveLocationDetail getLocationDetail() {
            return locationDetail;
        }
    }

    class AppliedDirectiveDeletion {

    }

    class AppliedDirectiveRenamed {

    }

    class AppliedDirectiveArgumentAddition {

    }

    class AppliedDirectiveArgumentDeletion {

    }

    class AppliedDirectiveArgumentValueModification implements ObjectModificationDetail {
        private final AppliedDirectiveLocationDetail locationDetail;
        private final String argumentName;
        private final String oldValue;
        private final String newValue;

        public AppliedDirectiveArgumentValueModification(AppliedDirectiveLocationDetail locationDetail, String argumentName, String oldValue, String newValue) {
            this.locationDetail = locationDetail;
            this.argumentName = argumentName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public AppliedDirectiveLocationDetail getLocationDetail() {
            return locationDetail;
        }

        public String getArgumentName() {
            return argumentName;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }
    }

    class AppliedDirectiveArgumentRename implements ObjectModificationDetail {
        private final AppliedDirectiveLocationDetail locationDetail;
        private final String oldName;
        private final String newName;

        public AppliedDirectiveArgumentRename(AppliedDirectiveLocationDetail locationDetail, String oldName, String newName) {
            this.locationDetail = locationDetail;
            this.oldName = oldName;
            this.newName = newName;
        }

        public AppliedDirectiveLocationDetail getLocationDetail() {
            return locationDetail;
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
        }
    }


}
