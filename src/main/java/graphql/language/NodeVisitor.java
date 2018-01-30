/*
 * AST visitor interface  
 * 
 * Created: Jan 27, 2018 5:25:34 PM
 * Author: gkesler
 */
package graphql.language;

/**
 *
 * @author gkesler
 */
public interface NodeVisitor<T> {
    Object visit (Argument node, T data);
    Object visit (ArrayValue node, T data);
    Object visit (BooleanValue node, T data);
    Object visit (Directive node, T data);
    Object visit (DirectiveDefinition node, T data);
    Object visit (DirectiveLocation node, T data);
    Object visit (Document node, T data);
    Object visit (EnumTypeDefinition node, T data);
    Object visit (EnumValue node, T data);
    Object visit (EnumValueDefinition node, T data);
    Object visit (Field node, T data);
    Object visit (FieldDefinition node, T data);
    Object visit (FloatValue node, T data);
    Object visit (FragmentDefinition node, T data);
    Object visit (FragmentSpread node, T data);
    Object visit (InlineFragment node, T data);
    Object visit (InputObjectTypeDefinition node, T data);
    Object visit (InputValueDefinition node, T data);
    Object visit (IntValue node, T data);
    Object visit (InterfaceTypeDefinition node, T data);
    Object visit (ListType node, T data);
    Object visit (NonNullType node, T data);
    Object visit (NullValue node, T data);
    Object visit (ObjectField node, T data);
    Object visit (ObjectTypeDefinition node, T data);
    Object visit (ObjectValue node, T data);
    Object visit (OperationDefinition node, T data);
    Object visit (OperationTypeDefinition node, T data);
    Object visit (ScalarTypeDefinition node, T data);
    Object visit (SchemaDefinition node, T data);
    Object visit (SelectionSet node, T data);
    Object visit (StringValue node, T data);
    Object visit (TypeName node, T data);
    Object visit (UnionTypeDefinition node, T data);
    Object visit (VariableDefinition node, T data);
    Object visit (VariableReference node, T data);
}
