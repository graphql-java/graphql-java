package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.InvalidSyntaxError;
import graphql.PublicApi;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.idl.errors.SchemaProblem;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.Charset.defaultCharset;

/**
 * This can take a graphql schema definition and parse it into a {@link TypeDefinitionRegistry} of
 * definitions ready to be placed into {@link SchemaGenerator} say
 */
@PublicApi
public class SchemaParser {

    /**
     * Parse a file of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param file the file to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(File file) throws SchemaProblem {
        try {
            return parse(Files.newBufferedReader(file.toPath(), defaultCharset()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse a reader of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param reader the reader to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(Reader reader) throws SchemaProblem {
        try (Reader input = reader) {
            return parse(read(input));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse a string of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param schemaInput the schema string to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(String schemaInput) throws SchemaProblem {
        try {
            Parser parser = new Parser();
            Document document = parser.parseDocument(schemaInput);

            return buildRegistry(document);
        } catch (ParseCancellationException e) {
            throw handleParseException(e);
        }
    }

    private SchemaProblem handleParseException(ParseCancellationException e) throws RuntimeException {
        InvalidSyntaxError invalidSyntaxError = InvalidSyntaxError.toInvalidSyntaxError(e);
        return new SchemaProblem(Collections.singletonList(invalidSyntaxError));
    }

    /**
     * special method to build directly a TypeDefinitionRegistry from a Document
     * useful for Introspection =&gt; IDL (Document) =&gt; TypeDefinitionRegistry
     *
     * @param document containing type definitions
     *
     * @return the TypeDefinitionRegistry containing all type definitions from the document
     *
     * @throws SchemaProblem if an error occurs
     */
    public TypeDefinitionRegistry buildRegistry(Document document) {
        List<GraphQLError> errors = new ArrayList<>();
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        List<Definition> definitions = document.getDefinitions();
        for (Definition definition : definitions) {
            typeRegistry.add(definition).ifPresent(errors::add);
        }
        if (errors.size() > 0) {
            throw new SchemaProblem(errors);
        } else {
            return typeRegistry;
        }
    }

    private String read(Reader reader) throws IOException {
        char[] buffer = new char[1024 * 4];
        StringWriter sw = new StringWriter();
        int n;
        while (-1 != (n = reader.read(buffer))) {
            sw.write(buffer, 0, n);
        }
        return sw.toString();
    }
}
