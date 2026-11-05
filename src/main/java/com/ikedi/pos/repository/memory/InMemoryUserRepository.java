package com.ikedi.pos.repository.memory;

import com.ikedi.pos.model.User;
import com.ikedi.pos.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(new TreeMap<>(store).values());
    }

    @Override
    public void save(User user) {
        store.put(user.getUsername(), user);
    }
}

