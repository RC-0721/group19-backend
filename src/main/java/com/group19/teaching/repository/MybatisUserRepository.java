package com.group19.teaching.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.mapper.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MybatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<User> findByAccount(String account) {
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, account)
                .last("limit 1")));
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public void save(User user) {
        userMapper.insert(user);
    }

    @Override
    public void updateUserState(User user) {
        userMapper.updateById(user);
    }
}
