package graphql.language;


public interface TypeDefinition extends Node, Definition {
    /**
     * @return the name of the type being defined.
     */
    String getName();
}
