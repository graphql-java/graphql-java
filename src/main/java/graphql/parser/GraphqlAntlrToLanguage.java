package graphql.parser;


import graphql.Assert;
import graphql.Internal;
import graphql.language.AbstractNode;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.Definition;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.antlr.GraphqlBaseVisitor;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static graphql.language.NullValue.Null;
import static graphql.parser.StringValueParsing.parseSingleQuotedString;
import static graphql.parser.StringValueParsing.parseTripleQuotedString;

@Internal
public class GraphqlAntlrToLanguage extends GraphqlBaseVisitor<Void> {

    private final CommonTokenStream tokens;
    private Document result;

    public GraphqlAntlrToLanguage(CommonTokenStream tokens) {
        this.tokens = tokens;
    }

    protected enum ContextProperty {
        OperationDefinition,
        FragmentDefinition,
        Field,
        InlineFragment,
        FragmentSpread,
        SelectionSet,
        VariableDefinition,
        ListType,
        NonNullType,
        Directive,
        EnumTypeDefinition,
        ObjectTypeDefinition,
        InputObjectTypeDefinition,
        ScalarTypeDefinition,
        UnionTypeDefinition,
        InterfaceTypeDefinition,
        EnumValueDefinition,
        FieldDefinition,
        InputValueDefinition,
        SchemaDefinition,
        OperationTypeDefinition,
        DirectiveDefinition,
    }

    protected static class ContextEntry {
        public final ContextProperty contextProperty;
        public final Object value;

        public ContextEntry(ContextProperty contextProperty, Object value) {
            this.contextProperty = contextProperty;
            this.value = value;
        }
    }

    private final Deque<ContextEntry> contextStack = new ArrayDeque<>();


    protected void addContextProperty(ContextProperty contextProperty, Object value) {
        contextStack.addFirst(new ContextEntry(contextProperty, value));
    }

    protected void popContext() {
        contextStack.removeFirst();
    }

    protected Object getFromContextStack(ContextProperty contextProperty) {
        return getFromContextStack(contextProperty, false);
    }

    @SuppressWarnings("SameParameterValue")
    protected Object getFromContextStack(ContextProperty contextProperty, boolean required) {
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == contextProperty) {
                return contextEntry.value;
            }
        }
        if (required) {
            Assert.assertShouldNeverHappen("not found %s", contextProperty);
        }
        return null;
    }

    //MARKER START: Here GraphqlOperation.g4 specific methods begin



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
        if (ctx.operationType() == null) {
            operationDefinition.setOperation(OperationDefinition.Operation.QUERY);
        } else {
            operationDefinition.setOperation(parseOperation(ctx.operationType()));
        }
        if (ctx.name() != null) {
            operationDefinition.setName(ctx.name().getText());
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
        } else if (operationTypeContext.getText().equals("subscription")) {
            return OperationDefinition.Operation.SUBSCRIPTION;
        } else {
            return Assert.assertShouldNeverHappen("InternalError: unknown operationTypeContext=%s", operationTypeContext.getText());
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
        variableDefinition.setName(ctx.variable().name().getText());
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
        fragmentDefinition.setTypeCondition(new TypeName(ctx.typeCondition().typeName().getText()));
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
        newSelectionSet(newSelectionSet);
        addContextProperty(ContextProperty.SelectionSet, newSelectionSet);
        super.visitSelectionSet(ctx);
        popContext();
        return null;
    }


    @Override
    public Void visitField(GraphqlParser.FieldContext ctx) {
        Field newField = new Field();
        newNode(newField, ctx);
        newField.setName(ctx.name().getText());
        if (ctx.alias() != null) {
            newField.setAlias(ctx.alias().name().getText());
        }
        newField(newField);
        addContextProperty(ContextProperty.Field, newField);
        super.visitField(ctx);
        popContext();
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
    public Void visitInlineFragment(GraphqlParser.InlineFragmentContext ctx) {
        TypeName typeName = ctx.typeCondition() != null ? new TypeName(ctx.typeCondition().typeName().getText()) : null;
        InlineFragment inlineFragment = new InlineFragment(typeName);
        newNode(inlineFragment, ctx);
        ((SelectionSet) getFromContextStack(ContextProperty.SelectionSet)).getSelections().add(inlineFragment);
        addContextProperty(ContextProperty.InlineFragment, inlineFragment);
        super.visitInlineFragment(ctx);
        popContext();
        return null;
    }

    //MARKER END: Here GraphqlOperation.g4 specific methods end

    @Override
    public Void visitTypeName(GraphqlParser.TypeNameContext ctx) {
        TypeName typeName = new TypeName(ctx.name().getText());
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
            if (contextEntry.value instanceof FieldDefinition) {
                ((FieldDefinition) contextEntry.value).setType(typeName);
                break;
            }
            if (contextEntry.value instanceof InputValueDefinition) {
                ((InputValueDefinition) contextEntry.value).setType(typeName);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.ObjectTypeDefinition) {
                ((ObjectTypeDefinition) contextEntry.value).getImplements().add(typeName);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.UnionTypeDefinition) {
                ((UnionTypeDefinition) contextEntry.value).getMemberTypes().add(typeName);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.OperationTypeDefinition) {
                ((OperationTypeDefinition) contextEntry.value).setType(typeName);
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
            if (contextEntry.value instanceof FieldDefinition) {
                ((FieldDefinition) contextEntry.value).setType(nonNullType);
                break;
            }
            if (contextEntry.value instanceof InputValueDefinition) {
                ((InputValueDefinition) contextEntry.value).setType(nonNullType);
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
            if (contextEntry.value instanceof FieldDefinition) {
                ((FieldDefinition) contextEntry.value).setType(listType);
                break;
            }
            if (contextEntry.value instanceof InputValueDefinition) {
                ((InputValueDefinition) contextEntry.value).setType(listType);
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
        Argument argument = new Argument(ctx.name().getText(), getValue(ctx.valueWithVariable()));
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
    public Void visitDirective(GraphqlParser.DirectiveContext ctx) {
        Directive directive = new Directive(ctx.name().getText());
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
            } else if (contextEntry.contextProperty == ContextProperty.EnumValueDefinition) {
                ((EnumValueDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.FieldDefinition) {
                ((FieldDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InputValueDefinition) {
                ((InputValueDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InterfaceTypeDefinition) {
                ((InterfaceTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.EnumTypeDefinition) {
                ((EnumTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.ObjectTypeDefinition) {
                ((ObjectTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.ScalarTypeDefinition) {
                ((ScalarTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.UnionTypeDefinition) {
                ((UnionTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.InputObjectTypeDefinition) {
                ((InputObjectTypeDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            } else if (contextEntry.contextProperty == ContextProperty.SchemaDefinition) {
                ((SchemaDefinition) contextEntry.value).getDirectives().add(directive);
                break;
            }
        }
        addContextProperty(ContextProperty.Directive, directive);
        super.visitDirective(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitSchemaDefinition(GraphqlParser.SchemaDefinitionContext ctx) {
        SchemaDefinition def = new SchemaDefinition();
        newNode(def, ctx);
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.SchemaDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitOperationTypeDefinition(GraphqlParser.OperationTypeDefinitionContext ctx) {
        OperationTypeDefinition def = new OperationTypeDefinition(ctx.operationType().getText());
        newNode(def, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.SchemaDefinition) {
                ((SchemaDefinition) contextEntry.value).getOperationTypeDefinitions().add(def);
                break;
            }
        }
        addContextProperty(ContextProperty.OperationTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitScalarTypeDefinition(GraphqlParser.ScalarTypeDefinitionContext ctx) {
        ScalarTypeDefinition def = new ScalarTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.ScalarTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitObjectTypeDefinition(GraphqlParser.ObjectTypeDefinitionContext ctx) {
        ObjectTypeDefinition def = new ObjectTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.ObjectTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }


    @Override
    public Void visitFieldDefinition(GraphqlParser.FieldDefinitionContext ctx) {
        FieldDefinition def = new FieldDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.InterfaceTypeDefinition) {
                ((InterfaceTypeDefinition) contextEntry.value).getFieldDefinitions().add(def);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.ObjectTypeDefinition) {
                ((ObjectTypeDefinition) contextEntry.value).getFieldDefinitions().add(def);
                break;
            }
        }
        addContextProperty(ContextProperty.FieldDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitInputValueDefinition(GraphqlParser.InputValueDefinitionContext ctx) {
        InputValueDefinition def = new InputValueDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        if (ctx.defaultValue() != null) {
            def.setDefaultValue(getValue(ctx.defaultValue().value()));
        }
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.FieldDefinition) {
                ((FieldDefinition) contextEntry.value).getInputValueDefinitions().add(def);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.InputObjectTypeDefinition) {
                ((InputObjectTypeDefinition) contextEntry.value).getInputValueDefinitions().add(def);
                break;
            }
            if (contextEntry.contextProperty == ContextProperty.DirectiveDefinition) {
                ((DirectiveDefinition) contextEntry.value).getInputValueDefinitions().add(def);
                break;
            }
        }
        addContextProperty(ContextProperty.InputValueDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitInterfaceTypeDefinition(GraphqlParser.InterfaceTypeDefinitionContext ctx) {
        InterfaceTypeDefinition def = new InterfaceTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.InterfaceTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitUnionTypeDefinition(GraphqlParser.UnionTypeDefinitionContext ctx) {
        UnionTypeDefinition def = new UnionTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.UnionTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitEnumTypeDefinition(GraphqlParser.EnumTypeDefinitionContext ctx) {
        EnumTypeDefinition def = new EnumTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.EnumTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitEnumValueDefinition(GraphqlParser.EnumValueDefinitionContext ctx) {
        EnumValueDefinition def = new EnumValueDefinition(ctx.enumValue().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.EnumTypeDefinition) {
                ((EnumTypeDefinition) contextEntry.value).getEnumValueDefinitions().add(def);
                break;
            }
        }
        addContextProperty(ContextProperty.EnumValueDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitInputObjectTypeDefinition(GraphqlParser.InputObjectTypeDefinitionContext ctx) {
        InputObjectTypeDefinition def = new InputObjectTypeDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.InputObjectTypeDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    private Void extensionTypeImpl(ParserRuleContext ctx, Definition def, ContextProperty ctxProperty) {
        newNode((AbstractNode) def, ctx);
        result.getDefinitions().add(def);
        addContextProperty(ctxProperty, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitObjectTypeExtensionDefinition(GraphqlParser.ObjectTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new ObjectTypeExtensionDefinition(ctx.name().getText()), ContextProperty.ObjectTypeDefinition);
    }

    @Override
    public Void visitInterfaceTypeExtensionDefinition(GraphqlParser.InterfaceTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new InterfaceTypeExtensionDefinition(ctx.name().getText()), ContextProperty.InterfaceTypeDefinition);
    }

    @Override
    public Void visitUnionTypeExtensionDefinition(GraphqlParser.UnionTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new UnionTypeExtensionDefinition(ctx.name().getText()), ContextProperty.UnionTypeDefinition);
    }

    @Override
    public Void visitEnumTypeExtensionDefinition(GraphqlParser.EnumTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new EnumTypeExtensionDefinition(ctx.name().getText()), ContextProperty.EnumTypeDefinition);
    }

    @Override
    public Void visitScalarTypeExtensionDefinition(GraphqlParser.ScalarTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new ScalarTypeExtensionDefinition(ctx.name().getText()), ContextProperty.ScalarTypeDefinition);
    }

    @Override
    public Void visitInputObjectTypeExtensionDefinition(GraphqlParser.InputObjectTypeExtensionDefinitionContext ctx) {
        return extensionTypeImpl(ctx, new InputObjectTypeExtensionDefinition(ctx.name().getText()), ContextProperty.InputObjectTypeDefinition);
    }

    @Override
    public Void visitDirectiveDefinition(GraphqlParser.DirectiveDefinitionContext ctx) {
        DirectiveDefinition def = new DirectiveDefinition(ctx.name().getText());
        newNode(def, ctx);
        def.setDescription(newDescription(ctx.description()));
        result.getDefinitions().add(def);
        addContextProperty(ContextProperty.DirectiveDefinition, def);
        super.visitChildren(ctx);
        popContext();
        return null;
    }

    @Override
    public Void visitDirectiveLocation(GraphqlParser.DirectiveLocationContext ctx) {
        DirectiveLocation def = new DirectiveLocation(ctx.name().getText());
        newNode(def, ctx);
        for (ContextEntry contextEntry : contextStack) {
            if (contextEntry.contextProperty == ContextProperty.DirectiveDefinition) {
                ((DirectiveDefinition) contextEntry.value).getDirectiveLocations().add(def);
                break;
            }
        }
        super.visitChildren(ctx);
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
        } else if (ctx.NullValue() != null) {
            newNode(Null, ctx);
            return Null;
        } else if (ctx.stringValue() != null) {
            StringValue stringValue = new StringValue(quotedString(ctx.stringValue()));
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
                ObjectField objectField = new ObjectField(objectFieldWithVariableContext.name().getText(), getValue(objectFieldWithVariableContext.valueWithVariable()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        } else if (ctx.variable() != null) {
            VariableReference variableReference = new VariableReference(ctx.variable().name().getText());
            newNode(variableReference, ctx);
            return variableReference;
        }
        return Assert.assertShouldNeverHappen();
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
        } else if (ctx.NullValue() != null) {
            newNode(Null, ctx);
            return Null;
        } else if (ctx.stringValue() != null) {
            StringValue stringValue = new StringValue(quotedString(ctx.stringValue()));
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
                ObjectField objectField = new ObjectField(objectFieldContext.name().getText(), getValue(objectFieldContext.value()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        }
        return Assert.assertShouldNeverHappen();
    }

    static String quotedString(GraphqlParser.StringValueContext ctx) {
        boolean multiLine = ctx.TripleQuotedStringValue() != null;
        String strText = ctx.getText();
        if (multiLine) {
            return parseTripleQuotedString(strText);
        } else {
            return parseSingleQuotedString(strText);
        }
    }


    private void newNode(AbstractNode abstractNode, ParserRuleContext parserRuleContext) {
        List<Comment> comments = getComments(parserRuleContext);
        if (!comments.isEmpty()) {
            abstractNode.setComments(comments);
        }

        abstractNode.setSourceLocation(getSourceLocation(parserRuleContext));
    }

    private Description newDescription(GraphqlParser.DescriptionContext descriptionCtx) {
        if (descriptionCtx == null) {
            return null;
        }
        GraphqlParser.StringValueContext stringValueCtx = descriptionCtx.stringValue();
        if (stringValueCtx == null) {
            return null;
        }
        boolean multiLine = stringValueCtx.TripleQuotedStringValue() != null;
        String content = stringValueCtx.getText();
        if (multiLine) {
            content = parseTripleQuotedString(content);
        } else {
            content = parseSingleQuotedString(content);
        }
        SourceLocation sourceLocation = getSourceLocation(descriptionCtx);
        return new Description(content, sourceLocation, multiLine);
    }


    private SourceLocation getSourceLocation(ParserRuleContext parserRuleContext) {
        Token startToken = parserRuleContext.getStart();
        String sourceName = startToken.getTokenSource().getSourceName();
        if (IntStream.UNKNOWN_SOURCE_NAME.equals(sourceName)) {
            // UNKNOWN_SOURCE_NAME is Antrl's way of indicating that no source name was given during parsing --
            // which is the case when queries and other operations are parsed. We don't want this hardcoded
            // '<unknown>' sourceName to leak to clients when the response is serialized as JSON, so we null it.
            sourceName = null;
        }
        return new SourceLocation(startToken.getLine(), startToken.getCharPositionInLine() + 1, sourceName);
    }

    private List<Comment> getComments(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        if (start != null) {
            int tokPos = start.getTokenIndex();
            List<Token> refChannel = tokens.getHiddenTokensToLeft(tokPos, 2);
            if (refChannel != null) {
                return getCommentOnChannel(refChannel);
            }
        }
        return Collections.emptyList();
    }


    private List<Comment> getCommentOnChannel(List<Token> refChannel) {
        List<Comment> comments = new ArrayList<>();
        for (Token refTok : refChannel) {
            String text = refTok.getText();
            // we strip the leading hash # character but we don't trim because we don't
            // know the "comment markup".  Maybe its space sensitive, maybe its not.  So
            // consumers can decide that
            if (text == null) {
                continue;
            }
            text = text.replaceFirst("^#", "");
            comments.add(new Comment(text, new SourceLocation(refTok.getLine(), refTok.getCharPositionInLine())));
        }
        return comments;
    }

    public Document getResult() {
        return result;
    }

    protected void setResult(Document result) {
        this.result = result;
    }

    public Deque<ContextEntry> getContextStack() {
        return contextStack;
    }
}
