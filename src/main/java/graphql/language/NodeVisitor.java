package graphql.language;

public interface NodeVisitor<T> {
    Object visitArgument(Argument node, T data);

    Object visitArrayValue(ArrayValue node, T data);

    Object visitBooleanValue(BooleanValue node, T data);

    Object visitDirective(Directive node, T data);

    Object visitDirectiveDefinition(DirectiveDefinition node, T data);

    Object visitDirectiveLocation(DirectiveLocation node, T data);

    Object visitDocument(Document node, T data);

    Object visitEnumTypeDefinition(EnumTypeDefinition node, T data);

    Object visitEnumValue(EnumValue node, T data);

    Object visitEnumValueDefinition(EnumValueDefinition node, T data);

    Object visitField(Field node, T data);

    Object visitFieldDefinition(FieldDefinition node, T data);

    Object visitFloatValue(FloatValue node, T data);

    Object visitFragmentDefinition(FragmentDefinition node, T data);

    Object visitFragmentSpread(FragmentSpread node, T data);

    Object visitInlineFragment(InlineFragment node, T data);

    Object visitInputObjectTypeDefinition(InputObjectTypeDefinition node, T data);

    Object visitInputValueDefinition(InputValueDefinition node, T data);

    Object visitIntValue(IntValue node, T data);

    Object visitInterfaceTypeDefinition(InterfaceTypeDefinition node, T data);

    Object visitListType(ListType node, T data);

    Object visitNonNullType(NonNullType node, T data);

    Object visitNullValue(NullValue node, T data);

    Object visitObjectField(ObjectField node, T data);

    Object visitObjectTypeDefinition(ObjectTypeDefinition node, T data);

    Object visitObjectValue(ObjectValue node, T data);

    Object visitOperationDefinition(OperationDefinition node, T data);

    Object visitOperationTypeDefinition(OperationTypeDefinition node, T data);

    Object visitScalarTypeDefinition(ScalarTypeDefinition node, T data);

    Object visitSchemaDefinition(SchemaDefinition node, T data);

    Object visitSelectionSet(SelectionSet node, T data);

    Object visitStringValue(StringValue node, T data);

    Object visitTypeName(TypeName node, T data);

    Object visitUnionTypeDefinition(UnionTypeDefinition node, T data);

    Object visitVariableDefinition(VariableDefinition node, T data);

    Object visitVariableReference(VariableReference node, T data);
}
