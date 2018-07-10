package graphql.language;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObjectTypeExtensionDefinition extends ObjectTypeDefinition {
    public ObjectTypeExtensionDefinition() {
        super(null);
    }

    public ObjectTypeExtensionDefinition(String name) {
        super(name);
    }

    public ObjectTypeExtensionDefinition(String name, List<Type> implementz, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        super(name, implementz, directives, fieldDefinitions);
    }

    @Override
    public ObjectTypeExtensionDefinition deepCopy() {
        return new ObjectTypeExtensionDefinition(getName(),
                deepCopy(getImplements()),
                deepCopy(getDirectives()),
                deepCopy(getFieldDefinitions())
        );
    }


    @Override
    public String toString() {
        return "ObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", implements=" + getImplements() +
                ", directives=" + getDirectives() +
                ", fieldDefinitions=" + getFieldDefinitions() +
                '}';
    }

    public static Builder newObjectTypeExtensionDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Type> implementz = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private List<FieldDefinition> fieldDefinitions = new ArrayList<>();

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

        public Builder implementz(List<Type> implementz) {
            this.implementz = implementz;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder fieldDefinitions(List<FieldDefinition> fieldDefinitions) {
            this.fieldDefinitions = fieldDefinitions;
            return this;
        }

        public ObjectTypeExtensionDefinition build() {
            ObjectTypeExtensionDefinition objectTypeDefinition = new ObjectTypeExtensionDefinition(name, implementz, directives, fieldDefinitions);
            objectTypeDefinition.setSourceLocation(sourceLocation);
            objectTypeDefinition.setComments(comments);
            objectTypeDefinition.setDescription(description);
            return objectTypeDefinition;
        }
    }
}
