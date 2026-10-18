package dev.codex.compressedblocks.token;

import java.util.Optional;

public record TokenInspection(Status status, TokenData data, String error) {
    public enum Status {
        NOT_TOKEN,
        VALID,
        CORRUPT
    }

    public static TokenInspection notToken() {
        return new TokenInspection(Status.NOT_TOKEN, null, null);
    }

    public static TokenInspection valid(TokenData data) {
        return new TokenInspection(Status.VALID, data, null);
    }

    public static TokenInspection corrupt(String error) {
        return new TokenInspection(Status.CORRUPT, null, error);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public boolean isTokenLike() {
        return status != Status.NOT_TOKEN;
    }

    public Optional<TokenData> validData() {
        return Optional.ofNullable(data);
    }
}
