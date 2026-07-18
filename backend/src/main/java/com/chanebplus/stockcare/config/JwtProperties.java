package com.chanebplus.stockcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stockcare.jwt")
public class JwtProperties {
    /** HS256 signing secret (>= 32 chars). Provided via env in production. */
    private String secret;
    private long expirationMinutes = 120;
    private String issuer = "stockcare";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationMinutes() { return expirationMinutes; }
    public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
