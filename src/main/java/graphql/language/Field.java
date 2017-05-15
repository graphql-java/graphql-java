package graphql.language;


import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;

/*
* This is provided to a DataFetcher, therefore it is a public API.
* This might change in the future.
 */
@PublicApi
public class Field extends AbstractNode implements Selection {

    private String name;
    private String alias;

    private List<Argument> arguments = new ArrayList<>();
    private List<Directive> directives = new ArrayList<>();
    private SelectionSet selectionSet;

    public Field() {

    }

    public Field(String name) {
        this.name = name;
    }

    public Field(String name, SelectionSet selectionSet) {
        this.name = name;
        this.selectionSet = selectionSet;
    }


    public Field(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public Field(String name, List<Argument> arguments, List<Directive> directives) {
        this.name = name;
        this.arguments = arguments;
        this.directives = directives;
    }

    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this.name = name;
        this.arguments = arguments;
        this.selectionSet = selectionSet;
    }


    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(arguments);
        result.addAll(directives);
        if (selectionSet != null) result.add(selectionSet);
        return result;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    public void setSelectionSet(SelectionSet selectionSet) {
        this.selectionSet = selectionSet;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        if (name != null ? !name.equals(field.name) : field.name != null) return false;
        return !(alias != null ? !alias.equals(field.alias) : field.alias != null);

    }


    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", arguments=" + arguments +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
