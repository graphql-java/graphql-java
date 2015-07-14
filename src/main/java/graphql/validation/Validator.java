package graphql.validation;


import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.List;

public class Validator {

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document) {
        ValidationContext validationContext = new ValidationContext(schema, document);

        LanguageTraversal languageTraversal = new LanguageTraversal();
        languageTraversal.traverse(document, new RulesVisitor(validationContext));

        return validationContext.getValidationErrors();
    }
}
