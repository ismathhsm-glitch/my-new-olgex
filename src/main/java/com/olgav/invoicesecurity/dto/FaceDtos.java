package com.olgav.invoicesecurity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class FaceDtos {
    private FaceDtos() {
    }

    public record EnrollmentRequest(
            @NotNull @Size(min = 3, max = 3) List<@NotNull @Size(min = 128, max = 128) List<Float>> samples) {
    }

    public record FaceStatus(boolean enrolled, int samplesStored) {
    }
}
