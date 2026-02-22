package my.code.auth.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tokens")
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true)
    private String tokenValue;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private TokenType tokenType = TokenType.BEARER;

    @Builder.Default
    @Column(nullable = false)
    private boolean expired = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant expiresAt;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
