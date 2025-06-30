package my.code.auth.util;

import lombok.Getter;

@Getter
public enum OAuth2Provider {
    GOOGLE("sub"),
    GITHUB("id"),
    UNKNOWN("id");

    private final String nameAttributeKey;

    OAuth2Provider(String nameAttributeKey) {
        this.nameAttributeKey = nameAttributeKey;
    }

    public static OAuth2Provider fromString(String provider) {
        try {
            return valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
