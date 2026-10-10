package com.lankapay.registry.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for reg_registry.
 * Controls whether a user's phone number is discoverable by others.
 */
@Entity
@Table(name = "reg_registry")
public class Registry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** E.164 phone number – mirrors idn_users.phone for fast lookup. */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /** true = user can be found by phone lookup. Default true. */
    @Column(nullable = false)
    private boolean discoverable = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Registry() {}

    public Registry(User user, String phone) {
        this.user = user;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getPhone() { return phone; }
    public boolean isDiscoverable() { return discoverable; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable; }
}
