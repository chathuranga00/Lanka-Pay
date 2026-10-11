package com.lankapay.admin.repository;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserAdminSpecification {

    public static Specification<User> withFilters(String phone, String nic, UserStatus status, UserRole role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (phone != null && !phone.isBlank()) {
                predicates.add(cb.like(root.get("phone"), "%" + phone.trim() + "%"));
            }

            if (nic != null && !nic.isBlank()) {
                predicates.add(cb.like(root.get("nicNumber"), "%" + nic.trim() + "%"));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
