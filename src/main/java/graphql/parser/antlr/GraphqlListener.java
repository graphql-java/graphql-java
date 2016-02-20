// Generated from /Users/andi/dev/projects/graphql-java/src/main/grammar/Graphql.g4 by ANTLR 4.5
package graphql.parser.antlr;

import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link GraphqlParser}.
 */
public interface GraphqlListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link GraphqlParser#document}.
     *
     * @param ctx the parse tree
     */
    void enterDocument(@NotNull GraphqlParser.DocumentContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#document}.
     *
     * @param ctx the parse tree
     */
    void exitDocument(@NotNull GraphqlParser.DocumentContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#definition}.
     *
     * @param ctx the parse tree
     */
    void enterDefinition(@NotNull GraphqlParser.DefinitionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#definition}.
     *
     * @param ctx the parse tree
     */
    void exitDefinition(@NotNull GraphqlParser.DefinitionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#operationDefinition}.
     *
     * @param ctx the parse tree
     */
    void enterOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#operationDefinition}.
     *
     * @param ctx the parse tree
     */
    void exitOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#operationType}.
     *
     * @param ctx the parse tree
     */
    void enterOperationType(@NotNull GraphqlParser.OperationTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#operationType}.
     *
     * @param ctx the parse tree
     */
    void exitOperationType(@NotNull GraphqlParser.OperationTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#variableDefinitions}.
     *
     * @param ctx the parse tree
     */
    void enterVariableDefinitions(@NotNull GraphqlParser.VariableDefinitionsContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#variableDefinitions}.
     *
     * @param ctx the parse tree
     */
    void exitVariableDefinitions(@NotNull GraphqlParser.VariableDefinitionsContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#variableDefinition}.
     *
     * @param ctx the parse tree
     */
    void enterVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#variableDefinition}.
     *
     * @param ctx the parse tree
     */
    void exitVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#variable}.
     *
     * @param ctx the parse tree
     */
    void enterVariable(@NotNull GraphqlParser.VariableContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#variable}.
     *
     * @param ctx the parse tree
     */
    void exitVariable(@NotNull GraphqlParser.VariableContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#defaultValue}.
     *
     * @param ctx the parse tree
     */
    void enterDefaultValue(@NotNull GraphqlParser.DefaultValueContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#defaultValue}.
     *
     * @param ctx the parse tree
     */
    void exitDefaultValue(@NotNull GraphqlParser.DefaultValueContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#selectionSet}.
     *
     * @param ctx the parse tree
     */
    void enterSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#selectionSet}.
     *
     * @param ctx the parse tree
     */
    void exitSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#selection}.
     *
     * @param ctx the parse tree
     */
    void enterSelection(@NotNull GraphqlParser.SelectionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#selection}.
     *
     * @param ctx the parse tree
     */
    void exitSelection(@NotNull GraphqlParser.SelectionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#field}.
     *
     * @param ctx the parse tree
     */
    void enterField(@NotNull GraphqlParser.FieldContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#field}.
     *
     * @param ctx the parse tree
     */
    void exitField(@NotNull GraphqlParser.FieldContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#alias}.
     *
     * @param ctx the parse tree
     */
    void enterAlias(@NotNull GraphqlParser.AliasContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#alias}.
     *
     * @param ctx the parse tree
     */
    void exitAlias(@NotNull GraphqlParser.AliasContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#arguments}.
     *
     * @param ctx the parse tree
     */
    void enterArguments(@NotNull GraphqlParser.ArgumentsContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#arguments}.
     *
     * @param ctx the parse tree
     */
    void exitArguments(@NotNull GraphqlParser.ArgumentsContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#argument}.
     *
     * @param ctx the parse tree
     */
    void enterArgument(@NotNull GraphqlParser.ArgumentContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#argument}.
     *
     * @param ctx the parse tree
     */
    void exitArgument(@NotNull GraphqlParser.ArgumentContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#fragmentSpread}.
     *
     * @param ctx the parse tree
     */
    void enterFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#fragmentSpread}.
     *
     * @param ctx the parse tree
     */
    void exitFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#inlineFragment}.
     *
     * @param ctx the parse tree
     */
    void enterInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#inlineFragment}.
     *
     * @param ctx the parse tree
     */
    void exitInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#fragmentDefinition}.
     *
     * @param ctx the parse tree
     */
    void enterFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#fragmentDefinition}.
     *
     * @param ctx the parse tree
     */
    void exitFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#fragmentName}.
     *
     * @param ctx the parse tree
     */
    void enterFragmentName(@NotNull GraphqlParser.FragmentNameContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#fragmentName}.
     *
     * @param ctx the parse tree
     */
    void exitFragmentName(@NotNull GraphqlParser.FragmentNameContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#typeCondition}.
     *
     * @param ctx the parse tree
     */
    void enterTypeCondition(@NotNull GraphqlParser.TypeConditionContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#typeCondition}.
     *
     * @param ctx the parse tree
     */
    void exitTypeCondition(@NotNull GraphqlParser.TypeConditionContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#value}.
     *
     * @param ctx the parse tree
     */
    void enterValue(@NotNull GraphqlParser.ValueContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#value}.
     *
     * @param ctx the parse tree
     */
    void exitValue(@NotNull GraphqlParser.ValueContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#valueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void enterValueWithVariable(@NotNull GraphqlParser.ValueWithVariableContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#valueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void exitValueWithVariable(@NotNull GraphqlParser.ValueWithVariableContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#enumValue}.
     *
     * @param ctx the parse tree
     */
    void enterEnumValue(@NotNull GraphqlParser.EnumValueContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#enumValue}.
     *
     * @param ctx the parse tree
     */
    void exitEnumValue(@NotNull GraphqlParser.EnumValueContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#arrayValue}.
     *
     * @param ctx the parse tree
     */
    void enterArrayValue(@NotNull GraphqlParser.ArrayValueContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#arrayValue}.
     *
     * @param ctx the parse tree
     */
    void exitArrayValue(@NotNull GraphqlParser.ArrayValueContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#arrayValueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void enterArrayValueWithVariable(@NotNull GraphqlParser.ArrayValueWithVariableContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#arrayValueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void exitArrayValueWithVariable(@NotNull GraphqlParser.ArrayValueWithVariableContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#objectValue}.
     *
     * @param ctx the parse tree
     */
    void enterObjectValue(@NotNull GraphqlParser.ObjectValueContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#objectValue}.
     *
     * @param ctx the parse tree
     */
    void exitObjectValue(@NotNull GraphqlParser.ObjectValueContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#objectValueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void enterObjectValueWithVariable(@NotNull GraphqlParser.ObjectValueWithVariableContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#objectValueWithVariable}.
     *
     * @param ctx the parse tree
     */
    void exitObjectValueWithVariable(@NotNull GraphqlParser.ObjectValueWithVariableContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#objectField}.
     *
     * @param ctx the parse tree
     */
    void enterObjectField(@NotNull GraphqlParser.ObjectFieldContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#objectField}.
     *
     * @param ctx the parse tree
     */
    void exitObjectField(@NotNull GraphqlParser.ObjectFieldContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#objectFieldWithVariable}.
     *
     * @param ctx the parse tree
     */
    void enterObjectFieldWithVariable(@NotNull GraphqlParser.ObjectFieldWithVariableContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#objectFieldWithVariable}.
     *
     * @param ctx the parse tree
     */
    void exitObjectFieldWithVariable(@NotNull GraphqlParser.ObjectFieldWithVariableContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#directives}.
     *
     * @param ctx the parse tree
     */
    void enterDirectives(@NotNull GraphqlParser.DirectivesContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#directives}.
     *
     * @param ctx the parse tree
     */
    void exitDirectives(@NotNull GraphqlParser.DirectivesContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#directive}.
     *
     * @param ctx the parse tree
     */
    void enterDirective(@NotNull GraphqlParser.DirectiveContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#directive}.
     *
     * @param ctx the parse tree
     */
    void exitDirective(@NotNull GraphqlParser.DirectiveContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#type}.
     *
     * @param ctx the parse tree
     */
    void enterType(@NotNull GraphqlParser.TypeContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#type}.
     *
     * @param ctx the parse tree
     */
    void exitType(@NotNull GraphqlParser.TypeContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#typeName}.
     *
     * @param ctx the parse tree
     */
    void enterTypeName(@NotNull GraphqlParser.TypeNameContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#typeName}.
     *
     * @param ctx the parse tree
     */
    void exitTypeName(@NotNull GraphqlParser.TypeNameContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#listType}.
     *
     * @param ctx the parse tree
     */
    void enterListType(@NotNull GraphqlParser.ListTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#listType}.
     *
     * @param ctx the parse tree
     */
    void exitListType(@NotNull GraphqlParser.ListTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link GraphqlParser#nonNullType}.
     *
     * @param ctx the parse tree
     */
    void enterNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link GraphqlParser#nonNullType}.
     *
     * @param ctx the parse tree
     */
    void exitNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx);
}