package com.olgav.invoicesecurity.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum Permission {
    VIEW_OWN_ACTIVITY,
    MANAGE_OWN_FACE,
    MANAGE_OWN_DEVICES,
    VIEW_ORG_SETTINGS,
    EDIT_ORG_SETTINGS,
    EDIT_BANK_DETAILS,
    MANAGE_MEMBERS,
    TRANSFER_ADMIN,
    VIEW_ORG_AUDIT,
    VIEW_SECURITY_DASHBOARD;

    public static List<Permission> forRole(OrgRole role) {
        if (role == null) {
            return List.of();
        }
        return switch (role) {
            case ORG_ADMIN -> List.of(
                    VIEW_OWN_ACTIVITY,
                    MANAGE_OWN_FACE,
                    MANAGE_OWN_DEVICES,
                    VIEW_ORG_SETTINGS,
                    EDIT_ORG_SETTINGS,
                    EDIT_BANK_DETAILS,
                    MANAGE_MEMBERS,
                    TRANSFER_ADMIN,
                    VIEW_ORG_AUDIT,
                    VIEW_SECURITY_DASHBOARD);
            case ACCOUNTANT, STAFF, VIEWER -> List.of(
                    VIEW_OWN_ACTIVITY,
                    MANAGE_OWN_FACE,
                    MANAGE_OWN_DEVICES,
                    VIEW_ORG_SETTINGS);
        };
    }

    public static List<Permission> forUser(User user, OrganizationMember member) {
        Set<Permission> permissions = new LinkedHashSet<>();
        permissions.add(VIEW_OWN_ACTIVITY);
        permissions.add(MANAGE_OWN_FACE);
        permissions.add(MANAGE_OWN_DEVICES);

        if (user != null && user.getSystemRole() == User.SystemRole.SUPER_ADMIN) {
            if (member != null && "ACTIVE".equals(member.getStatus())) {
                permissions.addAll(forRole(member.getRole()));
            }
            return List.copyOf(permissions);
        }

        OrgRole orgRole = member != null ? member.getRole() : null;
        if (member != null && "ACTIVE".equals(member.getStatus())) {
            permissions.addAll(forRole(orgRole));
        }
        return List.copyOf(permissions);
    }
}
