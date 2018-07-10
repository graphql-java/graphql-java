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
import graphql.language.NodeBuilder;
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
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.NullValue.Null;
import static graphql.parser.StringValueParsing.parseSingleQuotedString;
import static graphql.parser.StringValueParsing.parseTripleQuotedString;

@Internal
public class GraphqlAntlrToLanguage2 {

    private final CommonTokenStream tokens;


    public GraphqlAntlrToLanguage2(CommonTokenStream tokens) {
        this.tokens = tokens;
    }

    //MARKER START: Here GraphqlOperation.g4 specific methods begin


    public Document createDocument(GraphqlParser.DocumentContext ctx) {
        Document.Builder document = Document.newDocument();
        newNode(document, ctx);
        document.definitions(ctx.definition().stream().map(definition -> createDefinition(definition))
                .collect(Collectors.toList()));
        return document.build();
    }

    public Definition createDefinition(GraphqlParser.DefinitionContext definitionContext) {
        if (definitionContext.operationDefinition() != null) {
            return createOperationDefinition(definitionContext.operationDefinition());
        } else if (definitionContext.fragmentDefinition() != null) {
            return createFragmentDefinition(definitionContext.fragmentDefinition());
        } else if (definitionContext.typeSystemDefinition() != null) {
            return createTypeSystemDefinition(definitionContext.typeSystemDefinition());
        } else {
            return assertShouldNeverHappen();
        }

    }

    private Definition createTypeSystemDefinition(GraphqlParser.TypeSystemDefinitionContext ctx) {
        if (ctx.schemaDefinition() != null) {
            return createSchemaDefinition(ctx.schemaDefinition());
        } else if (ctx.directiveDefinition() != null) {
            return createDirectiveDefinition(ctx.directiveDefinition());
        } else if (ctx.typeDefinition() != null) {
            return createTypeDefinition(ctx.typeDefinition());
        } else if (ctx.typeExtension() != null) {
            return createTypeExtension(ctx.typeExtension());
        } else {
            return assertShouldNeverHappen();
        }
    }

    private TypeDefinition createTypeExtension(GraphqlParser.TypeExtensionContext ctx) {
        if (ctx.enumTypeExtensionDefinition() != null) {
            return createEnumTypeExtensionDefinition(ctx.enumTypeExtensionDefinition());

        } else if (ctx.objectTypeExtensionDefinition() != null) {
            return createObjectTypeExtensionDefinition(ctx.objectTypeExtensionDefinition());

        } else if (ctx.inputObjectTypeExtensionDefinition() != null) {
            return createInputObjectTypeExtensionDefinition(ctx.inputObjectTypeExtensionDefinition());

        } else if (ctx.interfaceTypeExtensionDefinition() != null) {
            return createInterfaceTypeExtensionDefinition(ctx.interfaceTypeExtensionDefinition());

        } else if (ctx.scalarTypeExtensionDefinition() != null) {
            return createScalarTypeExtensionDefinition(ctx.scalarTypeExtensionDefinition());

        } else if (ctx.unionTypeExtensionDefinition() != null) {
            return createUnionTypeExtensionDefinition(ctx.unionTypeExtensionDefinition());
        } else {
            return assertShouldNeverHappen();
        }
    }

    private TypeDefinition createTypeDefinition(GraphqlParser.TypeDefinitionContext ctx) {
        if (ctx.enumTypeDefinition() != null) {
            return createEnumTypeDefinition(ctx.enumTypeDefinition());

        } else if (ctx.objectTypeDefinition() != null) {
            return createObjectTypeDefinition(ctx.objectTypeDefinition());

        } else if (ctx.inputObjectTypeDefinition() != null) {
            return createInputObjectTypeDefinition(ctx.inputObjectTypeDefinition());

        } else if (ctx.interfaceTypeDefinition() != null) {
            return createInterfaceTypeDefinition(ctx.interfaceTypeDefinition());

        } else if (ctx.scalarTypeDefinition() != null) {
            return createScalarTypeDefinition(ctx.scalarTypeDefinition());

        } else if (ctx.unionTypeDefinition() != null) {
            return createUnionTypeDefinition(ctx.unionTypeDefinition());

        } else {
            return assertShouldNeverHappen();
        }
    }


    public OperationDefinition createOperationDefinition(GraphqlParser.OperationDefinitionContext ctx) {
        OperationDefinition.Builder operationDefinition = OperationDefinition.newOperationDefinition();
        newNode(operationDefinition, ctx);
        if (ctx.operationType() == null) {
            operationDefinition.operation(OperationDefinition.Operation.QUERY);
        } else {
            operationDefinition.operation(parseOperation(ctx.operationType()));
        }
        if (ctx.name() != null) {
            operationDefinition.name(ctx.name().getText());
        }
        if (ctx.variableDefinitions() != null) {
            operationDefinition.variableDefinitions(createVariableDefinitions(ctx.variableDefinitions()));
        }
        if (ctx.selectionSet() != null) {
            operationDefinition.selectionSet(createSelectionSet(ctx.selectionSet()));
        }
        if (ctx.directives() != null) {
            operationDefinition.directives(createDirectives(ctx.directives()));
        }
        return operationDefinition.build();
    }

    private OperationDefinition.Operation parseOperation(GraphqlParser.OperationTypeContext operationTypeContext) {
        if (operationTypeContext.getText().equals("query")) {
            return OperationDefinition.Operation.QUERY;
        } else if (operationTypeContext.getText().equals("mutation")) {
            return OperationDefinition.Operation.MUTATION;
        } else if (operationTypeContext.getText().equals("subscription")) {
            return OperationDefinition.Operation.SUBSCRIPTION;
        } else {
            return assertShouldNeverHappen("InternalError: unknown operationTypeContext=%s", operationTypeContext.getText());
        }
    }

    public FragmentSpread createFragmentSpread(GraphqlParser.FragmentSpreadContext ctx) {
        FragmentSpread fragmentSpread = new FragmentSpread(ctx.fragmentName().getText());
        newNode(fragmentSpread, ctx);
        return fragmentSpread;
    }

    public List<VariableDefinition> createVariableDefinitions(GraphqlParser.VariableDefinitionsContext ctx) {
        return ctx.variableDefinition().stream().map(this::createVariableDefinition).collect(Collectors.toList());
    }

    private VariableDefinition createVariableDefinition(GraphqlParser.VariableDefinitionContext ctx) {
        VariableDefinition.Builder variableDefinition = VariableDefinition.newVariableDefinition();
        newNode(variableDefinition, ctx);
        variableDefinition.name(ctx.variable().name().getText());
        if (ctx.defaultValue() != null) {
            Value value = createValue(ctx.defaultValue().value());
            variableDefinition.defaultValue(value);
        }
        variableDefinition.type(createType(ctx.type()));
        return variableDefinition.build();

    }

    public FragmentDefinition createFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx) {
        FragmentDefinition.Builder fragmentDefinition = FragmentDefinition.newFragmentDefinition();
        newNode(fragmentDefinition, ctx);
        fragmentDefinition.name(ctx.fragmentName().getText());
        fragmentDefinition.typeCondition(new TypeName(ctx.typeCondition().typeName().getText()));
        if (ctx.directives() != null) {
            fragmentDefinition.directives(createDirectives(ctx.directives()));
        }
        if (ctx.selectionSet() != null) {
            fragmentDefinition.selectionSet(createSelectionSet(ctx.selectionSet()));
        }
        return fragmentDefinition.build();
    }


    public SelectionSet createSelectionSet(GraphqlParser.SelectionSetContext ctx) {
        SelectionSet.Builder builder = SelectionSet.newSelectionSet();
        newNode(builder, ctx);
        List<Selection> selections = ctx.selection().stream().map(selectionContext -> {
            if (selectionContext.field() != null) {
                return createField(selectionContext.field());
            }
            if (selectionContext.fragmentSpread() != null) {
                return createFragmentSpread(selectionContext.fragmentSpread());
            }
            if (selectionContext.inlineFragment() != null) {
                return createInlineFragment(selectionContext.inlineFragment());
            }
            return (Selection) Assert.assertShouldNeverHappen();

        }).collect(Collectors.toList());
        builder.selections(selections);
        return builder.build();
    }


    public Field createField(GraphqlParser.FieldContext ctx) {
        Field.Builder builder = Field.newField();
        newNode(builder, ctx);
        builder.name(ctx.name().getText());
        if (ctx.alias() != null) {
            builder.alias(ctx.alias().name().getText());
        }
        if (ctx.directives() != null) {
            builder.directives(createDirectives(ctx.directives()));
        }
        if (ctx.arguments() != null) {
            builder.arguments(createArguments(ctx.arguments()));
        }
        if (ctx.selectionSet() != null) {
            builder.selectionSet(createSelectionSet(ctx.selectionSet()));
        }
        return builder.build();
    }


    public InlineFragment createInlineFragment(GraphqlParser.InlineFragmentContext ctx) {
        InlineFragment.Builder inlineFragment = InlineFragment.newInlineFragment();
        newNode(inlineFragment, ctx);
        if (ctx.typeCondition() != null) {
            inlineFragment.typeCondition(createTypeName(ctx.typeCondition().typeName()));
        }
        if (ctx.directives() != null) {
            inlineFragment.directives(createDirectives(ctx.directives()));
        }
        if (ctx.selectionSet() != null) {
            inlineFragment.selectionSet(createSelectionSet(ctx.selectionSet()));
        }
        return inlineFragment.build();
    }

    //MARKER END: Here GraphqlOperation.g4 specific methods end


    public Type createType(GraphqlParser.TypeContext ctx) {
        if (ctx.typeName() != null) {
            return createTypeName(ctx.typeName());
        } else if (ctx.nonNullType() != null) {
            return createNonNullType(ctx.nonNullType());
        } else if (ctx.listType() != null) {
            return createListType(ctx.listType());
        } else {
            return assertShouldNeverHappen();
        }
    }

    public TypeName createTypeName(GraphqlParser.TypeNameContext ctx) {
        TypeName.Builder builder = TypeName.newTypeName();
        builder.name(ctx.name().getText());
        newNode(builder, ctx);
        return builder.build();
    }

    public NonNullType createNonNullType(GraphqlParser.NonNullTypeContext ctx) {
        NonNullType.Builder builder = NonNullType.newNonNullType();
        newNode(builder, ctx);
        if (ctx.listType() != null) {
            builder.type(createListType(ctx.listType()));
        } else if (ctx.typeName() != null) {
            builder.type(createTypeName(ctx.typeName()));
        } else {
            return assertShouldNeverHappen();
        }
        return builder.build();
    }

    public ListType createListType(GraphqlParser.ListTypeContext ctx) {
        ListType.Builder builder = ListType.newListType();
        newNode(builder, ctx);
        builder.type(createType(ctx.type()));
        return builder.build();
    }

    public Argument createArgument(GraphqlParser.ArgumentContext ctx) {
        Argument.Builder builder = Argument.newArgument();
        newNode(builder, ctx);
        builder.name(ctx.name().getText());
        builder.value(createValue(ctx.valueWithVariable()));
        return builder.build();
    }

    public List<Argument> createArguments(GraphqlParser.ArgumentsContext ctx) {
        return ctx.argument().stream().map(this::createArgument).collect(Collectors.toList());
    }


    public List<Directive> createDirectives(GraphqlParser.DirectivesContext ctx) {
        return ctx.directive().stream().map(this::createDirective).collect(Collectors.toList());
    }

    public Directive createDirective(GraphqlParser.DirectiveContext ctx) {
        Directive.Builder builder = Directive.newDirective();
        builder.name(ctx.name().getText());
        newNode(builder, ctx);
        if (ctx.arguments() != null) {
            builder.arguments(createArguments(ctx.arguments()));
        }
        return builder.build();
    }

    public SchemaDefinition createSchemaDefinition(GraphqlParser.SchemaDefinitionContext ctx) {
        SchemaDefinition.Builder def = SchemaDefinition.newSchemaDefintion();
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        def.operationTypeDefinitions(ctx.operationTypeDefinition().stream()
                .map(this::createOperationTypeDefinition).collect(Collectors.toList()));
        return def.build();
    }

    public OperationTypeDefinition createOperationTypeDefinition(GraphqlParser.OperationTypeDefinitionContext ctx) {
        OperationTypeDefinition.Builder def = OperationTypeDefinition.newOperationTypeDefinition();
        def.name(ctx.operationType().getText());
        def.type(createTypeName(ctx.typeName()));
        newNode(def, ctx);
        return def.build();
    }

    public ScalarTypeDefinition createScalarTypeDefinition(GraphqlParser.ScalarTypeDefinitionContext ctx) {
        ScalarTypeDefinition.Builder def = ScalarTypeDefinition.newScalarTypeDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        return def.build();
    }

    public ScalarTypeExtensionDefinition createScalarTypeExtensionDefinition(GraphqlParser.ScalarTypeExtensionDefinitionContext ctx) {
        ScalarTypeExtensionDefinition.Builder def = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        return def.build();
    }

    public ObjectTypeDefinition createObjectTypeDefinition(GraphqlParser.ObjectTypeDefinitionContext ctx) {
        ObjectTypeDefinition.Builder def = ObjectTypeDefinition.newObjectTypeDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = new ArrayList<>();
        while (implementsInterfacesContext != null) {
            List<TypeName> typeNames = implementsInterfacesContext.typeName().stream().map(this::createTypeName).collect(Collectors.toList());
            implementz.addAll(0, typeNames);
            implementsInterfacesContext = implementsInterfacesContext.implementsInterfaces();
        }
        def.implementz(implementz);
        if (ctx.fieldsDefinition() != null) {
            def.fieldDefinitions(createFieldDefinitions(ctx.fieldsDefinition()));
        }
        return def.build();
    }

    public ObjectTypeExtensionDefinition createObjectTypeExtensionDefinition(GraphqlParser.ObjectTypeExtensionDefinitionContext ctx) {
        ObjectTypeExtensionDefinition.Builder def = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = new ArrayList<>();
        while (implementsInterfacesContext != null) {
            List<TypeName> typeNames = implementsInterfacesContext.typeName().stream().map(this::createTypeName).collect(Collectors.toList());
            implementz.addAll(0, typeNames);
            implementsInterfacesContext = implementsInterfacesContext.implementsInterfaces();
        }
        def.implementz(implementz);
        if (ctx.fieldsDefinition() != null) {
            def.fieldDefinitions(createFieldDefinitions(ctx.fieldsDefinition()));
        }
        return def.build();
    }

    public List<FieldDefinition> createFieldDefinitions(GraphqlParser.FieldsDefinitionContext ctx) {
        return ctx.fieldDefinition().stream().map(this::createFieldDefinition).collect(Collectors.toList());
    }

    public FieldDefinition createFieldDefinition(GraphqlParser.FieldDefinitionContext ctx) {
        FieldDefinition.Builder def = FieldDefinition.newFieldDefintion();
        def.name(ctx.name().getText());
        def.type(createType(ctx.type()));
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.argumentsDefinition() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.argumentsDefinition().inputValueDefinition()));
        }
        return def.build();
    }

    public List<InputValueDefinition> createInputValueDefinitions(List<GraphqlParser.InputValueDefinitionContext> defs) {
        return defs.stream().map(this::createInputValueDefinition).collect(Collectors.toList());

    }

    public InputValueDefinition createInputValueDefinition(GraphqlParser.InputValueDefinitionContext ctx) {
        InputValueDefinition.Builder def = InputValueDefinition.newInputValueDefinition();
        def.name(ctx.name().getText());
        def.type(createType(ctx.type()));
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.defaultValue() != null) {
            def.defaultValue(createValue(ctx.defaultValue().value()));
        }
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        return def.build();
    }

    public InterfaceTypeDefinition createInterfaceTypeDefinition(GraphqlParser.InterfaceTypeDefinitionContext ctx) {
        InterfaceTypeDefinition.Builder def = InterfaceTypeDefinition.newInterfaceTypeDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.fieldsDefinition() != null) {
            def.definitions(createFieldDefinitions(ctx.fieldsDefinition()));
        }
        return def.build();
    }

    public InterfaceTypeExtensionDefinition createInterfaceTypeExtensionDefinition(GraphqlParser.InterfaceTypeExtensionDefinitionContext ctx) {
        InterfaceTypeExtensionDefinition.Builder def = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.fieldsDefinition() != null) {
            def.definitions(createFieldDefinitions(ctx.fieldsDefinition()));
        }
        return def.build();
    }

    public UnionTypeDefinition createUnionTypeDefinition(GraphqlParser.UnionTypeDefinitionContext ctx) {
        UnionTypeDefinition.Builder def = UnionTypeDefinition.newUnionTypeDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        List<Type> members = new ArrayList<>();
        GraphqlParser.UnionMembersContext unionMembersContext = ctx.unionMembership().unionMembers();
        while (unionMembersContext != null) {
            members.add(0, createTypeName(unionMembersContext.typeName()));
            unionMembersContext = unionMembersContext.unionMembers();
        }
        def.memberTypes(members);
        return def.build();
    }

    public UnionTypeExtensionDefinition createUnionTypeExtensionDefinition(GraphqlParser.UnionTypeExtensionDefinitionContext ctx) {
        UnionTypeExtensionDefinition.Builder def = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        List<Type> members = new ArrayList<>();
        if (ctx.unionMembership() != null) {
            GraphqlParser.UnionMembersContext unionMembersContext = ctx.unionMembership().unionMembers();
            while (unionMembersContext != null) {
                members.add(0, createTypeName(unionMembersContext.typeName()));
                unionMembersContext = unionMembersContext.unionMembers();
            }
            def.memberTypes(members);
        }
        return def.build();
    }

    public EnumTypeDefinition createEnumTypeDefinition(GraphqlParser.EnumTypeDefinitionContext ctx) {
        EnumTypeDefinition.Builder def = EnumTypeDefinition.newEnumTypeDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.enumValueDefinitions() != null) {
            def.enumValueDefinitions(
                    ctx.enumValueDefinitions().enumValueDefinition().stream().map(this::createEnumValueDefinition).collect(Collectors.toList()));
        }
        return def.build();
    }

    public EnumTypeExtensionDefinition createEnumTypeExtensionDefinition(GraphqlParser.EnumTypeExtensionDefinitionContext ctx) {
        EnumTypeExtensionDefinition.Builder def = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.enumValueDefinitions() != null) {
            def.enumValueDefinitions(
                    ctx.enumValueDefinitions().enumValueDefinition().stream().map(this::createEnumValueDefinition).collect(Collectors.toList()));
        }
        return def.build();
    }

    public EnumValueDefinition createEnumValueDefinition(GraphqlParser.EnumValueDefinitionContext ctx) {
        EnumValueDefinition.Builder def = EnumValueDefinition.newEnumValueDefinition();
        def.name(ctx.enumValue().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        return def.build();
    }

    public InputObjectTypeDefinition createInputObjectTypeDefinition(GraphqlParser.InputObjectTypeDefinitionContext ctx) {
        InputObjectTypeDefinition.Builder def = InputObjectTypeDefinition.newInputObjectDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.inputObjectValueDefinitions() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.inputObjectValueDefinitions().inputValueDefinition()));
        }
        return def.build();
    }

    public InputObjectTypeExtensionDefinition createInputObjectTypeExtensionDefinition(GraphqlParser.InputObjectTypeExtensionDefinitionContext ctx) {
        InputObjectTypeExtensionDefinition.Builder def = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        if (ctx.directives() != null) {
            def.directives(createDirectives(ctx.directives()));
        }
        if (ctx.inputObjectValueDefinitions() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.inputObjectValueDefinitions().inputValueDefinition()));
        }
        return def.build();
    }

    public DirectiveDefinition createDirectiveDefinition(GraphqlParser.DirectiveDefinitionContext ctx) {
        DirectiveDefinition.Builder def = DirectiveDefinition.newDirectiveDefinition();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        def.description(newDescription(ctx.description()));
        GraphqlParser.DirectiveLocationsContext directiveLocationsContext = ctx.directiveLocations();
        List<DirectiveLocation> directiveLocations = new ArrayList<>();
        while (directiveLocationsContext != null) {
            directiveLocations.add(0, createDirectiveLocation(directiveLocationsContext.directiveLocation()));
            directiveLocationsContext = directiveLocationsContext.directiveLocations();
        }
        def.directiveLocations(directiveLocations);
        if (ctx.argumentsDefinition() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.argumentsDefinition().inputValueDefinition()));
        }
        return def.build();
    }

    public DirectiveLocation createDirectiveLocation(GraphqlParser.DirectiveLocationContext ctx) {
        DirectiveLocation.Builder def = DirectiveLocation.newDirectiveLocation();
        def.name(ctx.name().getText());
        newNode(def, ctx);
        return def.build();
    }

    private Value createValue(GraphqlParser.ValueWithVariableContext ctx) {
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
                arrayValue.getValues().add(createValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValueWithVariable() != null) {
            ObjectValue objectValue = new ObjectValue();
            newNode(objectValue, ctx);
            for (GraphqlParser.ObjectFieldWithVariableContext objectFieldWithVariableContext :
                    ctx.objectValueWithVariable().objectFieldWithVariable()) {
                ObjectField objectField = new ObjectField(objectFieldWithVariableContext.name().getText(), createValue(objectFieldWithVariableContext.valueWithVariable()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        } else if (ctx.variable() != null) {
            VariableReference variableReference = new VariableReference(ctx.variable().name().getText());
            newNode(variableReference, ctx);
            return variableReference;
        }
        return assertShouldNeverHappen();
    }

    private Value createValue(GraphqlParser.ValueContext ctx) {
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
                arrayValue.getValues().add(createValue(valueWithVariableContext));
            }
            return arrayValue;
        } else if (ctx.objectValue() != null) {
            ObjectValue objectValue = new ObjectValue();
            newNode(objectValue, ctx);
            for (GraphqlParser.ObjectFieldContext objectFieldContext :
                    ctx.objectValue().objectField()) {
                ObjectField objectField = new ObjectField(objectFieldContext.name().getText(), createValue(objectFieldContext.value()));
                objectValue.getObjectFields().add(objectField);
            }
            return objectValue;
        }
        return assertShouldNeverHappen();
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

    private void newNode(NodeBuilder nodeBuilder, ParserRuleContext parserRuleContext) {
        List<Comment> comments = getComments(parserRuleContext);
        if (!comments.isEmpty()) {
            nodeBuilder.comments(comments);
        }
        nodeBuilder.sourceLocation(getSourceLocation(parserRuleContext));
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

}
