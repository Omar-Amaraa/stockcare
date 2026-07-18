package com.chanebplus.stockcare.security;

import com.chanebplus.stockcare.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates HS256 JWTs. Tokens carry role and ownership claims. */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(CustomUserDetails user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("uid", user.getUserId().toString())
                .claim("role", user.getRole().name());
        if (user.getPharmacyId() != null) {
            builder.claim("pharmacyId", user.getPharmacyId().toString());
        }
        if (user.getDepotId() != null) {
            builder.claim("depotId", user.getDepotId().toString());
        }
        return builder.signWith(key).compact();
    }

    public Instant expiresAt() {
        return Instant.now().plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    public Map<String, Object> claims(String token) {
        return parse(token);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
