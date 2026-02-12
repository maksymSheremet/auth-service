package my.code.auth.database.repository;

import my.code.auth.database.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    @Query("SELECT t FROM Token t WHERE t.user.id = :userId AND t.expired = false AND t.revoked = false")
    List<Token> findAllValidTokensByUser(Long userId);

    Optional<Token> findByToken(String token);

    /** Bulk-revoke all active tokens for a user. Returns count of updated rows. */
    @Modifying
    @Query("UPDATE Token t SET t.expired = true, t.revoked = true " +
           "WHERE t.user.id = :userId AND (t.expired = false OR t.revoked = false)")
    int revokeAllTokensByUser(Long userId);

}
