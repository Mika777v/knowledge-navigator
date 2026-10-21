package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.domain.Contact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByCode(String code);
}
