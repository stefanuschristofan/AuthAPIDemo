package com.christofan.test;

import com.christofan.test.model.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, String> {

    Optional<User> findByUserIdAndPassword(String userId, String password);
}
