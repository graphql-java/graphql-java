package graphql.normalized;

import graphql.Internal;
import graphql.language.AstPrinter;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.List;

import static graphql.util.FpKit.flatList;
import static graphql.util.FpKit.map;

@Internal
public class NormalizedQuery {

    private final List<NormalizedQueryField> rootFields;

    public NormalizedQuery(List<NormalizedQueryField> rootFields) {
        this.rootFields = rootFields;
    }

    public List<NormalizedQueryField> getRootFields() {
        return rootFields;
    }

    public String printOriginalQuery() {
        List<Field> rootAstFields = flatList(map(rootFields, rootField -> rootField.getMergedField().getFields()));
        SelectionSet selectionSet = SelectionSet.newSelectionSet().selections(rootAstFields).build();
        return AstPrinter.printAst(selectionSet);
    }
}
