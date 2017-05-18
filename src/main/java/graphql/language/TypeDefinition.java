package graphql.language;


import java.util.List;

public interface TypeDefinition extends Node, Definition {
    /**
     * @return the name of the type being defined.
     */
    String getName();

    /**
     * @return the directives of this type being defined
     */
    List<Directive> getDirectives();

}
