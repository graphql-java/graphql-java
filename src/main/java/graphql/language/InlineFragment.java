package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

@PublicApi
public class InlineFragment extends AbstractNode<InlineFragment> implements Selection<InlineFragment>, SelectionSetContainer<InlineFragment> {
    private final TypeName typeCondition;
    private final List<Directive> directives;
    private final SelectionSet selectionSet;

    private InlineFragment(TypeName typeCondition,
                           List<Directive> directives,
                           SelectionSet selectionSet,
                           SourceLocation sourceLocation,
                           List<Comment> comments) {
        super(sourceLocation, comments);
        this.typeCondition = typeCondition;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }


    public TypeName getTypeCondition() {
        return typeCondition;
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


    @Override
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        if (typeCondition != null) {
            result.add(typeCondition);
        }
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public InlineFragment deepCopy() {
        return new InlineFragment(
                deepCopy(typeCondition),
                deepCopy(directives),
                deepCopy(selectionSet),
                getSourceLocation(),
                getComments()
        );
    }

    @Override
    public String toString() {
        return "InlineFragment{" +
                "typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInlineFragment(this, context);
    }

    public static Builder newInlineFragment() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
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

        public InlineFragment build() {
            InlineFragment inlineFragment = new InlineFragment(typeCondition, directives, selectionSet, sourceLocation, comments);
            return inlineFragment;
        }
    }
}
