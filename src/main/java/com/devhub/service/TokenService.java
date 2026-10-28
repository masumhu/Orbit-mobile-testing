package com.devhub.service;

import com.devhub.auth.AuthException;
import com.devhub.auth.RefreshTokenSession;
import com.devhub.auth.UserAccount;

import com.devhub.dto.AuthTokenResponse;
import com.devhub.dto.AuthUserResponse;
import com.devhub.repository.RefreshTokenSessionRepository;

import com.devhub.security.SecurityTokenProperties;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private static final String ISSUER = "orbit";
    private final JwtEncoder jwtEncoder;
    private final SecurityTokenProperties properties;
    private final RefreshTokenSessionRepository sessions;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, SecurityTokenProperties properties,
                        RefreshTokenSessionRepository sessions, MongoTemplate mongoTemplate) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.sessions = sessions;
        this.mongoTemplate = mongoTemplate;
    }

    public AuthTokenResponse issue(UserAccount user) {
        String refreshToken = newRefreshToken();
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        sessions.save(new RefreshTokenSession(null, UUID.randomUUID().toString(), user.id(),
                hash(refreshToken), now, null, refreshExpiresAt));
        return response(user, refreshToken, refreshExpiresAt, now);
    }

    public AuthTokenResponse refresh(String presentedToken) {
        String presentedHash = hash(presentedToken);
        RefreshTokenSession oldSession = sessions.findByTokenHash(presentedHash).orElseThrow(AuthException::new);
        Instant now = Instant.now();
        if (oldSession.revokedAt() != null) {
            revokeFamily(oldSession.familyId(), now);
            throw new AuthException();
        }
        if (!oldSession.expiresAt().isAfter(now)) throw new AuthException();

        Query activeSession = Query.query(Criteria.where("id").is(oldSession.id())
                .and("tokenHash").is(presentedHash).and("revokedAt").is(null).and("expiresAt").gt(now));
        RefreshTokenSession rotated = mongoTemplate.findAndModify(activeSession,
                new Update().set("revokedAt", now),
                FindAndModifyOptions.options().returnNew(true), RefreshTokenSession.class);
        if (rotated == null) throw new AuthException();

        UserAccount user = mongoTemplate.findById(rotated.userId(), UserAccount.class);
        if (user == null) throw new AuthException();

        String nextToken = newRefreshToken();
        Instant nextExpiry = now.plus(properties.refreshTokenTtl());
        sessions.save(new RefreshTokenSession(null, rotated.familyId(), user.id(), hash(nextToken), now, null, nextExpiry));
        return response(user, nextToken, nextExpiry, now);
    }

    public void revoke(String presentedToken) {
        String tokenHash = hash(presentedToken);
        sessions.findByTokenHash(tokenHash).ifPresent(session -> {
            if (session.revokedAt() == null) {
                Query activeSession = Query.query(Criteria.where("id").is(session.id())
                        .and("tokenHash").is(tokenHash).and("revokedAt").is(null));
                mongoTemplate.updateFirst(activeSession, new Update().set("revokedAt", Instant.now()), RefreshTokenSession.class);
            }
        });
    }

    private AuthTokenResponse response(UserAccount user, String refreshToken, Instant refreshExpiresAt, Instant now) {
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        List<String> roles = List.of("ROLE_" + user.role().name());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.id())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("email", user.email())
                .claim("organizationId", user.organizationId())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthTokenResponse(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(),
                refreshToken, refreshExpiresAt,
                new AuthUserResponse(user.id(), user.username(), user.email(), user.role(), user.organizationId()));
    }

    private void revokeFamily(String familyId, Instant now) {
        Query activeFamily = Query.query(Criteria.where("familyId").is(familyId).and("revokedAt").is(null));
        mongoTemplate.updateMulti(activeFamily, new Update().set("revokedAt", now), RefreshTokenSession.class);
    }

    private String newRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
