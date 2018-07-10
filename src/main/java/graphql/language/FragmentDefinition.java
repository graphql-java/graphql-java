package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provided to the DataFetcher, therefore public API
 */
@PublicApi
public class FragmentDefinition extends AbstractNode<FragmentDefinition> implements Definition<FragmentDefinition>, SelectionSetContainer<FragmentDefinition>, DirectivesContainer<FragmentDefinition> {

    private final String name;
    private final TypeName typeCondition;
    private final List<Directive> directives;
    private final SelectionSet selectionSet;

    private FragmentDefinition(String name,
                               TypeName typeCondition,
                               List<Directive> directives,
                               SelectionSet selectionSet,
                               SourceLocation sourceLocation,
                               List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.typeCondition = typeCondition;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    @Override
    public String getName() {
        return name;
    }


    public TypeName getTypeCondition() {
        return typeCondition;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
    }


    @Override
    public SelectionSet getSelectionSet() {
        return selectionSet;
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
                deepCopy(selectionSet),
                getSourceLocation(),
                getComments()
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

    public static Builder newFragmentDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private TypeName typeCondition;
        private List<Directive> directives = new ArrayList<>();
        private SelectionSet selectionSet;

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder typeCondition(TypeName typeCondition) {
            this.typeCondition = typeCondition;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder selectionSet(SelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public FragmentDefinition build() {
            FragmentDefinition fragmentDefinition = new FragmentDefinition(name, typeCondition, directives, selectionSet, sourceLocation, comments);
            return fragmentDefinition;
        }
    }
}
