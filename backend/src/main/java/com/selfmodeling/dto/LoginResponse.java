package com.selfmodeling.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType;

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn) {
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresIn(expiresIn);
        resp.setTokenType("Bearer");
        return resp;
    }
}
