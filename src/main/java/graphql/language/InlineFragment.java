package graphql.language;


import java.util.List;

public class InlineFragment implements Selection {
    private String typeCondition;
    private List<Directive> directives;
    private SelectionSet selectionSet;
}
