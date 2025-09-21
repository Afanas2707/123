package org.nobilis.nobichat.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.model.AuthToken;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${spring.security.jwt.secret_key}")
    private String jwtSecret;

    @Value("${spring.security.jwt.access_token_expiration}")
    private long jwtAccessTokenExpirationMs;

    @Value("${spring.security.jwt.refresh_token_expiration}")
    private long jwtRefreshTokenExpirationMs;

    private final AuthTokenRepository authTokenRepository;

    public boolean isTokenValid(String token, UserDetails user) {
        String username = extractUsername(token);

        boolean isValidToken = authTokenRepository.findByToken(token)
                .map(AuthToken::getActive).orElse(false);

        return username.equals(user.getUsername())
                && !isTokenExpired(token)
                && isValidToken;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = null;
        try {
            claims = extractAllClaims(token);
        } catch (IllegalArgumentException | JwtException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Переданный jwt token не может быть расшифрован");
        }
        return claimsResolver.apply(claims);
    }


    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateAccessToken(User user) {
        return generateToken(user, jwtAccessTokenExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, jwtRefreshTokenExpirationMs);
    }

    private String generateToken(UserDetails userDetails, long jwtExpirationMs) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64URL.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
