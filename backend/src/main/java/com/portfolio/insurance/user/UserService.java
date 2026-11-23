package com.portfolio.insurance.user;

import com.portfolio.insurance.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository userRepository;
    private final UserMapper userMapper;

    public List<UserDto> findAll() {
        return userMapper.toDtos(userRepository.findAllByOrderByUsernameAsc());
    }

    public UserDto findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", username));
    }
}
