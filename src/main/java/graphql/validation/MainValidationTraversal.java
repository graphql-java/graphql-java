package graphql.validation;

import graphql.execution.MergedField;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.AstComparator;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.normalized.NormalizedField;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.List;

import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.isNullable;
import static graphql.schema.GraphQLTypeUtil.isScalar;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;

public class MainValidationTraversal {

    private final GraphQLSchema graphQLSchema;
    private final Document document;


    private List<Node> path = new ArrayList<>();
    private final RulesVisitor rulesVisitor;

    public MainValidationTraversal(GraphQLSchema graphQLSchema, Document document, String operationNamem, RulesVisitor rulesVisitor) {
        this.graphQLSchema = graphQLSchema;
        this.document = document;
        this.rulesVisitor = rulesVisitor;
    }

    public void checkDocument() {
        rulesVisitor.enter(document, path);
        path.add(document);
        // we take the freedom here to collect first all FragmentDefinition so that we can access them later
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        for (Definition definition : document.getDefinitions()) {
            visitDefinition(definition);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(document, path);
    }

    private void visitDefinition(Definition definition) {
        if (definition instanceof OperationDefinition) {
            visitOperationDefinition((OperationDefinition) definition);
        } else if (definition instanceof FragmentDefinition) {
            visitFragmentDefinition((FragmentDefinition) definition);
        }
    }

    private void visitFragmentDefinition(FragmentDefinition definition) {
        rulesVisitor.enter(definition, path);
        path.add(definition);
        visitTypeName(definition.getTypeCondition());
        for (Directive directive : definition.getDirectives()) {
            visitDirective(directive);
        }
        visitSelectionSet(definition.getSelectionSet());
        path.remove(path.size() - 1);
        rulesVisitor.leave(definition, path);
    }

    private void visitOperationDefinition(OperationDefinition definition) {
        rulesVisitor.enter(definition, path);
        path.add(definition);
        for (VariableDefinition variableDefinition : definition.getVariableDefinitions()) {
            visitVariableDefinition(variableDefinition);
        }
        for (Directive directive : definition.getDirectives()) {
            visitDirective(directive);
        }
        visitSelectionSet(definition.getSelectionSet());
        path.remove(path.size() - 1);
        rulesVisitor.leave(definition, path);
    }

    private void visitTypeName(TypeName node) {
        rulesVisitor.enter(node, path);
        path.add(node);

        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitArgument(Argument node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        visitValue(node.getValue());
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitVariableDefinition(VariableDefinition node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        visitType(node.getType());
        if (node.getDefaultValue() != null) {
            visitValue(node.getDefaultValue());
        }
        for (Directive directive : node.getDirectives()) {
            visitDirective(directive);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitType(Type node) {
        if (node instanceof TypeName) {
            visitTypeName((TypeName) node);
        } else if (node instanceof NonNullType) {
            visitNonNullType((NonNullType) node);
        } else if (node instanceof ListType) {
            visitListType((ListType) node);
        }
    }

    private void visitListType(ListType node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        visitType(node.getType());
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);

    }

    private void visitNonNullType(NonNullType node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        visitType(node.getType());
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitDirective(Directive node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        for (Argument argument : node.getArguments()) {
            visitArgument(argument);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitVariableReference(VariableReference node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitSelectionSet(SelectionSet selectionSet) {
        rulesVisitor.enter(selectionSet, path);
        path.add(selectionSet);

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                visitField((Field) selection);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread((FragmentSpread) selection);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment((InlineFragment) selection);
            }
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(selectionSet, path);
    }

    private void visitField(Field field) {
        rulesVisitor.enter(field, path);
        path.add(field);
        for (Argument argument : field.getArguments()) {
            visitArgument(argument);
        }
        for (Directive directive : field.getDirectives()) {
            visitDirective(directive);
        }
        if (field.getSelectionSet() != null) {
            visitSelectionSet(field.getSelectionSet());
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(field, path);
    }

    private void visitInlineFragment(InlineFragment inlineFragment) {
        rulesVisitor.enter(inlineFragment, path);
        path.add(inlineFragment);
        if (inlineFragment.getTypeCondition() != null) {
            visitTypeName(inlineFragment.getTypeCondition());
        }
        for (Directive directive : inlineFragment.getDirectives()) {
            visitDirective(directive);
        }
        visitSelectionSet(inlineFragment.getSelectionSet());
        path.remove(path.size() - 1);
        rulesVisitor.leave(inlineFragment, path);
    }

    private void visitFragmentSpread(FragmentSpread fragmentSpread) {
        rulesVisitor.enter(fragmentSpread, path);
        path.add(fragmentSpread);
        for (Directive directive : fragmentSpread.getDirectives()) {
            visitDirective(directive);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(fragmentSpread, path);
    }

    private void visitValue(Value value) {
        if (value instanceof VariableReference) {
            visitVariableReference((VariableReference) value);
        } else if (value instanceof ArrayValue) {
            visitArrayValue((ArrayValue) value);
        } else if (value instanceof ObjectValue) {
            visitObjectValue((ObjectValue) value);
        }
    }

    private void visitObjectValue(ObjectValue node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        for (ObjectField objectField : node.getObjectFields()) {
            visitObjectField(objectField);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitObjectField(ObjectField node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        visitValue(node.getValue());
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private void visitArrayValue(ArrayValue node) {
        rulesVisitor.enter(node, path);
        path.add(node);
        for (Value value : node.getValues()) {
            visitValue(value);
        }
        path.remove(path.size() - 1);
        rulesVisitor.leave(node, path);
    }

    private Conflict checkIfFieldIsCompatible(Field field, GraphQLType fieldType, GraphQLCompositeType parentType, NormalizedField parentNormalizedField) {
        String resultKey = field.getResultKey();
        NormalizedField child = parentNormalizedField.getChild(resultKey);
        if (child == null) {
            // new normalized field
        }

        Field fieldA = field;
        Field fieldB = normalizedFieldToMergedField.get(child).getSingleField();

        GraphQLType typeA = fieldType;
        GraphQLType typeB = child.getFieldType();

        Conflict conflict = checkListAndNonNullConflict(field, fieldType, child);

        if (conflict != null) {
            return conflict;
        }

        typeA = unwrapAll(typeA);
        typeB = unwrapAll(typeB);

        if (checkScalarAndEnumConflict(typeA, typeB)) {
            return mkNotSameTypeError(resultKey, fieldA, fieldB, typeA, typeB);
        }

        GraphQLType parentTypeA = parentType;
        GraphQLType parentTypeB = unwrapAll(parentNormalizedField.getFieldType());

        // the rest of the checks are only needed if both fields need to be
        // exactly the same: same field name with same arguments
        // Both fields needs to be exactly the same if at execution time
        // both could be valid and therefore needed to be executed together
        // The only case where they don't need to be the same is
        // for both parents being Object types
        if (!sameType(parentTypeA, parentTypeB) &&
                parentTypeA instanceof GraphQLObjectType &&
                parentTypeB instanceof GraphQLObjectType) {

            MergedField mergedField = normalizedFieldToMergedField.get(child);
            MergedField newMergedField = mergedField.transform(builder -> builder.addField(field));
            normalizedFieldToMergedField.put(child, newMergedField);
            return null;
        }

        String fieldNameA = fieldA.getName();
        String fieldNameB = fieldB.getName();

        if (!fieldNameA.equals(fieldNameB)) {
            String reason = format("%s: %s and %s are different fields", resultKey, fieldNameA, fieldNameB);
            return new Conflict(resultKey, reason, fieldA, fieldB);
        }

        if (!sameType(typeA, typeB)) {
            return mkNotSameTypeError(resultKey, fieldA, fieldB, typeA, typeB);
        }

        if (!sameArguments(fieldA.getArguments(), fieldB.getArguments())) {
            String reason = format("%s: they have differing arguments", resultKey);
            return new Conflict(resultKey, reason, fieldA, fieldB);
        }

        MergedField mergedField = normalizedFieldToMergedField.get(child);
        MergedField newMergedField = mergedField.transform(builder -> builder.addField(field));
        normalizedFieldToMergedField.put(child, newMergedField);
        return null;
    }

    private Conflict mkNotSameTypeError(String responseName, Field fieldA, Field fieldB, GraphQLType typeA, GraphQLType typeB) {
        String name1 = typeA != null ? simplePrint(typeA) : "null";
        String name2 = typeB != null ? simplePrint(typeB) : "null";
        String reason = format("%s: they return differing types %s and %s", responseName, name1, name2);
        return new Conflict(responseName, reason, fieldA, fieldB);
    }


    private boolean checkScalarAndEnumConflict(GraphQLType typeA, GraphQLType typeB) {
        if (isScalar(typeA) || isScalar(typeB)) {
            if (!sameType(typeA, typeB)) {
                return true;
            }
        }
        if (isEnum(typeA) || isEnum(typeB)) {
            if (!sameType(typeA, typeB)) {
                return true;
            }
        }
        return false;
    }

    private String joinReasons(List<Conflict> conflicts) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        for (Conflict conflict : conflicts) {
            result.append(conflict.reason);
            result.append(", ");
        }
        result.delete(result.length() - 2, result.length());
        result.append(")");
        return result.toString();
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean sameType(GraphQLType type1, GraphQLType type2) {
        if (type1 == null || type2 == null) {
            return true;
        }
        return type1.equals(type2);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean sameValue(Value value1, Value value2) {
        if (value1 == null && value2 == null) {
            return true;
        }
        if (value1 == null) {
            return false;
        }
        if (value2 == null) {
            return false;
        }
        return new AstComparator().isEqual(value1, value2);
    }

    private boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!sameValue(argument.getValue(), matchedArgument.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }


    private Conflict checkListAndNonNullConflict(Field field, GraphQLType fieldType, NormalizedField normalizedField) {
        String resultKey = field.getResultKey();

        Field fieldA = field;
        Field fieldB = normalizedFieldToMergedField.get(normalizedField).getSingleField();
        GraphQLType typeA = fieldType;
        GraphQLType typeB = normalizedField.getFieldDefinition().getType();

        while (true) {
            if (isNonNull(typeA) || isNonNull(typeB)) {
                if (isNullable(typeA) || isNullable(typeB)) {
                    String reason = format("%s: fields have different nullability shapes", resultKey);
                    return new Conflict(resultKey, reason, fieldA, fieldB);
                }
            }
            if (isList(typeA) || isList(typeB)) {
                if (!isList(typeA) || !isList(typeB)) {
                    String reason = format("%s: fields have different list shapes", resultKey);
                    return new Conflict(resultKey, reason, fieldA, fieldB);
                }
            }
            if (isNotWrapped(typeA) && isNotWrapped(typeB)) {
                break;
            }
            typeA = unwrapOne(typeA);
            typeB = unwrapOne(typeB);
        }
        return null;
    }


    private static class Conflict {
        final String responseName;
        final String reason;
        final List<Field> fields = new ArrayList<>();

        public Conflict(String responseName, String reason, Field field1, Field field2) {
            this.responseName = responseName;
            this.reason = reason;
            this.fields.add(field1);
            this.fields.add(field2);
        }

        public Conflict(String responseName, String reason, List<Field> fields) {
            this.responseName = responseName;
            this.reason = reason;
            this.fields.addAll(fields);
        }

    }


}

