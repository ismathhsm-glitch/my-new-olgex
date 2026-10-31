package com.olgav.invoicesecurity.model;

import com.olgav.invoicesecurity.security.AesGcmConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_templates", uniqueConstraints =
        @UniqueConstraint(name = "uk_face_template_sample", columnNames = {"user_id", "sample_index"}))
@Getter
@Setter
@NoArgsConstructor
public class FaceTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = AesGcmConverter.class)
    @Column(name = "embedding_encrypted", nullable = false, columnDefinition = "varbinary(4096)")
    private String embedding;

    @Column(name = "sample_index", nullable = false)
    private int sampleIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
