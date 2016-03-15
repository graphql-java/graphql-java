package graphql.parser;


import graphql.ShouldNotHappenException;
import graphql.language.*;
import graphql.parser.antlr.GraphqlBaseVisitor;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

public class GraphqlAntlrToLanguage extends GraphqlBaseVisitor<Void> {

    Document result;

    private enum ContextProperty {
        OperationDefinition,
        FragmentDefinition,
        Field,
        InlineFragment,
        FragmentSpread,
        SelectionSet,
        VariableDefinition,
        ListType,
        NonNullType,
        Directive
    }

    static class ContextEntry {
        ContextProperty contextProperty;
        Object value;

        public ContextEntry(ContextProperty contextProperty, Object value) {
            this.contextProperty = contextProperty;
            this.value = value;
        }
    }

    private Deque<ContextEntry> contextStack = new ArrayDeque<ContextEntry>();


    private void addContextProperty(ContextProperty contextProperty, Object value) {

        switch (contextProperty) {
            case SelectionSet:
                newSelectionSet((SelectionSet) value);
                break;
            case Field:
                newField((Field) value);
                break;
        }
        contextStack.addFirst(new ContextEntry(contextProperty, value));
    }

    private void popContext() {
        contextStack.removeFirst();
    }

    private Object getFromContextStack(ContextProperty contextProperty) {
        return getFromContextStack(contextProperty, false);
    }

    private Object getFromContextStack(ContextProperty contextProperty, boolean required) {
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == contextProperty) {
                return contextEntry.value;
            }
        }
        if (required) throw new RuntimeException("not found" + contextProperty);
        return null;
    }

    private void newSelectionSet(SelectionSet selectionSet) {
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.Field) {
                ((Field) contextEntry.value).setSelectionSet(selectionSet);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.OperationDefinition) {
                ((OperationDefinition) contextEntry.value).setSelectionSet(selectionSet);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FragmentDefinition) {
                ((FragmentDefinition) contextEntry.value).setSelectionSet(selectionSet);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InlineFragment) {
                ((InlineFragment) contextEntry.value).setSelectionSet(selectionSet);
                break;
            }
        }
    }

    private void newField(Field field) {
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(field);
    }


    @Override
    public Void visitDocument(GraphqlParser.DocumentContext ctx) {
        result = new Document();
        newNode(result, ctx);
        return super.visitDocument(ctx);
    }

    @Override
    public Void visitOperationDefinition(GraphqlParser.OperationDefinitionContext ctx) {
        OperationDefinition operationDefinition;
        operationDefinition = new OperationDefinition();
        newNode(operationDefinition, ctx);
        newNode(operationDefinition, ctx);
        if (ctx.operationType() == null) {
            operationDefinition.setOperation(OperationDefinition.Operation.QUERY);
        } else {
            operationDefinition.setOperation(parseOperation(ctx.operationType()));
        }
        if (ctx.NAME() != null) {
            operationDefinition.setName(ctx.NAME().getText());
        }
        result.getDefinitions().add(operationDefinition);
        addContextProperty(ContextProperty.OperationDefinition, operationDefinition);
        super.visitOperationDefinition(ctx);
        popContext();

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
    public Void visitFragmentSpread(GraphqlParser.FragmentSpreadContext ctx) {
        FragmentSpread fragmentSpread = new FragmentSpread(ctx.fragmentName().getText());
        newNode(fragmentSpread, ctx);
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(fragmentSpread);
        addContextProperty(ContextProperty.FragmentSpread, fragmentSpread);
        super.visitFragmentSpread(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitVariableDefinition(GraphqlParser.VariableDefinitionContext ctx) {
        VariableDefinition variableDefinition = new VariableDefinition();
        newNode(variableDefinition, ctx);
        variableDefinition.setName(ctx.variable().NAME().getText());
        if (ctx.defaultValue() != null) {
            Value value = getValue(ctx.defaultValue().value());
            variableDefinition.setDefaultValue(value);
        }
        OperationDefinition operationDefinition = (OperationDefinition) getFromContextStack(ContextProperty.OperationDefinition);
        operationDefinition.getVariableDefinitions().add(variableDefinition);

        addContextProperty(ContextProperty.VariableDefinition, variableDefinition);
        super.visitVariableDefinition(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx) {
        FragmentDefinition fragmentDefinition = new FragmentDefinition();
        newNode(fragmentDefinition, ctx);
        fragmentDefinition.setName(ctx.fragmentName().getText());
        fragmentDefinition.setTypeCondition(new TypeName(ctx.typeCondition().getText()));
        addContextProperty(ContextProperty.FragmentDefinition, fragmentDefinition);
        result.getDefinitions().add(fragmentDefinition);
        super.visitFragmentDefinition(ctx);
        popContext();
        return null;
    }


    @Override
    public Void visitSelectionSet(GraphqlParser.SelectionSetContext ctx) {
        SelectionSet newSelectionSet = new SelectionSet();
        newNode(newSelectionSet, ctx);
        addContextProperty(ContextProperty.SelectionSet, newSelectionSet);
        super.visitSelectionSet(ctx);
        popContext();
        return null;
    }


    @Override
    public Void visitField(GraphqlParser.FieldContext ctx) {
        Field newField = new Field();
        newNode(newField, ctx);
        newField.setName(ctx.NAME().getText());
        if (ctx.alias() != null) {
            newField.setAlias(ctx.alias().NAME().getText());
        }
        addContextProperty(ContextProperty.Field, newField);
        super.visitField(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitTypeName(GraphqlParser.TypeNameContext ctx) {
        TypeName typeName = new TypeName(ctx.NAME().getText());
        newNode(typeName, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.value instanceof ListType) {
                ((ListType) contextEntry.value).setType(typeName);
                break;
            }
            if (contextEntry.value instanceof NonNullType) {
                ((NonNullType) contextEntry.value).setType(typeName);
                break;
            }
            if (contextEntry.value instanceof VariableDefinition) {
                ((VariableDefinition) contextEntry.value).setType(typeName);
                break;
            }
        }
        return super.visitTypeName(ctx);
    }

    @Override
    public Void visitNonNullType(GraphqlParser.NonNullTypeContext ctx) {
        NonNullType nonNullType = new NonNullType();
        newNode(nonNullType, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.value instanceof ListType) {
                ((ListType) contextEntry.value).setType(nonNullType);
                break;
            }
            if (contextEntry.value instanceof VariableDefinition) {
                ((VariableDefinition) contextEntry.value).setType(nonNullType);
                break;
            }
        }
        addContextProperty(ContextProperty.NonNullType, nonNullType);
        super.visitNonNullType(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitListType(GraphqlParser.ListTypeContext ctx) {
        ListType listType = new ListType();
        newNode(listType, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.value instanceof ListType) {
                ((ListType) contextEntry.value).setType(listType);
                break;
            }
            if (contextEntry.value instanceof NonNullType) {
                ((NonNullType) contextEntry.value).setType(listType);
                break;
            }
            if (contextEntry.value instanceof VariableDefinition) {
                ((VariableDefinition) contextEntry.value).setType(listType);
                break;
            }
        }
        addContextProperty(ContextProperty.ListType, listType);
        super.visitListType(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitArgument(GraphqlParser.ArgumentContext ctx) {
        Argument argument = new Argument(ctx.NAME().getText(), getValue(ctx.valueWithVariable()));
        newNode(argument, ctx);
        if (getFromContextStack(ContextProperty.Directive, false) != null) {
            ((Directive) getFromContextStack(ContextProperty.Directive)).getArguments().add(argument);
        } else {
            Field field = (Field) getFromContextStack(ContextProperty.Field);
            field.getArguments().add(argument);
        }
        return super.visitArgument(ctx);
    }

    @Override
    public Void visitInlineFragment(GraphqlParser.InlineFragmentContext ctx) {
        InlineFragment inlineFragment = new InlineFragment(new TypeName(ctx.typeCondition().getText()));
        newNode(inlineFragment, ctx);
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(inlineFragment);
        addContextProperty(ContextProperty.InlineFragment, inlineFragment);
        super.visitInlineFragment(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitDirective(GraphqlParser.DirectiveContext ctx) {
        Directive directive = new Directive(ctx.NAME().getText());
        newNode(directive, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.Field) {
                ((Field) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FragmentDefinition) {
                ((FragmentDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FragmentSpread) {
                ((FragmentSpread) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InlineFragment) {
                ((InlineFragment) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.OperationDefinition) {
                ((OperationDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            }
        }
        addContextProperty(ContextProperty.Directive, directive);
        super.visitDirective(ctx);
        popContext();
        return null;
    }

    private Value getValue(GraphqlParser.ValueWithVariableContext ctx) {
        if (ctx.IntValue() != null) {
            IntValue intValue = new IntValue(new BigInteger(ctx.IntValue().getText()));
            newNode(intValue, ctx);
            return intValue;
        } else if (ctx.FloatValue() != null) {
            FloatValue floatValue = new FloatValue(new BigDecimal(ctx.FloatValue().getText()));
            newNode(floatValue, ctx);
            return floatValue;
        } else if (ctx.BooleanValue() != null) {
            BooleanValue booleanValue = new BooleanValue(Boolean.parseBoolean(ctx.BooleanValue().getText()));
            newNode(booleanValue, ctx);
            return booleanValue;
        } else if (ctx.StringValue() != null) {
            StringValue stringValue = new StringValue(trimQuotes(ctx.StringValue().getText()));
            newNode(stringValue, ctx);
            return stringValue;
        } else if (ctx.enumValue() != null) {
            EnumValue enumValue = new EnumValue(ctx.enumValue().getText());
            newNode(enumValue, ctx);
            return enumValue;
        } else if (ctx.arrayValueWithVariable() != null) {
            ArrayValue arrayValue = new ArrayValue();
            newNode(arrayValue, ctx);
            for (GraphqlParser.ValueWithVariableContext valueWithVariableContext : ctx.arrayValueWithVariable().valueWithVariable()) {
                arrayValue.getValues().add(getValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValueWithVariable() != null) {
            ObjectValue objectValue = new ObjectValue();
            newNode(objectValue, ctx);
            for (GraphqlParser.ObjectFieldWithVariableContext objectFieldWithVariableContext :
                    ctx.objectValueWithVariable().objectFieldWithVariable()) {
                ObjectField objectField = new ObjectField(objectFieldWithVariableContext.NAME().getText(), getValue(objectFieldWithVariableContext.valueWithVariable()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        } else if (ctx.variable() != null) {
            VariableReference variableReference = new VariableReference(ctx.variable().NAME().getText());
            newNode(variableReference, ctx);
            return variableReference;
        }
        throw new ShouldNotHappenException();
    }

    private Value getValue(GraphqlParser.ValueContext ctx) {
        if (ctx.IntValue() != null) {
            IntValue intValue = new IntValue(new BigInteger(ctx.IntValue().getText()));
            newNode(intValue, ctx);
            return intValue;
        } else if (ctx.FloatValue() != null) {
            FloatValue floatValue = new FloatValue(new BigDecimal(ctx.FloatValue().getText()));
            newNode(floatValue, ctx);
            return floatValue;
        } else if (ctx.BooleanValue() != null) {
            BooleanValue booleanValue = new BooleanValue(Boolean.parseBoolean(ctx.BooleanValue().getText()));
            newNode(booleanValue, ctx);
            return booleanValue;
        } else if (ctx.StringValue() != null) {
            StringValue stringValue = new StringValue(trimQuotes(ctx.StringValue().getText()));
            newNode(stringValue, ctx);
            return stringValue;
        } else if (ctx.enumValue() != null) {
            EnumValue enumValue = new EnumValue(ctx.enumValue().getText());
            newNode(enumValue, ctx);
            return enumValue;
        } else if (ctx.arrayValue() != null) {
            ArrayValue arrayValue = new ArrayValue();
            newNode(arrayValue, ctx);
            for (GraphqlParser.ValueContext valueWithVariableContext : ctx.arrayValue().value()) {
                arrayValue.getValues().add(getValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValue() != null) {
            ObjectValue objectValue = new ObjectValue();
            newNode(objectValue, ctx);
            for (GraphqlParser.ObjectFieldContext objectFieldContext :
                    ctx.objectValue().objectField()) {
                ObjectField objectField = new ObjectField(objectFieldContext.NAME().getText(), getValue(objectFieldContext.value()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        }
        throw new ShouldNotHappenException();
    }


    private String trimQuotes(String string) {
        return string.substring(1, string.length() - 1);
    }

    private void newNode(AbstractNode abstractNode, ParserRuleContext parserRuleContext) {
        abstractNode.setSourceLocation(getSourceLocation(parserRuleContext));
    }

    private SourceLocation getSourceLocation(ParserRuleContext parserRuleContext) {
        return new SourceLocation(parserRuleContext.getStart().getLine(), parserRuleContext.getStart().getCharPositionInLine() + 1);
    }

}
