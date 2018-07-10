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
public class SchemaDefinition extends AbstractNode<SchemaDefinition> implements Definition<SchemaDefinition> {

    private final List<Directive> directives;
    private final List<OperationTypeDefinition> operationTypeDefinitions;

    private SchemaDefinition(List<Directive> directives,
                             List<OperationTypeDefinition> operationTypeDefinitions,
                             SourceLocation sourceLocation,
                             List<Comment> comments) {
        super(sourceLocation, comments);
        this.directives = directives;
        this.operationTypeDefinitions = operationTypeDefinitions;
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


    public List<OperationTypeDefinition> getOperationTypeDefinitions() {
        return new ArrayList<>(operationTypeDefinitions);
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(operationTypeDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemaDefinition that = (SchemaDefinition) o;

        return true;
    }

    @Override
    public SchemaDefinition deepCopy() {
        return new SchemaDefinition(deepCopy(directives), deepCopy(operationTypeDefinitions), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "SchemaDefinition{" +
                "directives=" + directives +
                ", operationTypeDefinitions=" + operationTypeDefinitions +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitSchemaDefinition(this, context);
    }

    public static Builder newSchemaDefintion() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private List<Directive> directives = new ArrayList<>();
        private List<OperationTypeDefinition> operationTypeDefinitions = new ArrayList<>();

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

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder operationTypeDefinitions(List<OperationTypeDefinition> operationTypeDefinitions) {
            this.operationTypeDefinitions = operationTypeDefinitions;
            return this;
        }

        public Builder operationTypeDefinition(OperationTypeDefinition operationTypeDefinitions) {
            this.operationTypeDefinitions.add(operationTypeDefinitions);
            return this;
        }

        public SchemaDefinition build() {
            SchemaDefinition schemaDefinition = new SchemaDefinition(directives,
                    operationTypeDefinitions,
                    sourceLocation,
                    comments);
            return schemaDefinition;
        }
    }
}
