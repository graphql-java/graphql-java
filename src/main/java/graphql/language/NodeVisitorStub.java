/*
 * Abstract Visitor interface implementation,  
 * which helps to override only required methods in concrete visitor implementation.
 * 
 * 
 * Created: Jan 29, 2018 1:37:20 PM
 * Author: gkesler
 */
package graphql.language;

import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

/**
 *
 * @author gkesler
 */
public abstract class NodeVisitorStub<T> 
        implements NodeVisitor<T>, TraverserVisitor<Node, T> {
    @Override
    public Object visit(Argument node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(ArrayValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(BooleanValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(Directive node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(DirectiveDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visit(DirectiveLocation node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(Document node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(EnumTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(EnumValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(EnumValueDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(Field node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visit(FieldDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(FloatValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(FragmentDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visit(FragmentSpread node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visit(InlineFragment node, T data) {
        return visitSelection(node, data);
    }

    @Override
    public Object visit(InputObjectTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(InputValueDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(IntValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(InterfaceTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(ListType node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visit(NonNullType node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visit(NullValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(ObjectField node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(ObjectTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(ObjectValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(OperationDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visit(OperationTypeDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(ScalarTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(SchemaDefinition node, T data) {
        return visitDefinition(node, data);
    }

    @Override
    public Object visit(SelectionSet node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(StringValue node, T data) {
        return visitValue(node, data);
    }

    @Override
    public Object visit(TypeName node, T data) {
        return visitType(node, data);
    }

    @Override
    public Object visit(UnionTypeDefinition node, T data) {
        return visitTypeDefinition(node, data);
    }

    @Override
    public Object visit(VariableDefinition node, T data) {
        return visitNode(node, data);
    }

    @Override
    public Object visit(VariableReference node, T data) {
        return visitValue(node, data);
    }

    protected Object visitNode (Node node, T data) {
        return data;
    }
    
    protected Object visitValue (Value<?> node, T data) {
        return visitNode(node, data);
    }
    
    protected Object visitDefinition (Definition<?> node, T data) {
        return visitNode(node, data);
    }
    
    protected Object visitTypeDefinition (TypeDefinition<?> node, T data) {
        return visitDefinition(node, data);
    }
    
    protected Object visitSelection (Selection<?> node, T data) {
        return visitNode(node, data);
    }
    
    protected Object visitType (Type<?> node, T data) {
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
