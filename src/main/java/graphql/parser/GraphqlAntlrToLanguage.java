package graphql.parser;


import graphql.language.*;
import graphql.parser.antlr.GraphqlBaseListener;
import graphql.parser.antlr.GraphqlBaseVisitor;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class GraphqlAntlrToLanguage extends GraphqlBaseVisitor<Void> {

    Document result;

    private enum ContextProperty {
        OperationDefinition,
        FragmentDefinition,
        Field,
        InlineFragment,
        SelectionSet
    }

    private Map<ContextProperty, Object> context = new LinkedHashMap<>();


    private Object setContextProperty(ContextProperty contextProperty, Object value) {
        Object oldValue = context.get(contextProperty);
        context.put(contextProperty, value);
        switch (contextProperty) {
            case SelectionSet:
                newSelectionSet((SelectionSet) value);
                break;
            case Field:
                newField((Field) value);
                break;
        }
        return oldValue;
    }

    private void newSelectionSet(SelectionSet selectionSet) {
        if (context.get(ContextProperty.Field) != null) {
            ((Field) context.get(ContextProperty.Field)).setSelectionSet(selectionSet);
        } else if (context.get(ContextProperty.OperationDefinition) != null) {
            ((OperationDefinition) context.get(ContextProperty.OperationDefinition)).setSelectionSet(selectionSet);
        }

    }

    private void newField(Field field) {
        ((SelectionSet) context.get(ContextProperty.SelectionSet)).getSelections().add(field);
    }

    private void restoreContext(ContextProperty contextProperty, Object oldValue) {
        context.put(contextProperty, oldValue);
    }

    @Override
    public Void visitDocument(@NotNull GraphqlParser.DocumentContext ctx) {
        result = new Document();
        return super.visitDocument(ctx);
    }

    @Override
    public Void visitOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx) {
        OperationDefinition operationDefinition;
        operationDefinition = new OperationDefinition();
        if (ctx.operationType() == null) {
            operationDefinition.setOperation(OperationDefinition.Operation.QUERY);
        }
        result.getDefinitions().add(operationDefinition);
        Object oldValue = setContextProperty(ContextProperty.OperationDefinition, operationDefinition);
        super.visitOperationDefinition(ctx);
        restoreContext(ContextProperty.OperationDefinition, oldValue);

        return null;
    }

    @Override
    public Void visitFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx) {
        FragmentDefinition fragmentDefinition = new FragmentDefinition();
        Object oldValue = setContextProperty(ContextProperty.FragmentDefinition, fragmentDefinition);
        super.visitFragmentDefinition(ctx);
        restoreContext(ContextProperty.FragmentDefinition, oldValue);
        return null;
    }

    @Override
    public Void visitSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx) {
        SelectionSet newSelectionSet = new SelectionSet();
        Object oldValue = setContextProperty(ContextProperty.SelectionSet, newSelectionSet);
        super.visitSelectionSet(ctx);
        restoreContext(ContextProperty.SelectionSet, oldValue);
        return null;
    }

    @Override
    public Void visitSelection(@NotNull GraphqlParser.SelectionContext ctx) {
        return super.visitSelection(ctx);
    }

    @Override
    public Void visitField(@NotNull GraphqlParser.FieldContext ctx) {
        Field newField = new Field();
        newField.setName(ctx.NAME().getText());
        System.out.println("new field " + newField);
        Object oldValue = setContextProperty(ContextProperty.Field, newField);
        super.visitField(ctx);
        restoreContext(ContextProperty.Field, oldValue);
        return null;
    }
}
