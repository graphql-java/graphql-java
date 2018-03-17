package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

/**
 * Provided to the DataFetcher, therefore public API
 */
@PublicApi
public class FragmentDefinition extends AbstractNode<FragmentDefinition> implements Definition<FragmentDefinition> {

    private String name;
    private TypeName typeCondition;
    private List<Directive> directives;
    private SelectionSet selectionSet;

    public FragmentDefinition() {
        this(null, null, new ArrayList<>(), null);
    }

    public FragmentDefinition(String name, TypeName typeCondition) {
        this(name, typeCondition, new ArrayList<>(), null);
    }

    public FragmentDefinition(String name, TypeName typeCondition, SelectionSet selectionSet) {
        this(name, typeCondition, new ArrayList<>(), selectionSet);
    }

    public FragmentDefinition(String name, TypeName typeCondition, List<Directive> directives, SelectionSet selectionSet) {
        this.name = name;
        this.typeCondition = typeCondition;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TypeName getTypeCondition() {
        return typeCondition;
    }

    public void setTypeCondition(TypeName typeCondition) {
        this.typeCondition = typeCondition;
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
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(typeCondition);
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentDefinition that = (FragmentDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public FragmentDefinition deepCopy() {
        return new FragmentDefinition(name,
                deepCopy(typeCondition),
                deepCopy(directives),
                deepCopy(selectionSet)
        );
    }

    @Override
    public String toString() {
        return "FragmentDefinition{" +
                "name='" + name + '\'' +
                ", typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor nodeVisitor) {
        return nodeVisitor.visitFragmentDefinition(this, context);
    }
}
