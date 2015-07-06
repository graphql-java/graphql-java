package graphql.parser;


import graphql.language.*;
import graphql.parser.antlr.GraphqlBaseListener;
import graphql.parser.antlr.GraphqlBaseVisitor;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.misc.NotNull;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphqlAntlrToLanguage extends GraphqlBaseVisitor<Void> {

    Document result;

    private enum ContextProperty {
        OperationDefinition,
        FragmentDefinition,
        Field,
        InlineFragment,
        SelectionSet,
        VariableDefinition,
        Argument
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
        } else {
            operationDefinition.setOperation(parseOperation(ctx.operationType()));
        }
        if (ctx.NAME() != null) {
            operationDefinition.setName(ctx.NAME().getText());
        }
        result.getDefinitions().add(operationDefinition);
        Object oldValue = setContextProperty(ContextProperty.OperationDefinition, operationDefinition);
        super.visitOperationDefinition(ctx);
        restoreContext(ContextProperty.OperationDefinition, oldValue);

        return null;
    }

    private OperationDefinition.Operation parseOperation(GraphqlParser.OperationTypeContext operationTypeContext) {
        if (operationTypeContext.getText().equals("query")) {
            return OperationDefinition.Operation.QUERY;
        } else if (operationTypeContext.getText().equals("mutation")) {
            return OperationDefinition.Operation.MUTATION;
        } else {
            throw new RuntimeException();
        }

    }


    @Override
    public Void visitVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx) {
        VariableDefinition variableDefinition = new VariableDefinition();
        variableDefinition.setName(ctx.variable().NAME().getText());
        OperationDefinition operationDefiniton = (OperationDefinition) context.get(ContextProperty.OperationDefinition);
        operationDefiniton.getVariableDefinitions().add(variableDefinition);
        Object oldValue = setContextProperty(ContextProperty.VariableDefinition, variableDefinition);
        super.visitVariableDefinition(ctx);
        restoreContext(ContextProperty.VariableDefinition, oldValue);
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
        Object oldValue = setContextProperty(ContextProperty.Field, newField);
        super.visitField(ctx);
        restoreContext(ContextProperty.Field, oldValue);
        return null;
    }

    @Override
    public Void visitTypeName(@NotNull GraphqlParser.TypeNameContext ctx) {
        NamedType namedType = new NamedType(ctx.NAME().getText());
        if (context.get(ContextProperty.VariableDefinition) != null) {
            ((VariableDefinition) context.get(ContextProperty.VariableDefinition)).setType(namedType);
        }
        return super.visitTypeName(ctx);
    }

    @Override
    public Void visitArgument(@NotNull GraphqlParser.ArgumentContext ctx) {
        Argument argument = new Argument(ctx.NAME().getText(), getValue(ctx.value()));
        Field field = (Field) context.get(ContextProperty.Field);
        field.getArguments().add(argument);
        return super.visitArgument(ctx);
    }

    private Value getValue(GraphqlParser.ValueContext ctx) {
        if (ctx.IntValue() != null) {
            IntValue intValue = new IntValue(Integer.parseInt(ctx.IntValue().getText()));
            return intValue;
        } else if (ctx.FloatValue() != null) {
            FloatValue floatValue = new FloatValue(new BigDecimal(ctx.FloatValue().getText()));
            return floatValue;
        } else if (ctx.BooleanValue() != null) {
            BooleanValue booleanValue = new BooleanValue(Boolean.parseBoolean(ctx.BooleanValue().getText()));
            return booleanValue;
        } else if (ctx.StringValue() != null) {
            StringValue booleanValue = new StringValue(trimQuotes(ctx.StringValue().getText()));
            return booleanValue;
        }
        return null;
    }

    private String trimQuotes(String string) {
        return string.substring(1, string.length() - 1);
    }

}
