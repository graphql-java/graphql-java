package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.InvalidSyntaxError;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.Parser;
import graphql.schema.idl.errors.SchemaProblem;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This can take a graphql schema definition and compile it into a {@link TypeDefinitionRegistry} of
 * definitions ready to be placed into {@link SchemaGenerator} say
 */
public class SchemaCompiler {

    /**
     * Compiles a file of schema definitions into a {@link TypeDefinitionRegistry}
     *
     * @param file the file to compile
     *
     * @return the registry of compiled type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry compile(File file) throws SchemaProblem {
        try {
            return compile(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compiles a reader of schema definitions into a {@link TypeDefinitionRegistry}
     *
     * @param reader the reader to compile
     *
     * @return the registry of compiled type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry compile(Reader reader) throws SchemaProblem {
        try (Reader input = reader) {
            return compile(read(input));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compiles a srtring of schema definitions into a {@link TypeDefinitionRegistry}
     *
     * @param schemaInput the schema string to compile
     *
     * @return the registry of compiled type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry compile(String schemaInput) throws SchemaProblem {
        try {
            Parser parser = new Parser();
            Document document = parser.parseDocument(schemaInput);

            return buildRegistry(document);
        } catch (ParseCancellationException e) {
            throw handleParseException(e);
        }
    }

    private SchemaProblem handleParseException(ParseCancellationException e) throws RuntimeException {
        RecognitionException recognitionException = (RecognitionException) e.getCause();
        SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
        InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
        return new SchemaProblem(Collections.singletonList(invalidSyntaxError));
    }

    private TypeDefinitionRegistry buildRegistry(Document document) {
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
