package dev.learning.storage;

import dev.learning.storage.model.StoredEntry;

import java.util.*;

public class InMemoryStorage {
    private final Map<String, StoredEntry> data;

    public InMemoryStorage() {
        this.data = new HashMap<>();
    }


    public Optional<String> get(String key) {
        if (!data.containsKey(key)) {
            return Optional.empty();
        }
        StoredEntry result = data.get(key);
        return Optional.of(result.value());
    }

    public void set(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input is invalid");
        }

        Map<String, String> values = spiltValue(input.trim());
        String[] temp = input.split(" ");
        Long expireTime = null;

        if (values.containsKey("EX")) {
            expireTime = Long.parseLong(values.get("EX"));
        }

        data.put(temp[0], new StoredEntry(values.get(temp[0]), expireTime));
    }

    public void set(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        data.put(key, new StoredEntry(value, null));
    }

    public int del(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        if (data.remove(key) != null) {
            return 1;
        }

        return 0;
    }

    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key should not be blank");
        }

        return data.containsKey(key);
    }

    private Map<String, String> spiltValue(String input) {
        Map<String, String> result = new HashMap<>();
        int position = 0;
        String temp = "";
        boolean close = true;

        for (int i = 0; i < input.length(); ++i) {
            if (input.charAt(i) == '"') {
                close = !close;

                if (temp.isEmpty()) {
                    throw new IllegalArgumentException("Invalid key - value mapping");
                }

                if (!close) {
                    position++;
                } else {
                    result.put(temp, input.substring(position, i));
                    position = i + 2;
                    temp = "";
                }

                continue;
            }

            if (input.charAt(i) != ' ' || !close) {
                continue;
            }

            if (temp.isEmpty()) {
                temp = input.substring(position, i);
            } else {
                result.put(temp, input.substring(position, i));
                temp = "";
            }
            position = i + 1;
        }

        if (!temp.isEmpty()) {
            result.put(temp, input.substring(position));
        }

        return result;
    }
}
