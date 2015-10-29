package io.qntfy.nifi.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;

import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;

import org.apache.nifi.processor.io.StreamCallback;

public class StreamingJSONAppender implements StreamCallback {
    private final String field;
    private final String content;
    private final Stack<String> depth = new Stack<>();
    private static final String A = "A";
    private static final String O = "O";
    
    public StreamingJSONAppender(String field, String content) {
        this.field = field;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1) {
            throw new IllegalArgumentException("content must be valid JSON");
        }
        this.content = content.substring(start + 1, end);
    }
    
    private static void writeComma(Event prev, OutputStream out) throws IOException {
        if (prev != Event.START_ARRAY && prev != Event.START_OBJECT && prev != Event.KEY_NAME) {
            out.write(", ".getBytes());
        }
    }
    
    @Override
    public void process(InputStream in, OutputStream out)
            throws IOException {
        JsonParser jp = Json.createParser(in);
        Event prev = null;
        boolean insideTarget = false;
        boolean seenTarget = false;
        while (jp.hasNext()) {
            Event e = jp.next();
            String str = null;
            
            switch (e) {
            case KEY_NAME:
                writeComma(prev, out);
                str = "\"" + jp.getString() + "\": ";
                out.write(str.getBytes());
                if (depth.size() == 1) {
                    // this could be the top-level target
                    if (jp.getString().equals(this.field)) {
                        // this is a match
                        insideTarget = true;
                        seenTarget = true;
                    }
                }
                
                break;
            case START_ARRAY:
                out.write("[".getBytes());
                depth.push(A);
                break;
            case START_OBJECT:
                out.write("{".getBytes());
                depth.push(O);
                break;
            case END_ARRAY:
                out.write("]".getBytes());
                depth.pop();
                break;
            case END_OBJECT:
                if (depth.size() == 1 && !seenTarget) {
                    writeComma(prev, out);
                    str = "\"" + this.field + "\": {" + this.content + "}";
                    out.write(str.getBytes());
                }
                if (insideTarget) {
                    writeComma(prev, out);
                    out.write(this.content.getBytes());
                    insideTarget = false;
                }
                out.write("}".getBytes());
                depth.pop();
                break;
            case VALUE_STRING:
                writeComma(prev, out);
                str = "\"" + jp.getString() + "\"";
                out.write(str.getBytes());
                break;
            case VALUE_NUMBER:
                writeComma(prev, out);
                out.write(jp.getString().getBytes());
                break;
            default:
                break;
            }
            prev = e;
        } 
    }
}