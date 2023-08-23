package graphql.schema.diffing.ana;

import graphql.Internal;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Any kind of difference between two schemas is a SchemaDifference.
 * <p>
 * Below that we have three different possible kind of differences:
 * - Addition
 * - Deletion
 * - Modification
 */
@Internal
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
        private final boolean isNameChanged;
        private final List<ObjectModificationDetail> details = new ArrayList<>();

        public ObjectModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public ObjectModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
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

        public boolean isNameChanged() {
            return isNameChanged;
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
        private final String fieldName;
        private final String oldName;
        private final String newName;

        public ObjectFieldArgumentRename(String fieldName, String oldName, String newName) {
            this.fieldName = fieldName;
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getFieldName() {
            return fieldName;
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

        public String getName() {
            return name;
        }
    }

    class InterfaceModification implements SchemaModification, InterfaceDifference {
        private final String oldName;
        private final String newName;
        private final boolean isNameChanged;
        private final List<InterfaceModificationDetail> details = new ArrayList<>();

        public InterfaceModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public InterfaceModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
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

        public boolean isNameChanged() {
            return isNameChanged;
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

        private final String fieldName;
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
        private final String fieldName;
        private final String oldName;
        private final String newName;

        public InterfaceFieldArgumentRename(String fieldName, String oldName, String newName) {
            this.fieldName = fieldName;
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getFieldName() {
            return fieldName;
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
        private final boolean isNameChanged;

        private final List<UnionModificationDetail> details = new ArrayList<>();

        public UnionModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public UnionModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
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
            return isNameChanged;
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

    interface InputObjectModificationDetail {

    }

    class InputObjectFieldDeletion implements InputObjectModificationDetail {
        private final String name;

        public InputObjectFieldDeletion(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InputObjectFieldRename implements InputObjectModificationDetail {
        private final String oldName;
        private final String newName;

        public InputObjectFieldRename(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
        }
    }

    class InputObjectFieldDefaultValueModification implements InputObjectModificationDetail {
        private final String fieldName;
        private final String oldDefaultValue;
        private final String newDefaultValue;

        public InputObjectFieldDefaultValueModification(String fieldName, String oldDefaultValue, String newDefaultValue) {
            this.fieldName = fieldName;
            this.oldDefaultValue = oldDefaultValue;
            this.newDefaultValue = newDefaultValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getOldDefaultValue() {
            return oldDefaultValue;
        }

        public String getNewDefaultValue() {
            return newDefaultValue;
        }
    }

    class InputObjectFieldTypeModification implements InputObjectModificationDetail {
        private final String fieldName;
        private final String oldType;
        private final String newType;

        public InputObjectFieldTypeModification(String fieldName, String oldType, String newType) {
            this.fieldName = fieldName;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getOldType() {
            return oldType;
        }

        public String getNewType() {
            return newType;
        }
    }

    class InputObjectFieldAddition implements InputObjectModificationDetail {
        private final String name;

        public InputObjectFieldAddition(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class InputObjectModification implements SchemaModification, InputObjectDifference {
        private final String oldName;
        private final String newName;
        private final boolean isNameChanged;

        private final List<InputObjectModificationDetail> details = new ArrayList<>();


        public InputObjectModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public InputObjectModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
        }

        public boolean isNameChanged() {
            return isNameChanged;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }

        public List<InputObjectModificationDetail> getDetails() {
            return details;
        }

        public <T extends InputObjectModificationDetail> List<T> getDetails(Class<? extends T> clazz) {
            return (List) FpKit.filterList(details, clazz::isInstance);
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

        private final boolean isNameChanged;
        private final List<EnumModificationDetail> details = new ArrayList<>();

        public EnumModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public EnumModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
        }

        public boolean isNameChanged() {
            return isNameChanged;
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

    class EnumValueRenamed implements EnumModificationDetail {
        private final String oldName;
        private final String newName;

        public EnumValueRenamed(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
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
        private final boolean isNameChanged;
        private List<ScalarModificationDetail> details = new ArrayList<>();


        public ScalarModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public ScalarModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
        }


        public boolean isNameChanged() {
            return isNameChanged;
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
        private final boolean isNameChanged;

        private final List<DirectiveModificationDetail> details = new ArrayList<>();

        public DirectiveModification(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
            this.isNameChanged = !oldName.equals(newName);
        }

        public DirectiveModification(String newName) {
            this.oldName = newName;
            this.newName = newName;
            this.isNameChanged = false;
        }

        public boolean isNameChanged() {
            return isNameChanged;
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
        private final String directiveName;

        public AppliedDirectiveObjectFieldLocation(String objectName, String fieldName, String directiveName) {
            this.objectName = objectName;
            this.fieldName = fieldName;
            this.directiveName = directiveName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveInterfaceFieldLocation implements AppliedDirectiveLocationDetail {
        private final String interfaceName;
        private final String fieldName;

        private final String directiveName;


        public AppliedDirectiveInterfaceFieldLocation(String interfaceName, String fieldName, String directiveName) {
            this.interfaceName = interfaceName;
            this.fieldName = fieldName;
            this.directiveName = directiveName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveScalarLocation implements AppliedDirectiveLocationDetail {
        private final String name;

        private final String directiveName;

        public AppliedDirectiveScalarLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveSchemaLocation implements AppliedDirectiveLocationDetail {

    }

    class AppliedDirectiveObjectLocation implements AppliedDirectiveLocationDetail {
        private final String name;
        private final String directiveName;


        public AppliedDirectiveObjectLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveInterfaceLocation implements AppliedDirectiveLocationDetail {
        private final String name;
        private final String directiveName;

        public AppliedDirectiveInterfaceLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveObjectFieldArgumentLocation implements AppliedDirectiveLocationDetail {
        private final String objectName;
        private final String fieldName;
        private final String argumentName;
        private final String directiveName;


        public AppliedDirectiveObjectFieldArgumentLocation(String objectName, String fieldName, String argumentName, String directiveName) {
            this.objectName = objectName;
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.directiveName = directiveName;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveDirectiveArgumentLocation implements AppliedDirectiveLocationDetail {
        private final String directiveName;
        private final String argumentName;

        public AppliedDirectiveDirectiveArgumentLocation(String directiveName, String argumentName) {
            this.directiveName = directiveName;
            this.argumentName = argumentName;
        }

        public String getDirectiveName() {
            return directiveName;
        }

        public String getArgumentName() {
            return argumentName;
        }
    }

    class AppliedDirectiveInterfaceFieldArgumentLocation implements AppliedDirectiveLocationDetail {
        private final String interfaceName;
        private final String fieldName;
        private final String argumentName;
        private final String directiveName;


        public AppliedDirectiveInterfaceFieldArgumentLocation(String interfaceName, String fieldName, String argumentName, String directiveName) {
            this.interfaceName = interfaceName;
            this.fieldName = fieldName;
            this.argumentName = argumentName;
            this.directiveName = directiveName;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveUnionLocation implements AppliedDirectiveLocationDetail {
        private final String name;
        private final String directiveName;


        public AppliedDirectiveUnionLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }
    }

    class AppliedDirectiveEnumLocation implements AppliedDirectiveLocationDetail {
        private final String name;
        private final String directiveName;


        public AppliedDirectiveEnumLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveEnumValueLocation implements AppliedDirectiveLocationDetail {
        private final String enumName;
        private final String valueName;
        private final String directiveName;


        public AppliedDirectiveEnumValueLocation(String enumName, String valueName, String directiveName) {
            this.enumName = enumName;
            this.valueName = valueName;
            this.directiveName = directiveName;
        }

        public String getEnumName() {
            return enumName;
        }

        public String getValueName() {
            return valueName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveInputObjectLocation implements AppliedDirectiveLocationDetail {
        private final String name;
        private final String directiveName;


        public AppliedDirectiveInputObjectLocation(String name, String directiveName) {
            this.name = name;
            this.directiveName = directiveName;
        }

        public String getName() {
            return name;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }


    class AppliedDirectiveInputObjectFieldLocation implements AppliedDirectiveLocationDetail {
        private final String inputObjectName;
        private final String fieldName;
        private final String directiveName;


        public AppliedDirectiveInputObjectFieldLocation(String inputObjectName, String fieldName, String directiveName) {
            this.inputObjectName = inputObjectName;
            this.fieldName = fieldName;
            this.directiveName = directiveName;
        }

        public String getInputObjectName() {
            return inputObjectName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getDirectiveName() {
            return directiveName;
        }
    }

    class AppliedDirectiveAddition implements
            ObjectModificationDetail,
            InterfaceModificationDetail,
            ScalarModificationDetail,
            EnumModificationDetail,
            InputObjectModificationDetail,
            UnionModificationDetail,
            DirectiveModificationDetail {
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

    class AppliedDirectiveDeletion implements
            ObjectModificationDetail,
            InterfaceModificationDetail,
            ScalarModificationDetail,
            EnumModificationDetail,
            InputObjectModificationDetail,
            UnionModificationDetail,
            DirectiveModificationDetail {

        private final AppliedDirectiveLocationDetail locationDetail;
        private final String name;

        public AppliedDirectiveDeletion(AppliedDirectiveLocationDetail locationDetail, String name) {
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

    class AppliedDirectiveRenamed {

    }

    class AppliedDirectiveArgumentAddition {

    }

    class AppliedDirectiveArgumentDeletion implements ObjectModificationDetail, InterfaceModificationDetail {
        private final AppliedDirectiveLocationDetail locationDetail;
        private final String argumentName;

        public AppliedDirectiveArgumentDeletion(AppliedDirectiveLocationDetail locationDetail, String argumentName) {
            this.locationDetail = locationDetail;
            this.argumentName = argumentName;
        }

        public AppliedDirectiveLocationDetail getLocationDetail() {
            return locationDetail;
        }

        public String getArgumentName() {
            return argumentName;
        }
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
