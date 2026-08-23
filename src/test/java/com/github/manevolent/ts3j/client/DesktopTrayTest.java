package com.github.manevolent.ts3j.client;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.Assert.assertNotNull;

public class DesktopTrayTest {
    @Test
    public void bundledAppLogoIsAvailableForTheNoCallTrayState() throws Exception {
        try (InputStream stream = DesktopTray.class.getResourceAsStream(
                "/com/github/manevolent/ts3j/client/ts3j-client.png")) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
        }
    }
}
