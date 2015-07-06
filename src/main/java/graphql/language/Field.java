package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Field implements Selection {

    private String name;
    private String alias;

    private List<Argument> arguments = new ArrayList<>();
    private List<Directive> directives;
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

    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this.name = name;
        this.arguments = arguments;
        this.selectionSet = selectionSet;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        if (name != null ? !name.equals(field.name) : field.name != null) return false;
        if (alias != null ? !alias.equals(field.alias) : field.alias != null) return false;
        if (arguments != null ? !arguments.equals(field.arguments) : field.arguments != null) return false;
        if (directives != null ? !directives.equals(field.directives) : field.directives != null) return false;
        return !(selectionSet != null ? !selectionSet.equals(field.selectionSet) : field.selectionSet != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        result = 31 * result + (directives != null ? directives.hashCode() : 0);
        result = 31 * result + (selectionSet != null ? selectionSet.hashCode() : 0);
        return result;
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
