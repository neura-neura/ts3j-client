package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.identity.LocalIdentity;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IdentityProvisioningTest {
    @Test
    public void newIdentityMeetsTeamSpeakMinimum() throws Exception {
        Path identityPath = Files.createTempFile("ts3j-identity", ".ini");
        Files.delete(identityPath);

        LocalIdentity identity = TeamSpeakGateway.loadOrCreateIdentity(identityPath);

        assertTrue(identity.getSecurityLevel() >= TeamSpeakGateway.MINIMUM_IDENTITY_SECURITY);
        assertTrue(Files.size(identityPath) > 0);
    }

    @Test
    public void existingLowSecurityIdentityIsUpgradedWithoutChangingUid() throws Exception {
        Path identityPath = Files.createTempFile("ts3j-identity", ".ini");
        LocalIdentity original = LocalIdentity.generateNew(0);
        original.save(identityPath.toFile());

        LocalIdentity upgraded = TeamSpeakGateway.loadOrCreateIdentity(identityPath);

        assertEquals(original.getUid().toString(), upgraded.getUid().toString());
        assertTrue(upgraded.getSecurityLevel() >= TeamSpeakGateway.MINIMUM_IDENTITY_SECURITY);
    }
}
