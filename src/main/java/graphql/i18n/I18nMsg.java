package graphql.i18n;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * A class that represents the intention to create a I18n message
 */
public class I18nMsg {
    private final String msgKey;
    private final List<Object> msgArguments;

    public I18nMsg(String msgKey, List<Object> msgArguments) {
        this.msgKey = msgKey;
        this.msgArguments = msgArguments;
    }

    public I18nMsg(String msgKey, Object... msgArguments) {
        this.msgKey = msgKey;
        this.msgArguments = asList(msgArguments);
    }

    public String getMsgKey() {
        return msgKey;
    }

    public Object[] getMsgArguments() {
        return msgArguments.toArray();
    }

    public I18nMsg argumentAt(int index, Object argument) {
        List<Object> newArgs = new ArrayList<>(this.msgArguments);
        newArgs.add(index, argument);
        return new I18nMsg(this.msgKey, newArgs);
    }

    public String toI18n(I18N i18N) {
        return i18N.msg(msgKey, msgArguments);
    }
}
