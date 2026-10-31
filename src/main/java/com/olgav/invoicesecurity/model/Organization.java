package com.olgav.invoicesecurity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.olgav.invoicesecurity.security.AesGcmConverter;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 500)
    private String address;
    @Column(length = 100)
    private String phone;
    
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(length = 3)
    private String currency;

    @Column(name = "tax_settings", columnDefinition = "json")
    private String taxSettings;

    @Convert(converter = AesGcmConverter.class)
    @Column(name = "bank_details_encrypted", columnDefinition = "varbinary(4096)")
    private String bankDetails;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}