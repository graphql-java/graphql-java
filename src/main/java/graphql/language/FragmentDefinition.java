package graphql.language;


import java.util.List;

public class FragmentDefinition implements Definition{
    private String name;
    private String typeCondition;
    private List<Directive> directives;
    private SelectionSet selectionSet;
}
