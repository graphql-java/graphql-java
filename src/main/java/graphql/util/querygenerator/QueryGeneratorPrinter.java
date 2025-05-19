package graphql.util.querygenerator;

import graphql.Assert;
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
            List<QueryGeneratorFieldSelection.FieldSelection> fields
    ) {
        String[] fieldPathParts = operationFieldPath.split("\\.");

        String raw = fields.stream()
                .map(this::printField)
                .collect(Collectors.joining(
                        "",
                        printOperationStart(fieldPathParts, operationName, arguments),
                        printOperationEnd(fieldPathParts)
                ));

        return AstPrinter.printAst(Parser.parse(raw));
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

            if (i == fieldPathParts.length - 1) {
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
