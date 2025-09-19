package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.Role;
import org.nobilis.nobichat.constants.TokenType;
import org.nobilis.nobichat.dto.auth.AuthenticationResponseDto;
import org.nobilis.nobichat.dto.auth.LoginRequestDto;
import org.nobilis.nobichat.dto.auth.RegistrationRequestDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.AuthToken;
import org.nobilis.nobichat.model.Organization;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.AuthTokenRepository;
import org.nobilis.nobichat.repository.OrganizationRepository;
import org.nobilis.nobichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final OrganizationRepository organizationRepository;
    @Value("${spring.security.jwt.access_token_expiration}")
    private long jwtAccessTokenExpirationMs;

    @Value("${spring.security.jwt.refresh_token_expiration}")
    private long jwtRefreshTokenExpirationMs;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuthTokenRepository tokenRepository;

    public AuthenticationResponseDto register(RegistrationRequestDto request) {
        userRepository.findByUsernameIgnoreCase(request.getUsername()).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким логином уже существует");
        });

        userRepository.findByEmail(request.getUsername()).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email уже занят");
        });

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Организация с id: '" + request.getOrganizationId() + "' не найдена"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .organization(organization)
                .build();

        User savedUser = userRepository.save(user);
        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        saveUserTokens(accessToken, refreshToken, user);

        return  new AuthenticationResponseDto(accessToken, refreshToken);
    }

    public AuthenticationResponseDto login(LoginRequestDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            log.error("Неверный логин или пароль" +
                    " Login: " + request.getUsername() +
                    " Password: " + request.getPassword());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Неверный логин или пароль");
        }

        User user = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .orElseThrow();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveUserTokens(accessToken, refreshToken, user);

        return new AuthenticationResponseDto(accessToken, refreshToken);
    }

    private void saveUserTokens(String accessTokenStr, String refreshTokenStr, User user) {
        AuthToken accessToken = AuthToken.builder()
                .token(accessTokenStr)
                .user(user)
                .active(true)
                .expirationTime(new Date(System.currentTimeMillis() + jwtAccessTokenExpirationMs))
                .tokenType(TokenType.ACCESS)
                .build();

        AuthToken refreshToken = AuthToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .active(true)
                .expirationTime(new Date(System.currentTimeMillis() + jwtRefreshTokenExpirationMs))
                .tokenType(TokenType.REFRESH)
                .build();

        List<AuthToken> tokens = List.of(accessToken, refreshToken);

        tokenRepository.saveAll(tokens);
    }

    public AuthenticationResponseDto refreshToken(String authHeader) {
        String refreshToken = authHeader.substring(7);

        String username = jwtService.extractUsername(refreshToken);

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Пользователь с именем " + username + " не найден"));

        if (jwtService.isTokenValid(refreshToken, user)) {
            String newAccessToken = jwtService.generateAccessToken(user);
            String newRefreshToken = jwtService.generateRefreshToken(user);

            saveUserTokens(newAccessToken, newRefreshToken, user);

            return new AuthenticationResponseDto(newAccessToken, newRefreshToken);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RefreshToken недействителен");
        }
    }

    public void logout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<AuthToken> userTokens = tokenRepository.findAllByUserId(user.getId());
        userTokens.forEach(authToken -> authToken.setActive(false));
        tokenRepository.saveAll(userTokens);
        SecurityContextHolder.clearContext();
    }
}
