package com.money.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

/** Reads the release version embedded in the backend artifact at build time. */
@Service
@RequiredArgsConstructor
public class ReleaseVersionService {
    private final BuildProperties buildProperties;

    public String currentVersion() {
        return buildProperties.getVersion();
    }
}
