package graphql.language;


import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

/*
* This is provided to a DataFetcher, therefore it is a public API.
* This might change in the future.
 */
@PublicApi
public class Field extends AbstractNode<Field> implements Selection<Field> {

    private String name;
    private String alias;
    private List<Argument> arguments;
    private List<Directive> directives;
    private SelectionSet selectionSet;

    public Field() {
        this(null, null, new ArrayList<>(), new ArrayList<>(), null);
    }

    public Field(String name) {
        this(name, null, new ArrayList<>(), new ArrayList<>(), null);
    }

    public Field(String name, SelectionSet selectionSet) {
        this(name, null, new ArrayList<>(), new ArrayList<>(), selectionSet);
    }


    public Field(String name, List<Argument> arguments) {
        this(name, null, arguments, new ArrayList<>(), null);
    }

    public Field(String name, List<Argument> arguments, List<Directive> directives) {
        this(name, null, arguments, directives, null);
    }

    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this(name, null, arguments, new ArrayList<>(), selectionSet);
    }

    public Field(String name, String alias, List<Argument> arguments, List<Directive> directives, SelectionSet selectionSet) {
        this.name = name;
        this.alias = alias;
        this.arguments = arguments;
        this.directives = directives;
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

    public Map<String, Directive> getDirectivesByName() {
        return directivesByName(directives);
    }

    public Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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

        Field that = (Field) o;

        return NodeUtil.isEqualTo(this.name, that.name) && NodeUtil.isEqualTo(this.alias, that.alias);
    }

    @Override
    public Field deepCopy() {
        return new Field(name,
                alias,
                deepCopy(arguments),
                deepCopy(directives),
                deepCopy(selectionSet)
        );
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

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visitField(this, data);
    }
}
