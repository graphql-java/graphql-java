package graphql.validation;

import graphql.Assert;
import graphql.language.Argument;
import graphql.language.ArrayValue;
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
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainValidationTraversal {

    private final GraphQLSchema schema;
    private final Document document;


    private List<Node> path = new ArrayList<>();
    private final RulesVisitor rulesVisitor;

    private NormalizedField currentNormalizedField;

    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private OverlappingFields overlappingFields;
    private final ValidationContext validationContext;

    public MainValidationTraversal(GraphQLSchema graphQLSchema,
                                   Document document,
                                   String operationName,
                                   RulesVisitor rulesVisitor,
                                   ValidationContext validationContext) {
        this.schema = graphQLSchema;
        this.document = document;
        this.rulesVisitor = rulesVisitor;
        this.validationContext = validationContext;
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
        GraphQLObjectType rootType = getRootType(definition);
        overlappingFields.visitOperationDefinition(definition, rootType);
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

    private GraphQLObjectType getRootType(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return schema.getMutationType();
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            return schema.getQueryType();
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return schema.getSubscriptionType();
        } else {
            return Assert.assertShouldNeverHappen();
        }
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
        overlappingFields.visitField(field);
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
//        followFragmentSpread(fragmentSpread);
        path.remove(path.size() - 1);
        rulesVisitor.leave(fragmentSpread, path);
    }

    private void followFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
        if (fragmentDefinition != null && !path.contains(fragmentDefinition)) {
            visitFragmentDefinition(fragmentDefinition);
        }
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


}

