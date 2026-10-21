package com.akivnah.services.repository;

import com.akivnah.services.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import com.akivnah.services.model.Role;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    List<UserAccount> findByRole(Role role);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
