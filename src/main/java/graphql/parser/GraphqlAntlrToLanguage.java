package graphql.parser;


import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.parser.antlr.GraphqlBaseListener;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.misc.NotNull;

public class GraphqlAntlrToLanguage extends GraphqlBaseListener {

    Document result;
    OperationDefinition operationDefinition;
    SelectionSet selectionSet;
    Field field;

    @Override
    public void enterDocument(@NotNull GraphqlParser.DocumentContext ctx) {
        result = new Document();
    }

    @Override
    public void enterOperationDefinition(@NotNull GraphqlParser.OperationDefinitionContext ctx) {
        if (ctx.operationType() == null) {
            operationDefinition = new OperationDefinition();
            operationDefinition.setOperation(OperationDefinition.Operation.QUERY);
            result.getDefinitions().add(operationDefinition);
        }
    }

    @Override
    public void enterSelectionSet(@NotNull GraphqlParser.SelectionSetContext ctx) {
        selectionSet = new SelectionSet();
        operationDefinition.setSelectionSet(selectionSet);

    }

    @Override
    public void enterField(@NotNull GraphqlParser.FieldContext ctx) {
        Field field = new Field();
        field.setName(ctx.NAME().getText());
        selectionSet.getSelections().add(field);
    }

}
