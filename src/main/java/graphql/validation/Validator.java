package graphql.validation;


import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.List;

public class Validator {

    public List<ValidationError> validateDocument(GraphQLSchema schema, Document document) {
        ValidationContext validationContext = new ValidationContext(schema, document);

        LanguageTraversal languageTraversal = new LanguageTraversal();
        ErrorCollector  errorCollector = new ErrorCollector();
        languageTraversal.traverse(document, new RulesVisitor(validationContext,errorCollector));

        return errorCollector.getErrors();
    }
}
