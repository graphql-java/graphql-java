package graphql.parser;


import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiTypeLoader;
import graphql.language.*;
import graphql.parser.antlr.GraphqlBaseVisitor;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.misc.NotNull;

import java.math.BigDecimal;
import java.util.*;

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

    private Deque<ContextEntry> contextStack = new ArrayDeque<>();


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
    public Void visitFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx) {
        FragmentSpread fragmentSpread = new FragmentSpread(ctx.fragmentName().getText());
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(fragmentSpread);
        addContextProperty(ContextProperty.FragmentSpread, fragmentSpread);
        super.visitFragmentSpread(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx) {
        VariableDefinition variableDefinition = new VariableDefinition();
        variableDefinition.setName(ctx.variable().NAME().getText());
        if (ctx.defaultValue() != null) {
            Value value = getValue(ctx.defaultValue().value());
            variableDefinition.setDefaultValue(value);
        }
        OperationDefinition operationDefiniton = (OperationDefinition) getFromContextStack(ContextProperty.OperationDefinition);
        operationDefiniton.getVariableDefinitions().add(variableDefinition);

        addContextProperty(ContextProperty.VariableDefinition, variableDefinition);
        super.visitVariableDefinition(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx) {
        FragmentDefinition fragmentDefinition = new FragmentDefinition();
        fragmentDefinition.setName(ctx.fragmentName().getText());
        fragmentDefinition.setTypeCondition(new TypeName(ctx.typeCondition().getText()));
        addContextProperty(ContextProperty.FragmentDefinition, fragmentDefinition);
        result.getDefinitions().add(fragmentDefinition);
        super.visitFragmentDefinition(ctx);
        popContext();
        return null;
    }


    @Override
    public Void visitSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx) {
        SelectionSet newSelectionSet = new SelectionSet();
        addContextProperty(ContextProperty.SelectionSet, newSelectionSet);
        super.visitSelectionSet(ctx);
        popContext();
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
        addContextProperty(ContextProperty.Field, newField);
        super.visitField(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitTypeName(@NotNull GraphqlParser.TypeNameContext ctx) {

        for (ContextEntry contextEntry : contextStack) {
            TypeName typeName = new TypeName(ctx.NAME().getText());
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
    public Void visitNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx) {
        NonNullType nonNullType = new NonNullType();
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
    public Void visitListType(@NotNull GraphqlParser.ListTypeContext ctx) {
        ListType listType = new ListType();
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
    public Void visitArgument(@NotNull GraphqlParser.ArgumentContext ctx) {
        Argument argument = new Argument(ctx.NAME().getText(), getValue(ctx.valueWithVariable()));
        if (getFromContextStack(ContextProperty.Directive, false) != null) {
            ((Directive) getFromContextStack(ContextProperty.Directive)).getArguments().add(argument);
        } else {
            Field field = (Field) getFromContextStack(ContextProperty.Field);
            field.getArguments().add(argument);
        }
        return super.visitArgument(ctx);
    }

    @Override
    public Void visitInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx) {
        InlineFragment inlineFragment = new InlineFragment(new TypeName(ctx.typeCondition().getText()));
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(inlineFragment);
        addContextProperty(ContextProperty.InlineFragment, inlineFragment);
        super.visitInlineFragment(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitDirective(@NotNull GraphqlParser.DirectiveContext ctx) {
        Directive directive = new Directive(ctx.NAME().getText());
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.Field) {
                ((Field) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FragmentDefinition) {
                ((Field) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FragmentSpread) {
                ((FragmentSpread) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InlineFragment) {
                ((InlineFragment) contextEntry.value).getDirectives().add(directive);
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
            IntValue intValue = new IntValue(Integer.parseInt(ctx.IntValue().getText()));
            return intValue;
        } else if (ctx.FloatValue() != null) {
            FloatValue floatValue = new FloatValue(new BigDecimal(ctx.FloatValue().getText()));
            return floatValue;
        } else if (ctx.BooleanValue() != null) {
            BooleanValue booleanValue = new BooleanValue(Boolean.parseBoolean(ctx.BooleanValue().getText()));
            return booleanValue;
        } else if (ctx.StringValue() != null) {
            StringValue stringValue = new StringValue(trimQuotes(ctx.StringValue().getText()));
            return stringValue;
        } else if (ctx.enumValue() != null) {
            EnumValue enumValue = new EnumValue(ctx.enumValue().getText());
            return enumValue;
        } else if (ctx.arrayValueWithVariable() != null) {
            ArrayValue arrayValue = new ArrayValue();
            for (GraphqlParser.ValueWithVariableContext valueWithVariableContext : ctx.arrayValueWithVariable().valueWithVariable()) {
                arrayValue.getValues().add(getValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValueWithVariable() != null) {
            ObjectValue objectValue = new ObjectValue();
            for (GraphqlParser.ObjectFieldWithVariableContext objectFieldWithVariableContext :
                    ctx.objectValueWithVariable().objectFieldWithVariable()) {
                objectValue.getValueMap().put(objectFieldWithVariableContext.NAME().getText(), getValue(objectFieldWithVariableContext.valueWithVariable()));
            }
            return objectValue;
        } else if (ctx.variable() != null) {
            VariableReference variableReference = new VariableReference(ctx.variable().NAME().getText());
            return variableReference;
        }
        throw new RuntimeException();
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
            StringValue stringValue = new StringValue(trimQuotes(ctx.StringValue().getText()));
            return stringValue;
        } else if (ctx.enumValue() != null) {
            EnumValue enumValue = new EnumValue(ctx.enumValue().getText());
            return enumValue;
        } else if (ctx.arrayValue() != null) {
            ArrayValue arrayValue = new ArrayValue();
            for (GraphqlParser.ValueContext valueWithVariableContext : ctx.arrayValue().value()) {
                arrayValue.getValues().add(getValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValue() != null) {
            ObjectValue objectValue = new ObjectValue();
            for (GraphqlParser.ObjectFieldContext objectFieldContext :
                    ctx.objectValue().objectField()) {
                objectValue.getValueMap().put(objectFieldContext.NAME().getText(), getValue(objectFieldContext.value()));
            }
            return objectValue;
        }
        throw new RuntimeException();
    }


    private String trimQuotes(String string) {
        return string.substring(1, string.length() - 1);
    }

}
