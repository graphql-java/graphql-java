// Generated from Graphql.g4 by ANTLR 4.5.1
package graphql.parser.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link GraphqlParser}.
 */
public interface GraphqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#name}.
	 * @param ctx the parse tree
	 */
	void enterName(GraphqlParser.NameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#name}.
	 * @param ctx the parse tree
	 */
	void exitName(GraphqlParser.NameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#document}.
	 * @param ctx the parse tree
	 */
	void enterDocument(GraphqlParser.DocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#document}.
	 * @param ctx the parse tree
	 */
	void exitDocument(GraphqlParser.DocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#definition}.
	 * @param ctx the parse tree
	 */
	void enterDefinition(GraphqlParser.DefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#definition}.
	 * @param ctx the parse tree
	 */
	void exitDefinition(GraphqlParser.DefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#operationDefinition}.
	 * @param ctx the parse tree
	 */
	void enterOperationDefinition(GraphqlParser.OperationDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#operationDefinition}.
	 * @param ctx the parse tree
	 */
	void exitOperationDefinition(GraphqlParser.OperationDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#operationType}.
	 * @param ctx the parse tree
	 */
	void enterOperationType(GraphqlParser.OperationTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#operationType}.
	 * @param ctx the parse tree
	 */
	void exitOperationType(GraphqlParser.OperationTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#variableDefinitions}.
	 * @param ctx the parse tree
	 */
	void enterVariableDefinitions(GraphqlParser.VariableDefinitionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#variableDefinitions}.
	 * @param ctx the parse tree
	 */
	void exitVariableDefinitions(GraphqlParser.VariableDefinitionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#variableDefinition}.
	 * @param ctx the parse tree
	 */
	void enterVariableDefinition(GraphqlParser.VariableDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#variableDefinition}.
	 * @param ctx the parse tree
	 */
	void exitVariableDefinition(GraphqlParser.VariableDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(GraphqlParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(GraphqlParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void enterDefaultValue(GraphqlParser.DefaultValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void exitDefaultValue(GraphqlParser.DefaultValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#selectionSet}.
	 * @param ctx the parse tree
	 */
	void enterSelectionSet(GraphqlParser.SelectionSetContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#selectionSet}.
	 * @param ctx the parse tree
	 */
	void exitSelectionSet(GraphqlParser.SelectionSetContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#selection}.
	 * @param ctx the parse tree
	 */
	void enterSelection(GraphqlParser.SelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#selection}.
	 * @param ctx the parse tree
	 */
	void exitSelection(GraphqlParser.SelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#field}.
	 * @param ctx the parse tree
	 */
	void enterField(GraphqlParser.FieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#field}.
	 * @param ctx the parse tree
	 */
	void exitField(GraphqlParser.FieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#alias}.
	 * @param ctx the parse tree
	 */
	void enterAlias(GraphqlParser.AliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#alias}.
	 * @param ctx the parse tree
	 */
	void exitAlias(GraphqlParser.AliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(GraphqlParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(GraphqlParser.ArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(GraphqlParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(GraphqlParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#inlineFragment}.
	 * @param ctx the parse tree
	 */
	void enterInlineFragment(GraphqlParser.InlineFragmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#inlineFragment}.
	 * @param ctx the parse tree
	 */
	void exitInlineFragment(GraphqlParser.InlineFragmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#fragmentSpread}.
	 * @param ctx the parse tree
	 */
	void enterFragmentSpread(GraphqlParser.FragmentSpreadContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#fragmentSpread}.
	 * @param ctx the parse tree
	 */
	void exitFragmentSpread(GraphqlParser.FragmentSpreadContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#fragmentDefinition}.
	 * @param ctx the parse tree
	 */
	void enterFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#fragmentDefinition}.
	 * @param ctx the parse tree
	 */
	void exitFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#fragmentName}.
	 * @param ctx the parse tree
	 */
	void enterFragmentName(GraphqlParser.FragmentNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#fragmentName}.
	 * @param ctx the parse tree
	 */
	void exitFragmentName(GraphqlParser.FragmentNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#typeCondition}.
	 * @param ctx the parse tree
	 */
	void enterTypeCondition(GraphqlParser.TypeConditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#typeCondition}.
	 * @param ctx the parse tree
	 */
	void exitTypeCondition(GraphqlParser.TypeConditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(GraphqlParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(GraphqlParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#valueWithVariable}.
	 * @param ctx the parse tree
	 */
	void enterValueWithVariable(GraphqlParser.ValueWithVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#valueWithVariable}.
	 * @param ctx the parse tree
	 */
	void exitValueWithVariable(GraphqlParser.ValueWithVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void enterEnumValue(GraphqlParser.EnumValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void exitEnumValue(GraphqlParser.EnumValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#arrayValue}.
	 * @param ctx the parse tree
	 */
	void enterArrayValue(GraphqlParser.ArrayValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#arrayValue}.
	 * @param ctx the parse tree
	 */
	void exitArrayValue(GraphqlParser.ArrayValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#arrayValueWithVariable}.
	 * @param ctx the parse tree
	 */
	void enterArrayValueWithVariable(GraphqlParser.ArrayValueWithVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#arrayValueWithVariable}.
	 * @param ctx the parse tree
	 */
	void exitArrayValueWithVariable(GraphqlParser.ArrayValueWithVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void enterObjectValue(GraphqlParser.ObjectValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void exitObjectValue(GraphqlParser.ObjectValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#objectValueWithVariable}.
	 * @param ctx the parse tree
	 */
	void enterObjectValueWithVariable(GraphqlParser.ObjectValueWithVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#objectValueWithVariable}.
	 * @param ctx the parse tree
	 */
	void exitObjectValueWithVariable(GraphqlParser.ObjectValueWithVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#objectField}.
	 * @param ctx the parse tree
	 */
	void enterObjectField(GraphqlParser.ObjectFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#objectField}.
	 * @param ctx the parse tree
	 */
	void exitObjectField(GraphqlParser.ObjectFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#objectFieldWithVariable}.
	 * @param ctx the parse tree
	 */
	void enterObjectFieldWithVariable(GraphqlParser.ObjectFieldWithVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#objectFieldWithVariable}.
	 * @param ctx the parse tree
	 */
	void exitObjectFieldWithVariable(GraphqlParser.ObjectFieldWithVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#directives}.
	 * @param ctx the parse tree
	 */
	void enterDirectives(GraphqlParser.DirectivesContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#directives}.
	 * @param ctx the parse tree
	 */
	void exitDirectives(GraphqlParser.DirectivesContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#directive}.
	 * @param ctx the parse tree
	 */
	void enterDirective(GraphqlParser.DirectiveContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#directive}.
	 * @param ctx the parse tree
	 */
	void exitDirective(GraphqlParser.DirectiveContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(GraphqlParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(GraphqlParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#typeName}.
	 * @param ctx the parse tree
	 */
	void enterTypeName(GraphqlParser.TypeNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#typeName}.
	 * @param ctx the parse tree
	 */
	void exitTypeName(GraphqlParser.TypeNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#listType}.
	 * @param ctx the parse tree
	 */
	void enterListType(GraphqlParser.ListTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#listType}.
	 * @param ctx the parse tree
	 */
	void exitListType(GraphqlParser.ListTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlParser#nonNullType}.
	 * @param ctx the parse tree
	 */
	void enterNonNullType(GraphqlParser.NonNullTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlParser#nonNullType}.
	 * @param ctx the parse tree
	 */
	void exitNonNullType(GraphqlParser.NonNullTypeContext ctx);
}