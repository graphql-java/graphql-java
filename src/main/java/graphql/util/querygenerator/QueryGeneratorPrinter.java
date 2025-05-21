package graphql.util.querygenerator;

import graphql.language.AstPrinter;
import graphql.parser.Parser;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class QueryGeneratorPrinter {
    public String print(
            String operationFieldPath,
            @Nullable String operationName,
            @Nullable String arguments,
            QueryGeneratorFieldSelection.FieldSelection rootFieldSelection
    ) {
        String[] fieldPathParts = operationFieldPath.split("\\.");

        String fields = printFieldsForTopLevelType(rootFieldSelection);
        String start = printOperationStart(fieldPathParts, operationName, arguments);
        String end = printOperationEnd(fieldPathParts);
        String raw = start + fields + end;

        return AstPrinter.printAst(Parser.parse(raw));
    }

    private String printFieldsForTopLevelType(QueryGeneratorFieldSelection.FieldSelection rootFieldSelection) {
        return rootFieldSelection.fieldsByContainer.values().iterator().next().stream()
                .map(this::printFieldSelection)
                .collect(Collectors.joining(
                        "",
                        "... on " + rootFieldSelection.name + " {\n",
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

    private String printFieldSelectionForContainer(
            String containerName,
            List<QueryGeneratorFieldSelection.FieldSelection> fieldSelections,
            boolean needsTypeClassifier
    ) {
        String fieldStr = fieldSelections.stream()
                .map(subField ->
                        printFieldSelection(subField, needsTypeClassifier ? containerName + "_" : null))
                .collect(Collectors.joining());

        if (fieldStr.isEmpty()) {
            return "";
        }

        StringBuilder fieldSelectionSb = new StringBuilder();
        if (needsTypeClassifier) {
            fieldSelectionSb.append("... on ").append(containerName).append(" {\n");
        }

        fieldSelectionSb.append(fieldStr);

        if (needsTypeClassifier) {
            fieldSelectionSb.append(" }\n");
        }

        return fieldSelectionSb.toString();
    }

    private String printFieldSelection(QueryGeneratorFieldSelection.FieldSelection fieldSelection, @Nullable String aliasPrefix) {
        StringBuilder sb = new StringBuilder();
        if (fieldSelection.fieldsByContainer != null) {
            String fieldSelectionString = fieldSelection.fieldsByContainer.entrySet().stream()
                    .map((entry) ->
                            printFieldSelectionForContainer(entry.getKey(), entry.getValue(), fieldSelection.needsTypeClassifier))
                    .collect(Collectors.joining());
            // It is possible that some container fields ended up with empty fields (due to filtering etc). We shouldn't print those
            if (!fieldSelectionString.isEmpty()) {
                if (aliasPrefix != null) {
                    sb.append(aliasPrefix).append(fieldSelection.name).append(": ");
                }
                sb.append(fieldSelection.name)
                        .append(" {\n")
                        .append(fieldSelectionString)
                        .append("}\n");
            }
        } else {
            if (aliasPrefix != null) {
                sb.append(aliasPrefix).append(fieldSelection.name).append(": ");
            }
            sb.append(fieldSelection.name).append("\n");
        }
        return sb.toString();
    }

    private String printFieldSelection(QueryGeneratorFieldSelection.FieldSelection fieldSelection) {
        return printFieldSelection(fieldSelection, null);
    }
}
