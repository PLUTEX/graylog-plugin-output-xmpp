package de.plutex.graylog2.plugin;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

public class XMPPOutputMetaData implements PluginMetaData {
    @Override
    public String getUniqueId() {
        return XMPPOutput.class.getName();
    }

    @Override
    public String getName() {
        return "XMPP output";
    }

    @Override
    public String getAuthor() {
        return "Jan-Philipp Litza <jpl@plutex.de>";
    }

    @Override
    public URI getURL() {
        return URI.create("https://github.com/plutex/graylog-xmpp-output-plugin");
    }

    @Override
    public Version getVersion() {
        return Version.from(0, 1, 0);
    }

    @Override
    public String getDescription() {
        return "Output messages via XMPP";
    }

    @Override
    public Version getRequiredVersion() {
        return Version.from(2, 4, 0);
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return Collections.emptySet();
    }
}
