package com.olgav.invoicesecurity.service;

public interface GeoLocationService {
    Location locate(String ipAddress);

    record Location(String country, String city) {
        public String displayName() {
            return city + ", " + country;
        }
    }
}
