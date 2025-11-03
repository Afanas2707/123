package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Organization;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public Organization createOrganization(String organizationName) {
        if (organizationRepository.findByName(organizationName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Организация с названием '" + organizationName + "' уже существует");
        }

        Organization organization = Organization.builder()
                .name(organizationName)
                .build();
        return organizationRepository.save(organization);
    }

    public Organization getOrganizationById(UUID id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Организация с id: " + id + " не найдена"));
    }

    @Transactional
    public Organization updateOrganization(UUID id, String newOrganizationName) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        canEditOrganization(user, id);

        Organization existingOrganization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Организация с id: " + id + " не найдена"));

        if (!existingOrganization.getName().equalsIgnoreCase(newOrganizationName)) {
            Optional<Organization> organizationWithNewName = organizationRepository.findByName(newOrganizationName);
            if (organizationWithNewName.isPresent() && !organizationWithNewName.get().getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Организация с названием '" + newOrganizationName + "' уже существует");
            }
        }

        existingOrganization.setName(newOrganizationName);
        return organizationRepository.save(existingOrganization);
    }

    public boolean canEditOrganization(User user, UUID organizationId) {
        if (user.getOrganization().getId().equals(organizationId)) {
            return true;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя изменять чужую организацию");
        }
    }
}

