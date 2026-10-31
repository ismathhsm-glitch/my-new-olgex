package com.olgav.invoicesecurity.model;

import java.io.Serializable;
import java.util.Objects;

public class OrganizationMemberId implements Serializable {
    private Long user;
    private Long organization;

    public OrganizationMemberId() {
    }

    public OrganizationMemberId(Long user, Long organization) {
        this.user = user;
        this.organization = organization;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrganizationMemberId that)) return false;
        return Objects.equals(user, that.user) && Objects.equals(organization, that.organization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, organization);
    }
}
