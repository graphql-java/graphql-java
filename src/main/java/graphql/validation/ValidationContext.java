package graphql.validation;


import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

public class ValidationContext {

    private GraphQLSchema schema;
    private Document document;
    private Map<String,FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();

//    _typeInfo: TypeInfo;
//    _fragments: {[name: string]: FragmentDefinition};
}
