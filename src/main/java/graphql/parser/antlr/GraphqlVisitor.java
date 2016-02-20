// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5.1
package graphql.parser.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link graphql.parser.antlr.GraphqlParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link java.lang.Void} for
 * operations with no return type.
 * @author Andreas Marek
 * @version v1.0
 */
public interface GraphqlVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#document}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDocument(GraphqlParser.DocumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#definition}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefinition(GraphqlParser.DefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#operationDefinition}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperationDefinition(GraphqlParser.OperationDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#operationType}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperationType(GraphqlParser.OperationTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#variableDefinitions}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDefinitions(GraphqlParser.VariableDefinitionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#variableDefinition}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDefinition(GraphqlParser.VariableDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#variable}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariable(GraphqlParser.VariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#defaultValue}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefaultValue(GraphqlParser.DefaultValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#selectionSet}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelectionSet(GraphqlParser.SelectionSetContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#selection}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelection(GraphqlParser.SelectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#field}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField(GraphqlParser.FieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#alias}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlias(GraphqlParser.AliasContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#arguments}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArguments(GraphqlParser.ArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#argument}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(GraphqlParser.ArgumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#fragmentSpread}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentSpread(GraphqlParser.FragmentSpreadContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#inlineFragment}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInlineFragment(GraphqlParser.InlineFragmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#fragmentDefinition}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#fragmentName}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentName(GraphqlParser.FragmentNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#typeCondition}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeCondition(GraphqlParser.TypeConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#value}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(GraphqlParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#valueWithVariable}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueWithVariable(GraphqlParser.ValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#enumValue}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumValue(GraphqlParser.EnumValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#arrayValue}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayValue(GraphqlParser.ArrayValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#arrayValueWithVariable}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayValueWithVariable(GraphqlParser.ArrayValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#objectValue}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectValue(GraphqlParser.ObjectValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#objectValueWithVariable}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectValueWithVariable(GraphqlParser.ObjectValueWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#objectField}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectField(GraphqlParser.ObjectFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#objectFieldWithVariable}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectFieldWithVariable(GraphqlParser.ObjectFieldWithVariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#directives}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectives(GraphqlParser.DirectivesContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#directive}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirective(GraphqlParser.DirectiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#type}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(GraphqlParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#typeName}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeName(GraphqlParser.TypeNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#listType}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitListType(GraphqlParser.ListTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link graphql.parser.antlr.GraphqlParser#nonNullType}.
	 *
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNonNullType(GraphqlParser.NonNullTypeContext ctx);
}
