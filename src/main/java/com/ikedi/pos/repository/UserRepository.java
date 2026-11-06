package com.ikedi.pos.repository;

import com.ikedi.pos.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    List<User> findAll();

    /** Inserts or replaces the user with this username. */
    void save(User user);
}

