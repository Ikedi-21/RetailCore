package com.ikedi.pos.repository.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.ikedi.pos.model.User;
import com.ikedi.pos.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Persists users as a JSON array. */
public class JsonUserRepository implements UserRepository {

    private final Path file;
    private final Map<String, User> store = new ConcurrentHashMap<>();

    public JsonUserRepository(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            String raw = JsonFileStore.read(file);
            if ("{}".equals(raw.trim())) return;
            JsonArray array = JsonFileStore.GSON.fromJson(raw, JsonArray.class);
            if (array == null) return;
            for (JsonElement element : array) {
                User u = JsonFileStore.GSON.fromJson(element, User.class);
                store.put(u.getUsername(), u);
            }
        } catch (Exception e) {
            JsonFileStore.handleCorrupt(file, e);
        }
    }

    private void persist() throws IOException {
        JsonArray array = new JsonArray();
        for (User u : new TreeMap<>(store).values()) {
            array.add(JsonFileStore.GSON.toJsonTree(u));
        }
        JsonFileStore.write(file, JsonFileStore.GSON.toJson(array));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public synchronized void save(User user) {
        User previous = store.put(user.getUsername(), user);
        try {
            persist();
        } catch (IOException e) {
            // Rollback
            if (previous == null) {
                store.remove(user.getUsername());
            } else {
                store.put(user.getUsername(), previous);
            }
            throw new RuntimeException("Failed to save user " + user.getUsername() + ": " + e.getMessage(), e);
        }
    }
}

