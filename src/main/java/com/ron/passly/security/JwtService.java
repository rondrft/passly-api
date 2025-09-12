package com.ron.passly.security;

import com.ron.passly.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    //Convert secretKey to a valid object Key
    private Key getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    //Generate Token

    public String generateToken(User user){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        //Extra Claims
        Map<String,Object> extraClaims = new HashMap<>();
        extraClaims.put("id", user.getId());
        extraClaims.put("roles", user.getRoles());

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getUsername())
                .setIssuer("Passly-API")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .requireIssuer("Passly-API")
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claimsResolver.apply(claims);
        } catch (Exception e) {
            throw new RuntimeException("Invalid token or Malicious!");
        }
    }

    public boolean isTokenValid(String token, String email) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .requireIssuer("Passly-API")
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String extractedEmail = claims.getSubject();
            return extractedEmail.equals(email) &&
                    !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }


    private boolean isTokenExpired(String token){
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
}