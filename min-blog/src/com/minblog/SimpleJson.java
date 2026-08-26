package com.minblog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析 / 生成器。
 * 只支持博客数据需要的子集：对象、数组、字符串、数字、布尔、null。
 * 解析产物：Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null。
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    // ---------------- 生成 ----------------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------- 解析 ----------------

    private final String text;
    private int pos;

    private SimpleJson(String text) {
        this.text = text;
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        SimpleJson p = new SimpleJson(json);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        return v;
    }

    private Object parseValue() {
        if (pos >= text.length()) {
            return null;
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // consume '{'
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (pos < text.length()) {
            skipWs();
            String key = parseString();
            skipWs();
            expectChar(':');
            skipWs();
            Object val = parseValue();
            map.put(key, val);
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("JSON 对象语法错误 @" + pos);
            }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // consume '['
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (pos < text.length()) {
            skipWs();
            list.add(parseValue());
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("JSON 数组语法错误 @" + pos);
            }
        }
        return list;
    }

    private String parseString() {
        if (peek() != '"') {
            throw new IllegalArgumentException("JSON 字符串语法错误 @" + pos);
        }
        pos++; // consume '"'
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    break;
                }
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 <= text.length()) {
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        break;
                    default:
                        sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("JSON 字符串未闭合 @" + pos);
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        String num = text.substring(start, pos);
        try {
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos)) {
            throw new IllegalArgumentException("JSON 关键字错误 @" + pos);
        }
        pos += word.length();
    }

    private void expectChar(char c) {
        if (peek() != c) {
            throw new IllegalArgumentException("JSON 期待字符 '" + c + "' @" + pos);
        }
        pos++;
    }
}
