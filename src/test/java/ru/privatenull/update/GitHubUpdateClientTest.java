package ru.privatenull.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubUpdateClientTest {
    @Test
    void validatesRepositoryIdentifierBeforeMakingRequests() {
        assertDoesNotThrow(() -> new GitHubUpdateClient("privatenull/pnMarket"));
        assertThrows(IllegalArgumentException.class, () -> new GitHubUpdateClient("https://example.test/repo"));
        assertThrows(IllegalArgumentException.class, () -> new GitHubUpdateClient("owner/repo/extra"));
    }
}
