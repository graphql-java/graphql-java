// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5
package graphql.parser.antlr;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link GraphqlParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface GraphqlVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#document}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDocument(@NotNull GraphqlParser.DocumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#definition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefinition(@NotNull GraphqlParser.DefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#operationDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#operationType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperationType(@NotNull GraphqlParser.OperationTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#variableDefinitions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDefinitions(@NotNull GraphqlParser.VariableDefinitionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#variableDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#variable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariable(@NotNull GraphqlParser.VariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#defaultValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefaultValue(@NotNull GraphqlParser.DefaultValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#selectionSet}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#selection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelection(@NotNull GraphqlParser.SelectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#field}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField(@NotNull GraphqlParser.FieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#alias}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlias(@NotNull GraphqlParser.AliasContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#arguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArguments(@NotNull GraphqlParser.ArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#argument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(@NotNull GraphqlParser.ArgumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#fragmentSpread}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#inlineFragment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#fragmentDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#fragmentName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentName(@NotNull GraphqlParser.FragmentNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#typeCondition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeCondition(@NotNull GraphqlParser.TypeConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(@NotNull GraphqlParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#valueWithVariable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueWithVariable(@NotNull GraphqlParser.ValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#enumValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumValue(@NotNull GraphqlParser.EnumValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#arrayValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayValue(@NotNull GraphqlParser.ArrayValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#arrayValueWithVariable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayValueWithVariable(@NotNull GraphqlParser.ArrayValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#objectValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectValue(@NotNull GraphqlParser.ObjectValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#objectValueWithVariable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectValueWithVariable(@NotNull GraphqlParser.ObjectValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#objectField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectField(@NotNull GraphqlParser.ObjectFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#objectFieldWithVariable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectFieldWithVariable(@NotNull GraphqlParser.ObjectFieldWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#directives}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectives(@NotNull GraphqlParser.DirectivesContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#directive}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirective(@NotNull GraphqlParser.DirectiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(@NotNull GraphqlParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#typeName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeName(@NotNull GraphqlParser.TypeNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#listType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitListType(@NotNull GraphqlParser.ListTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlParser#nonNullType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx);
}