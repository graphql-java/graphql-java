package graphql;


import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlListener;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

public class GraphQL {

    public static void main(String[] args) {
        String input = "{me{name}}";
        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        GraphqlParser.DocumentContext document = parser.document();

        ParseTreeWalker walker = new ParseTreeWalker(); // create standard walker
        MyListener extractor = new MyListener();
        walker.walk(extractor, document); // initiate walk of tree with listener
    }

    static class MyListener implements GraphqlListener{

        @Override
        public void enterDocument(@NotNull GraphqlParser.DocumentContext ctx) {
                System.out.println(ctx.getText());
        }

        @Override
        public void exitDocument(@NotNull GraphqlParser.DocumentContext ctx) {

        }

        @Override
        public void enterDefinition(@NotNull GraphqlParser.DefinitionContext ctx) {

        }

        @Override
        public void exitDefinition(@NotNull GraphqlParser.DefinitionContext ctx) {

        }

        @Override
        public void enterOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx) {

        }

        @Override
        public void exitOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx) {

        }

        @Override
        public void enterOperationType(@NotNull GraphqlParser.OperationTypeContext ctx) {

        }

        @Override
        public void exitOperationType(@NotNull GraphqlParser.OperationTypeContext ctx) {

        }

        @Override
        public void enterVariableDefinitions(@NotNull GraphqlParser.VariableDefinitionsContext ctx) {

        }

        @Override
        public void exitVariableDefinitions(@NotNull GraphqlParser.VariableDefinitionsContext ctx) {

        }

        @Override
        public void enterVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx) {

        }

        @Override
        public void exitVariableDefinition(@NotNull GraphqlParser.VariableDefinitionContext ctx) {

        }

        @Override
        public void enterVariable(@NotNull GraphqlParser.VariableContext ctx) {

        }

        @Override
        public void exitVariable(@NotNull GraphqlParser.VariableContext ctx) {

        }

        @Override
        public void enterDefaultValue(@NotNull GraphqlParser.DefaultValueContext ctx) {

        }

        @Override
        public void exitDefaultValue(@NotNull GraphqlParser.DefaultValueContext ctx) {

        }

        @Override
        public void enterSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx) {
        }

        @Override
        public void exitSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx) {

        }

        @Override
        public void enterSelection(@NotNull GraphqlParser.SelectionContext ctx) {

        }

        @Override
        public void exitSelection(@NotNull GraphqlParser.SelectionContext ctx) {
            System.out.println("Selection:" + ctx.field().NAME());
        }

        @Override
        public void enterField(@NotNull GraphqlParser.FieldContext ctx) {

        }

        @Override
        public void exitField(@NotNull GraphqlParser.FieldContext ctx) {

        }

        @Override
        public void enterAlias(@NotNull GraphqlParser.AliasContext ctx) {

        }

        @Override
        public void exitAlias(@NotNull GraphqlParser.AliasContext ctx) {

        }

        @Override
        public void enterArguments(@NotNull GraphqlParser.ArgumentsContext ctx) {

        }

        @Override
        public void exitArguments(@NotNull GraphqlParser.ArgumentsContext ctx) {

        }

        @Override
        public void enterArgument(@NotNull GraphqlParser.ArgumentContext ctx) {

        }

        @Override
        public void exitArgument(@NotNull GraphqlParser.ArgumentContext ctx) {

        }

        @Override
        public void enterFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx) {

        }

        @Override
        public void exitFragmentSpread(@NotNull GraphqlParser.FragmentSpreadContext ctx) {

        }

        @Override
        public void enterInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx) {

        }

        @Override
        public void exitInlineFragment(@NotNull GraphqlParser.InlineFragmentContext ctx) {

        }

        @Override
        public void enterFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx) {

        }

        @Override
        public void exitFragmentDefinition(@NotNull GraphqlParser.FragmentDefinitionContext ctx) {

        }

        @Override
        public void enterFragmentName(@NotNull GraphqlParser.FragmentNameContext ctx) {

        }

        @Override
        public void exitFragmentName(@NotNull GraphqlParser.FragmentNameContext ctx) {

        }

        @Override
        public void enterTypeCondition(@NotNull GraphqlParser.TypeConditionContext ctx) {

        }

        @Override
        public void exitTypeCondition(@NotNull GraphqlParser.TypeConditionContext ctx) {

        }

        @Override
        public void enterValue(@NotNull GraphqlParser.ValueContext ctx) {

        }

        @Override
        public void exitValue(@NotNull GraphqlParser.ValueContext ctx) {

        }

        @Override
        public void enterEnumValue(@NotNull GraphqlParser.EnumValueContext ctx) {

        }

        @Override
        public void exitEnumValue(@NotNull GraphqlParser.EnumValueContext ctx) {

        }

        @Override
        public void enterArrayValue(@NotNull GraphqlParser.ArrayValueContext ctx) {

        }

        @Override
        public void exitArrayValue(@NotNull GraphqlParser.ArrayValueContext ctx) {

        }

        @Override
        public void enterObjectValue(@NotNull GraphqlParser.ObjectValueContext ctx) {

        }

        @Override
        public void exitObjectValue(@NotNull GraphqlParser.ObjectValueContext ctx) {

        }

        @Override
        public void enterObjectField(@NotNull GraphqlParser.ObjectFieldContext ctx) {

        }

        @Override
        public void exitObjectField(@NotNull GraphqlParser.ObjectFieldContext ctx) {

        }

        @Override
        public void enterDirectives(@NotNull GraphqlParser.DirectivesContext ctx) {

        }

        @Override
        public void exitDirectives(@NotNull GraphqlParser.DirectivesContext ctx) {

        }

        @Override
        public void enterDirective(@NotNull GraphqlParser.DirectiveContext ctx) {

        }

        @Override
        public void exitDirective(@NotNull GraphqlParser.DirectiveContext ctx) {

        }

        @Override
        public void enterType(@NotNull GraphqlParser.TypeContext ctx) {

        }

        @Override
        public void exitType(@NotNull GraphqlParser.TypeContext ctx) {

        }

        @Override
        public void enterTypeName(@NotNull GraphqlParser.TypeNameContext ctx) {

        }

        @Override
        public void exitTypeName(@NotNull GraphqlParser.TypeNameContext ctx) {

        }

        @Override
        public void enterListType(@NotNull GraphqlParser.ListTypeContext ctx) {

        }

        @Override
        public void exitListType(@NotNull GraphqlParser.ListTypeContext ctx) {

        }

        @Override
        public void enterNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx) {

        }

        @Override
        public void exitNonNullType(@NotNull GraphqlParser.NonNullTypeContext ctx) {

        }

        @Override
        public void visitTerminal(TerminalNode node) {

        }

        @Override
        public void visitErrorNode(ErrorNode node) {

        }

        @Override
        public void enterEveryRule(ParserRuleContext ctx) {

        }

        @Override
        public void exitEveryRule(ParserRuleContext ctx) {

        }
    }
}
