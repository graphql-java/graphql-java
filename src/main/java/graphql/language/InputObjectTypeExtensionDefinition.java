package graphql.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InputObjectTypeExtensionDefinition extends InputObjectTypeDefinition {

    private InputObjectTypeExtensionDefinition(String name, List<Directive> directives, List<InputValueDefinition> inputValueDefinitions) {
        super(name, directives, inputValueDefinitions);
    }

    @Override
    public InputObjectTypeExtensionDefinition deepCopy() {
        return new InputObjectTypeExtensionDefinition(getName(),
                deepCopy(getDirectives()),
                deepCopy(getInputValueDefinitions())
        );
    }

    @Override
    public String toString() {
        return "InputObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                ", inputValueDefinitions=" + getInputValueDefinitions() +
                '}';
    }

    public static Builder newInputObjectTypeExtensionDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();
        private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();

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

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = inputValueDefinitions;
            return this;
        }

        public InputObjectTypeExtensionDefinition build() {
            InputObjectTypeExtensionDefinition inputObjectTypeDefinition = new InputObjectTypeExtensionDefinition(name, directives, inputValueDefinitions);
            inputObjectTypeDefinition.setSourceLocation(sourceLocation);
            inputObjectTypeDefinition.setComments(comments);
            inputObjectTypeDefinition.setDescription(description);
            return inputObjectTypeDefinition;
        }
    }

}
