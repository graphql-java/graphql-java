package graphql.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InterfaceTypeExtensionDefinition extends InterfaceTypeDefinition {

    public InterfaceTypeExtensionDefinition(String name) {
        super(name);
    }

    private InterfaceTypeExtensionDefinition(String name, List<FieldDefinition> definitions, List<Directive> directives) {
        super(name, definitions, directives);
    }

    @Override
    public InterfaceTypeExtensionDefinition deepCopy() {
        return new InterfaceTypeExtensionDefinition(getName(),
                deepCopy(getFieldDefinitions()),
                deepCopy(getDirectives())
        );
    }

    @Override
    public String toString() {
        return "InterfaceTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", fieldDefinitions=" + getFieldDefinitions() +
                ", directives=" + getDirectives() +
                '}';

    }

    public static Builder newInterfaceTypeExtensionDefinition() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<FieldDefinition> definitions = new ArrayList<>();
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

        public Builder definitions(List<FieldDefinition> definitions) {
            this.definitions = definitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public InterfaceTypeExtensionDefinition build() {
            InterfaceTypeExtensionDefinition interfaceTypeDefinition = new InterfaceTypeExtensionDefinition(name, definitions, directives);
            interfaceTypeDefinition.setSourceLocation(sourceLocation);
            interfaceTypeDefinition.setComments(comments);
            interfaceTypeDefinition.setDescription(description);
            return interfaceTypeDefinition;
        }
    }
}
