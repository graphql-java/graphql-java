package graphql.normalized.incremental;

import com.google.common.collect.Multimap;
import graphql.Internal;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.incremental.NormalizedDeferExecution.DeferBlock;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: Javadoc
@Internal
public class NormalizedDeferExecutionFactory {
    public static void normalizeDeferExecutions(
            GraphQLSchema graphQLSchema,
            Multimap<ExecutableNormalizedField, DeferDeclaration> normalizedFieldDeferExecution
    ) {
        new DeferExecutionMergerInner(graphQLSchema, normalizedFieldDeferExecution).execute();
    }

    private static class DeferExecutionMergerInner {
        private final GraphQLSchema graphQLSchema;
        private final Multimap<ExecutableNormalizedField, DeferDeclaration> input;

        private DeferExecutionMergerInner(
                GraphQLSchema graphQLSchema,
                Multimap<ExecutableNormalizedField, DeferDeclaration> normalizedFieldToDeferExecution
        ) {
            this.graphQLSchema = graphQLSchema;
            this.input = normalizedFieldToDeferExecution;
        }

        private void execute() {
            Map<DeferDeclaration, DeferBlock> declarationToBlock = new HashMap<>();

            this.input.keySet().forEach(field -> {
                Collection<DeferDeclaration> executionsForField = input.get(field);

                Set<GraphQLObjectType> fieldTypes = field.getObjectTypeNames().stream()
                        .map(graphQLSchema::getType)
                        .filter(GraphQLObjectType.class::isInstance)
                        .map(GraphQLObjectType.class::cast)
                        .collect(Collectors.toSet());

                Set<String> fieldTypeNames = fieldTypes.stream().map(GraphQLObjectType::getName).collect(Collectors.toSet());

                Map<Optional<String>, List<DeferDeclaration>> executionsByLabel = executionsForField.stream()
                        .collect(Collectors.groupingBy(execution -> Optional.ofNullable(execution.getLabel())));

                Set<NormalizedDeferExecution> deferExecutions = executionsByLabel.keySet().stream()
                        .map(label -> {
                            List<DeferDeclaration> executionsForLabel = executionsByLabel.get(label);

                            DeferBlock deferBlock = executionsForLabel.stream()
                                    .map(declarationToBlock::get)
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(new DeferBlock(label.orElse(null)));

                            Set<String> types = executionsForLabel.stream()
                                    .map(deferExecution -> {
                                        declarationToBlock.put(deferExecution, deferBlock);
                                        return "MOCK"; //deferExecution.getTargetType();
                                    })
                                    .flatMap(mapToPossibleTypes(fieldTypeNames, fieldTypes))
                                    .collect(Collectors.toSet());

                            return new NormalizedDeferExecution(deferBlock, types);

                        })
                        .collect(Collectors.toSet());

                if (!deferExecutions.isEmpty()) {
                    // Mutate the field, by setting deferExecutions
//                    field.setDeferExecutions(deferExecutions);
                }
            });
        }

        @NotNull
        private Function<String, Stream<String>> mapToPossibleTypes(Set<String> fieldTypeNames, Set<GraphQLObjectType> fieldTypes) {
            return typeName -> {
                if (typeName == null) {
                    return fieldTypeNames.stream();
                }

                GraphQLType type = graphQLSchema.getType(typeName);

                if (type instanceof GraphQLInterfaceType) {
                    return fieldTypes.stream()
                            .filter(filterImplementsInterface((GraphQLInterfaceType) type))
                            .map(GraphQLObjectType::getName);
                }

                return Stream.of(typeName);
            };
        }

        private static Predicate<GraphQLObjectType> filterImplementsInterface(GraphQLInterfaceType interfaceType) {
            return objectType -> objectType.getInterfaces().stream()
                    .anyMatch(implementedInterface -> implementedInterface.getName().equals(interfaceType.getName()));
        }
    }

}
