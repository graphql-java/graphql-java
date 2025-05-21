package graphql.util.querygenerator;

import graphql.language.AstPrinter;
import graphql.parser.Parser;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Collectors;

public class QueryGeneratorPrinter {
    public String print(
            String operationFieldPath,
            @Nullable String operationName,
            @Nullable String arguments,
            Map<String, QueryGeneratorFieldSelection.FieldSelection> fieldSelections
    ) {
        String[] fieldPathParts = operationFieldPath.split("\\.");

        String raw = fieldSelections.entrySet().stream()
                .map(entry -> printFieldsForTopLevelType(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(
                        "",
                        printOperationStart(fieldPathParts, operationName, arguments),
                        printOperationEnd(fieldPathParts)
                ));

        return AstPrinter.printAst(Parser.parse(raw));
    }

    private String printFieldsForTopLevelType(String typeClassifier, QueryGeneratorFieldSelection.FieldSelection fieldSelections) {
        boolean hasTypeClassifier = typeClassifier != null;

        // TODO: this is awful. We should reuse the multiple containers logic somehow
        return fieldSelections.fieldsByContainer.values().iterator().next().stream()
                .map(this::printField)
                .collect(Collectors.joining(
                        "",
                        hasTypeClassifier ? "... on " + typeClassifier + " {\n" : "",
                        "}\n"
                ));
    }

    private String printOperationStart(
            String[] fieldPathParts,
            @Nullable String operationName,
            @Nullable String arguments
    ) {
        String operation = fieldPathParts[0].toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append(operation);

        if (operationName != null) {
            sb.append(" ").append(operationName).append(" ");
        }

        sb.append(" {\n");

        for (int i = 1; i < fieldPathParts.length; i++) {
            sb.append(fieldPathParts[i]);
            boolean isLastField = i == fieldPathParts.length - 1;

            if (isLastField) {
                if (arguments != null) {
                    sb.append(arguments);
                }
            }

            sb.append(" {\n");

        }
        return sb.toString();
    }

    private String printOperationEnd(String[] fieldPathParts) {
        return "}\n".repeat(fieldPathParts.length);
    }

    private String printField(QueryGeneratorFieldSelection.FieldSelection fieldSelection) {
        return printField(fieldSelection, null);
    }

    private String printField(QueryGeneratorFieldSelection.FieldSelection fieldSelection, @Nullable String aliasPrefix) {
        // It is possible that some container fields ended up with empty fields (due to filtering etc). We shouldn't print those
        if(fieldSelection.fieldsByContainer != null && fieldSelection.fieldsByContainer.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if(aliasPrefix != null) {
            sb.append(aliasPrefix).append(fieldSelection.name).append(": ");
        }
        sb.append(fieldSelection.name);
        if (fieldSelection.fieldsByContainer != null) {
            sb.append(" {\n");
            fieldSelection.fieldsByContainer.forEach((containerName, fieldSelectionList) -> {

                if(fieldSelection.needsTypeClassifier) {
                    sb.append("... on ").append(containerName).append(" {\n");
                }

                for (QueryGeneratorFieldSelection.FieldSelection subField : fieldSelectionList) {
                    sb.append(printField(subField, fieldSelection.needsTypeClassifier ? containerName + "_" : null));
                }

                if(fieldSelection.needsTypeClassifier) {
                    sb.append(" }\n");
                }

            });

            sb.append("}\n");
        } else {
            sb.append("\n");
        }
        return sb.toString();
    }
}
