package graphql.language;

import java.util.List;

public class InputObjectTypeExtensionDefinition extends InputObjectTypeDefinition {
    public InputObjectTypeExtensionDefinition(String name) {
        super(name);
    }

    public InputObjectTypeExtensionDefinition(String name, List<Directive> directives, List<InputValueDefinition> inputValueDefinitions) {
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

}
