# Mini Redis Server

A Java 21 learning project that implements a subset of Redis over RESP2.

Keys and values are UTF-8 text. Commands reject invalid UTF-8 instead of replacing
bytes, and arbitrary binary keys and values are unsupported. The RESP bulk-string
codec itself preserves raw bytes.

Incoming RESP frames are limited to 256 KiB in total, including nested content
and framing bytes. A single bulk payload or text line is limited to 16 KiB.
