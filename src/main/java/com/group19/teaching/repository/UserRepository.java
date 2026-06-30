package com.group19.teaching.repository;

import com.group19.teaching.domain.entity.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByAccount(String account);

    Optional<User> findById(Long id);

    void updateUserState(User user);
}
