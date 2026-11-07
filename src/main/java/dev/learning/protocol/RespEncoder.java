package dev.learning.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RespEncoder {

    public void encode(RespValue value, OutputStream output) throws IOException {
        switch (value) {
            case RespValue.SimpleString s -> encodeSimpleString(s, output);
            case RespValue.ErrorValue e -> encodeErrorValue(e, output);
            case RespValue.IntegerValue i -> encodeIntegerValue(i, output);
            case RespValue.BulkString b -> encodeBulkString(b, output);
            case RespValue.ArrayValue a -> encodeArrayValue(a, output);
        }
    }

    private void encodeArrayValue(RespValue.ArrayValue a, OutputStream output) throws IOException {
        if (a.elements() == null) {
            String outputValue = "*-1\r\n";
            output.write(outputValue.getBytes(StandardCharsets.UTF_8));
            return;
        }

        String outputValue = "*" + a.elements().size() + "\r\n";
        output.write(outputValue.getBytes(StandardCharsets.UTF_8));
        for (RespValue element : a.elements()) {
            encode(element, output);
        }
    }

    private void encodeBulkString(RespValue.BulkString b, OutputStream output) throws IOException {
        if (b.value() == null) {
            String outputValue = "$-1\r\n";
            output.write(outputValue.getBytes(StandardCharsets.UTF_8));
            return;
        }

        byte[] body = b.value();
        String header = "$" + body.length + "\r\n";

        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void encodeIntegerValue(RespValue.IntegerValue i, OutputStream output) throws IOException {
        String value = String.valueOf(i.value());
        String outputValue = ":" + value + "\r\n";
        output.write(outputValue.getBytes(StandardCharsets.UTF_8));
    }

    private void encodeErrorValue(RespValue.ErrorValue e, OutputStream output) throws IOException {
        validateLine(e.message());
        String outputValue = "-" + e.message() + "\r\n";
        output.write(outputValue.getBytes(StandardCharsets.UTF_8));
    }

    private void encodeSimpleString(RespValue.SimpleString s, OutputStream output) throws IOException {
        validateLine(s.value());
        String outputValue = "+" + s.value() + "\r\n";
        output.write(outputValue.getBytes(StandardCharsets.UTF_8));
    }

    private void validateLine(String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid RESP line");
        }
    }
}
