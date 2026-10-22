package com.hopoong.procureflow.config;

import java.util.Locale;

import org.hibernate.engine.jdbc.internal.FormatStyle;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

public class P6SpySqlFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared,
            String sql, String url) {
        String header = String.format("%s | %d ms | %s | connection %d", now, elapsed, category, connectionId);
        if (sql == null || sql.isBlank()) {
            return header;
        }
        return header + pretty(category, sql.trim()) + ";";
    }

    private String pretty(String category, String sql) {
        if (!Category.STATEMENT.getName().equals(category) && !Category.BATCH.getName().equals(category)) {
            return System.lineSeparator() + sql;
        }
        String head = sql.toLowerCase(Locale.ROOT);
        FormatStyle style = head.startsWith("create") || head.startsWith("alter") || head.startsWith("comment")
                ? FormatStyle.DDL
                : FormatStyle.BASIC;
        return style.getFormatter().format(sql);
    }
}
