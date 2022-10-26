package graphql.schema.diffing.ana;

import graphql.ExperimentalApi;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    class ObjectFieldTypeModification implements ObjectModificationDetail {
        private final String fieldName;
        private final String oldType;
        private final String newType;

        public ObjectFieldTypeModification(String fieldName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.oldType = oldType;
            this.newType = newType;
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
        private final String name;

        public ObjectFieldArgumentAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class ObjectFieldArgumentTypeModification implements ObjectModificationDetail {
        private final String oldType;
        private final String newType;

        public ObjectFieldArgumentTypeModification(String oldType, String newType) {
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
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

    class InterfaceFieldTypeModification implements InterfaceModificationDetail {
        private final String oldType;
        private final String newType;

        public InterfaceFieldTypeModification(String oldType, String newType) {
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
        }
    }

    class InterfaceFieldArgumentDeletion implements InterfaceModificationDetail {
        private final String name;

        public InterfaceFieldArgumentDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldArgumentAddition implements InterfaceModificationDetail {
        private final String name;

        public InterfaceFieldArgumentAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InterfaceFieldArgumentTypeModification implements InterfaceModificationDetail {
        private final String oldType;
        private final String newType;

        public InterfaceFieldArgumentTypeModification(String oldType, String newType) {
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getNewType() {
            return newType;
        }

        public String getOldType() {
            return oldType;
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

        public InputObjectModification(String oldName, String newName) {
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

        private final List<EnumModificationDetail> details = new ArrayList<>();

        public EnumModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public EnumModification(String newName) {
            this.oldName = "";
            this.newName = newName;
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

    class ScalarModification implements SchemaModification, ScalarDifference {
        private final String oldName;
        private final String newName;

        public ScalarModification(String oldName, String newName) {
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

    class DirectiveModification implements SchemaModification, SchemaDifference {
        private final String oldName;
        private final String newName;

        public DirectiveModification(String oldName, String newName) {
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


}
