package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.OrganizationMember;
import com.olgav.invoicesecurity.model.OrganizationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMemberId> {
    List<OrganizationMember> findByOrganizationId(Long organizationId);
    Optional<OrganizationMember> findByUserIdAndOrganizationId(Long userId, Long organizationId);
}
