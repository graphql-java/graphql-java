package graphql.language;


import java.util.List;

public class Field implements Selection {

    private String name;
    private String alias;

    private List<Argument> arguments;
    private List<Directive> directives;
    private SelectionSet selectionSet;
}
