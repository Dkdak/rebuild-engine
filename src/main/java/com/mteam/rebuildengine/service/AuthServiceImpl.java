package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.exception.DuplicateEmailException;
import com.mteam.rebuildengine.exception.InvalidCredentialsException;
import com.mteam.rebuildengine.exception.ValidationException;
import com.mteam.rebuildengine.model.entity.UserEntity;
import com.mteam.rebuildengine.model.request.LoginRequest;
import com.mteam.rebuildengine.model.request.SignupRequest;
import com.mteam.rebuildengine.model.request.UpdateNicknameRequest;
import com.mteam.rebuildengine.model.request.WithdrawRequest;
import com.mteam.rebuildengine.model.response.AuthResponse;
import com.mteam.rebuildengine.model.response.CurrentUserResponse;
import com.mteam.rebuildengine.model.response.NicknameResponse;
import com.mteam.rebuildengine.repository.UserRepository;
import com.mteam.rebuildengine.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MIN_NICKNAME_LENGTH = 2;
    private static final int MAX_NICKNAME_LENGTH = 20;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        validateSignup(request);

        UserEntity entity = userRepository.findByEmail(request.getEmail())
                .map(existing -> reactivateOrReject(existing, request))
                .orElseGet(() -> userRepository.save(request.toEntity(passwordEncoder.encode(request.getPassword()))));

        String token = jwtProvider.generateToken(entity.getEmail());
        return AuthResponse.of(token, entity);
    }

    // 논리 삭제된 계정이면 재활용, 활성 계정이면 중복 가입 거부 (FEATURE_02_AUTH.md §3.1, §3.3)
    private UserEntity reactivateOrReject(UserEntity existing, SignupRequest request) {
        if (!existing.isDeleted()) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }
        existing.reactivate(passwordEncoder.encode(request.getPassword()), request.getNickname());
        return existing;
    }

    private void validateSignup(SignupRequest request) {
        if (!request.isAgreedToTerms()) {
            throw new ValidationException("이용약관에 동의해야 합니다.");
        }
        if (request.getEmail() == null || !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new ValidationException("이메일 형식이 올바르지 않습니다.");
        }
        if (request.getPassword() == null || request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("비밀번호는 8자 이상이어야 합니다.");
        }
        validateNickname(request.getNickname());
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.length() < MIN_NICKNAME_LENGTH || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new ValidationException("닉네임은 2~20자여야 합니다.");
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserEntity entity = userRepository.findByEmail(request.getEmail())
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), entity.getPassword())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtProvider.generateToken(entity.getEmail());
        return AuthResponse.of(token, entity);
    }

    @Override
    public CurrentUserResponse getMe(String email) {
        UserEntity entity = userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("존재하지 않는 계정입니다."));
        return CurrentUserResponse.from(entity);
    }

    @Override
    @Transactional
    public NicknameResponse updateNickname(String email, UpdateNicknameRequest request) {
        validateNickname(request.getNickname());

        UserEntity entity = userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("존재하지 않는 계정입니다."));

        entity.updateNickname(request.getNickname());
        return NicknameResponse.from(entity);
    }

    @Override
    @Transactional
    public void withdraw(String email, WithdrawRequest request) {
        UserEntity entity = userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("존재하지 않는 계정입니다."));

        if (!passwordEncoder.matches(request.getPassword(), entity.getPassword())) {
            throw new InvalidCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        entity.markDeleted();
    }
}
