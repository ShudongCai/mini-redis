package dev.learning.persistence;

import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.redisValue.*;

public class SnapshotFormat {
    public static final byte[] MAGIC = {'M', 'R', 'S', '1'};
    public static final int VERSION = 1;
    public static final byte TYPE_STRING = 1;
    public static final byte TYPE_LIST = 2;
    public static final byte TYPE_SET = 3;
    public static final byte TYPE_SORTED_SET = 4;

    public static byte getValueType(RedisValue value) {
        if (value instanceof ListValue) {
            return TYPE_LIST;
        }

        if (value instanceof StringValue) {
            return TYPE_STRING;
        }

        if (value instanceof SetValue) {
            return TYPE_SET;
        }

        if (value instanceof SortedSetValue) {
            return TYPE_SORTED_SET;
        }

        throw new WrongTypeException();
    }
}
