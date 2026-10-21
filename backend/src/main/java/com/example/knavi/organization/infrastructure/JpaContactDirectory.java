package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.ContactDirectory;
import com.example.knavi.organization.domain.Contact;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaContactDirectory implements ContactDirectory {

    private final ContactRepository contacts;

    JpaContactDirectory(ContactRepository contacts) {
        this.contacts = contacts;
    }

    @Override
    public Optional<ContactRef> findByCode(String code) {
        return contacts.findByCode(code).map(JpaContactDirectory::toRef);
    }

    @Override
    public Optional<ContactRef> findById(UUID id) {
        return contacts.findById(id).map(JpaContactDirectory::toRef);
    }

    @Override
    public List<ContactRef> findByIds(Collection<UUID> ids) {
        return contacts.findAllById(ids).stream()
                .map(JpaContactDirectory::toRef)
                .toList();
    }

    private static ContactRef toRef(Contact c) {
        return new ContactRef(c.getId(), c.getCode(), c.getName(), c.getDepartmentId());
    }
}
