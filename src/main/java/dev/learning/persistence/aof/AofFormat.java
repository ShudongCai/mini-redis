package dev.learning.persistence.aof;

final class AofFormat {
    static final int MAGIC_VERSION = 0x4d524101; // MRA followed by version 1
    static final int HEADER_BYTES = 3 * Integer.BYTES;
    static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;
    static final int REPLACE = 1;
    static final int DELETE = 2;

    private AofFormat() {}
}
