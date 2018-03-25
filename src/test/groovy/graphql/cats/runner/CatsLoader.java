package graphql.cats.runner;

import graphql.cats.model.Background;
import graphql.cats.model.Given;

import java.util.Optional;

import static java.util.Optional.ofNullable;

class CatsLoader {

    static Optional<String> loadSchema(Given given, Background background) {

        return optionals(
                given.getSchema(),
                loadSchemaFile(given.getSchemaFile()),
                ofNullable(background).flatMap(Background::getSchema),
                ofNullable(background).flatMap(bg -> loadSchemaFile(bg.getSchemaFile())));
    }

    static <T> Optional<T> loadData(Given given, Background background) {
        return optionals(
                given.getTestData(),
                loadDataFile(given.getTestDataFile()),
                ofNullable(background).flatMap(Background::getTestData),
                ofNullable(background).flatMap(bg -> loadDataFile(bg.getTestDataFile())));
    }

    private static <T> Optional<T> loadDataFile(Optional<String> testDataFile) {
        // TODO
        return Optional.empty();
    }

    private static Optional<String> loadSchemaFile(Optional<String> schemaFile) {
        // TODO
        return Optional.empty();
    }

    private static <T> Optional<T> optionals(Optional<T>... options) {
        for (Optional<T> option : options) {
            if (option.isPresent()) {
                return option;
            }
        }
        return Optional.empty();
    }
}
