package graphql.language;

import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

public abstract class NodeVisitorStub<T>
        implements NodeVisitor<T>, TraverserVisitor<Node, T> {
    @Override
    public Object visitArgument(Argument node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitArrayValue(ArrayValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitBooleanValue(BooleanValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitDirective(Directive node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitDirectiveDefinition(DirectiveDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visitDirectiveLocation(DirectiveLocation node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitDocument(Document node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitEnumTypeDefinition(EnumTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitEnumValue(EnumValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitEnumValueDefinition(EnumValueDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitField(Field node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visitFieldDefinition(FieldDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitFloatValue(FloatValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitFragmentDefinition(FragmentDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visitFragmentSpread(FragmentSpread node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visitInlineFragment(InlineFragment node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visitInputObjectTypeDefinition(InputObjectTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitInputValueDefinition(InputValueDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitIntValue(IntValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitInterfaceTypeDefinition(InterfaceTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitListType(ListType node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visitNonNullType(NonNullType node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visitNullValue(NullValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitObjectField(ObjectField node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitObjectTypeDefinition(ObjectTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitObjectValue(ObjectValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitOperationDefinition(OperationDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visitOperationTypeDefinition(OperationTypeDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitScalarTypeDefinition(ScalarTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitSchemaDefinition(SchemaDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visitSelectionSet(SelectionSet node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitStringValue(StringValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visitTypeName(TypeName node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visitUnionTypeDefinition(UnionTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visitVariableDefinition(VariableDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visitVariableReference(VariableReference node, T data) {
        return visitValue(node, data);
    }

    protected Object visitNode(Node node, T data) {
        return data;
    }

    protected Object visitValue(Value<?> node, T data) {
        return visitNode(node, data);
    }

    protected Object visitDefinition(Definition<?> node, T data) {
        return visitNode(node, data);
    }

    protected Object visitTypeDefinition(TypeDefinition<?> node, T data) {
        return visitDefinition(node, data);
    }

    protected Object visitSelection(Selection<?> node, T data) {
        return visitNode(node, data);
    }

    protected Object visitType(Type<?> node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object enter(TraverserContext<Node> context, T data) {
        // perform double dispatch to the current node
        // avoids expensive instanceOf check
        return context
                .thisNode()
                .accept(data, this);
    }

    @Override
    public Object leave(TraverserContext<Node> context, T data) {
        return data;
    }
}
