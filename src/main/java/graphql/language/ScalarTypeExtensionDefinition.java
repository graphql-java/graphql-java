package graphql.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScalarTypeExtensionDefinition extends ScalarTypeDefinition {

    public ScalarTypeExtensionDefinition(String name) {
        super(name);
    }

    public ScalarTypeExtensionDefinition(String name, List<Directive> directives) {
        super(name, directives);
    }

    @Override
    public ScalarTypeExtensionDefinition deepCopy() {
        return new ScalarTypeExtensionDefinition(getName(), deepCopy(getDirectives()));
    }

    @Override
    public String toString() {
        return "ScalarTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                '}';

    }

    public static Builder newScalarTypeExtensionDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();

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

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public ScalarTypeExtensionDefinition build() {
            ScalarTypeExtensionDefinition scalarTypeDefinition = new ScalarTypeExtensionDefinition(name, directives);
            scalarTypeDefinition.setSourceLocation(sourceLocation);
            scalarTypeDefinition.setComments(comments);
            scalarTypeDefinition.setDescription(description);
            return scalarTypeDefinition;
        }
    }
}
