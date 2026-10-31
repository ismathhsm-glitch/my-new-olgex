package com.olgav.invoicesecurity.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class UnknownGeoLocationService implements GeoLocationService {
    @Override
    public Location locate(String ipAddress) {
        return new Location("UNKNOWN", "UNKNOWN");
    }
}
