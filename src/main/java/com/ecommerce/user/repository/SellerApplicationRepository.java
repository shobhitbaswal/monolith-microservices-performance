package com.ecommerce.user.repository;

import com.ecommerce.user.entity.SellerApplication;
import com.ecommerce.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    /**
     * Find seller application by user
     */
    Optional<SellerApplication> findByUser(User user);

    /**
     * Find seller application by user ID
     */
    Optional<SellerApplication> findByUserId(Long userId);

}
