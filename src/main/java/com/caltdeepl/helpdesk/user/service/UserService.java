package com.caltdeepl.helpdesk.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caltdeepl.helpdesk.common.error.AppException;
import com.caltdeepl.helpdesk.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** トークンの持ち主を返す。トークンが有効でもユーザーが消えていれば 404。 */
    @Transactional(readOnly = true)
    public UserView findById(UUID id) {
        return userRepository.findById(id).map(UserView::from).orElseThrow(() -> AppException.notFound("ユーザー"));
    }
}
