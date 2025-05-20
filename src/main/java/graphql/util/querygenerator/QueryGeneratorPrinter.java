package graphql.util.querygenerator;

import graphql.Assert;
import graphql.language.AstPrinter;
import graphql.parser.Parser;

import javax.annotation.Nullable;
import java.util.List;
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

        return fieldSelections.fields.stream()
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
        // It is possible that some container fields ended up with empty fields (due to filtering etc). We shouldn't print those
        if(fieldSelection.fields != null && fieldSelection.fields.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(fieldSelection.name);
        if (fieldSelection.fields != null && !fieldSelection.fields.isEmpty()) {
            sb.append(" {\n");
            for (QueryGeneratorFieldSelection.FieldSelection subField : fieldSelection.fields) {
                sb.append(printField(subField));
            }
            sb.append("}\n");
        } else {
            sb.append("\n");
        }
        return sb.toString();
    }
}
