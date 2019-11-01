package graphql.language;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

@PublicApi
public class SchemaExtensionDefinition extends SchemaDefinition {

    protected SchemaExtensionDefinition(List<Directive> directives, List<OperationTypeDefinition> operationTypeDefinitions, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(directives, operationTypeDefinitions, sourceLocation, comments, ignoredChars, additionalData);
    }

    @Override
    public SchemaExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .operationTypeDefinitions(newChildren.getChildren(CHILD_OPERATION_TYPE_DEFINITIONS))
        );
    }

    @Override
    public SchemaExtensionDefinition deepCopy() {
        return new SchemaExtensionDefinition(deepCopy(getDirectives()), deepCopy(getOperationTypeDefinitions()), getSourceLocation(), getComments(),
                getIgnoredChars(), getAdditionalData());
    }

    @Override
    public String toString() {
        return "SchemaExtensionDefinition{" +
                "directives=" + getDirectives() +
                ", operationTypeDefinitions=" + getOperationTypeDefinitions() +
                "}";
    }

    public SchemaExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newSchemaExtensionDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private List<OperationTypeDefinition> operationTypeDefinitions = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        protected Builder() {
        }

        protected Builder(SchemaDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.directives = existing.getDirectives();
            this.operationTypeDefinitions = existing.getOperationTypeDefinitions();
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
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

        public Builder directive(Directive directive) {
            this.directives.add(directive);
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

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }

        public SchemaExtensionDefinition build() {
            return new SchemaExtensionDefinition(directives,
                    operationTypeDefinitions,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }

}
