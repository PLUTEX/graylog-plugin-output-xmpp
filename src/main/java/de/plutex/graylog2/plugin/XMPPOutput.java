package de.plutex.graylog2.plugin;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.NumberField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.plugin.system.NodeId;

import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smack.packet.Message.Type;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.jid.impl.JidCreate;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import static com.google.common.base.Strings.isNullOrEmpty;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMPPOutput implements MessageOutput {
	private static final Logger LOG = LoggerFactory.getLogger(XMPPOutput.class);

	private static final String CK_HOSTNAME = "hostname";
	private static final String CK_SERVICE_NAME = "service_name";
	private static final String CK_PORT = "port";
	private static final String CK_ACCEPT_SELFSIGNED = "accept_selfsigned";
	private static final String CK_USERNAME = "username";
	private static final String CK_PASSWORD = "password";
	private static final String CK_REQUIRE_SECURITY = "require_security";
	private static final String CK_RECIPIENT = "recipient";
	private static final String CK_FORMAT = "format";
	private static final String CK_RESOURCE = "resource";

	private static final Pattern tagPattern = Pattern.compile("\\{(\\w+)\\}");

	private final Configuration config;
	private final String nodeId;
	private XMPPTCPConnection connection;
	private static AtomicBoolean isRunning = new AtomicBoolean(false);

	@Inject
	public XMPPOutput(@Assisted Stream stream, @Assisted Configuration config, NodeId nodeId) throws Exception {
		LOG.info("Initializing XMPP Output plugin");

		this.config = config;
		this.nodeId = nodeId.anonymize();
		connect();
		isRunning.set(true);
	}

	private void connect() throws Exception {
		try {
			connection = login();
		} catch (Exception e) {
			final String serverString = String.format("%s:%d (service name: %s)",
				config.getString(CK_HOSTNAME),
				config.getInt(CK_PORT),
				isNullOrEmpty(config.getString(CK_SERVICE_NAME)) ? config.getString(CK_HOSTNAME) : config.getString(CK_SERVICE_NAME)
			);
			throw new MessageOutputConfigurationException("Unable to connect to XMPP server " + serverString);
		}

		if (config.getString(CK_RECIPIENT).startsWith("muc:")) {
			String muc = config.getString(CK_RECIPIENT).substring(4);
			MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(connection);
			try {
				mucManager.getMultiUserChat(JidCreate.entityBareFrom(muc)).join(Resourcepart.from(config.getString(CK_RESOURCE)));
			} catch (Exception e) {
				throw new MessageOutputConfigurationException("Unable to join MUC " + muc);
			}
			LOG.debug("Joined MUC {0}", muc);
		}
	}

	private XMPPTCPConnection login() throws Exception {
		final String serviceName = isNullOrEmpty(config.getString(CK_SERVICE_NAME))
			? config.getString(CK_HOSTNAME) : config.getString(CK_SERVICE_NAME);

		final XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder()
			.setHost(config.getString(CK_HOSTNAME))
			.setPort(config.getInt(CK_PORT))
			.setUsernameAndPassword(config.getString(CK_USERNAME), config.getString(CK_PASSWORD))
			.setXmppDomain(serviceName)
			.setResource(Resourcepart.from(nodeId))
			.setSendPresence(false);

		if (config.getBoolean(CK_ACCEPT_SELFSIGNED)) {
			TLSUtils.acceptAllCertificates(configBuilder);
		}

		final boolean requireSecurity = config.getBoolean(CK_REQUIRE_SECURITY);
		final XMPPTCPConnectionConfiguration.SecurityMode securityMode = requireSecurity ?
			XMPPTCPConnectionConfiguration.SecurityMode.required : XMPPTCPConnectionConfiguration.SecurityMode.ifpossible;
		configBuilder.setSecurityMode(securityMode);

		final XMPPTCPConnectionConfiguration connectionConfiguration = configBuilder.build();
		if (LOG.isDebugEnabled()) {
		    LOG.debug("Supported SASL authentications: {}", SASLAuthentication.getRegisterdSASLMechanisms());
		    LOG.debug("require_security: {}", requireSecurity);
		    LOG.debug("Security mode: {}", connectionConfiguration.getSecurityMode());
		    LOG.debug("Socket factory: {}", connectionConfiguration.getSocketFactory());
		    LOG.debug("Keystore: {}", connectionConfiguration.getKeystorePath());
		    LOG.debug("Keystore type: {}", connectionConfiguration.getKeystoreType());
		}

		final XMPPTCPConnection xmppConnection = new XMPPTCPConnection(connectionConfiguration);

		xmppConnection.connect();
		xmppConnection.login();

		return xmppConnection;
	}

	@Override
	public void write(final org.graylog2.plugin.Message message) throws Exception {
		String recipient = config.getString(CK_RECIPIENT);
		Type xmppMsgtype;

		if (recipient.startsWith("muc:")) {
			xmppMsgtype = Type.groupchat;
			recipient = recipient.substring(4);
		} else {
			xmppMsgtype = Type.chat;
		}

		String format = config.getString(CK_FORMAT);
		Matcher m = tagPattern.matcher(format);
		StringBuffer messageContent = new StringBuffer();
		while (m.find()) {
			Object field = message.getField(m.group(1));
			if (field == null) {
				field = "";
			}
			m.appendReplacement(messageContent, (String) field);
		}
		m.appendTail(messageContent);
		final org.jivesoftware.smack.packet.Message xmppMessage = new org.jivesoftware.smack.packet.Message(
			recipient,
			messageContent.toString()
		);
		xmppMessage.setType(xmppMsgtype);
		try {
			connection.sendStanza(xmppMessage);
		} catch (NotConnectedException e) {
			connect();
			connection.sendStanza(xmppMessage);
		}
	}

	@Override
	public void write(final List<Message> messages) throws Exception {
		for (final Message message : messages) {
			write(message);
		}
	}

	@Override
	public void stop() {
		connection.disconnect();
		isRunning.set(false);
	}

	@Override
	public boolean isRunning() {
		return isRunning.get();
	}

	public interface Factory extends MessageOutput.Factory<XMPPOutput> {
		@Override
		XMPPOutput create(Stream stream, Configuration configuration);

		@Override
		Config getConfig();

		@Override
		Descriptor getDescriptor();
	}

	public static class Config extends MessageOutput.Config {
        	@Override
		public ConfigurationRequest getRequestedConfiguration() {
			final ConfigurationRequest cr = new ConfigurationRequest();

			cr.addField(new TextField(CK_RECIPIENT,
				"Recipient",
				"user@server.org",
				"Recipient of XMPP messages",
				ConfigurationField.Optional.NOT_OPTIONAL));

			cr.addField(new TextField(CK_HOSTNAME,
				"Hostname",
				"localhost",
				"Hostname of XMPP server",
				ConfigurationField.Optional.NOT_OPTIONAL));

			cr.addField(new NumberField(CK_PORT,
				"Port",
				5222,
				"Port of XMPP server",
				ConfigurationField.Optional.NOT_OPTIONAL));

			cr.addField(new BooleanField(CK_REQUIRE_SECURITY,
				"Require SSL/TLS?",
				false,
				"Force encryption for the server connection?"));

			cr.addField(new BooleanField(CK_ACCEPT_SELFSIGNED,
				"Accept self-signed certificates?",
				false,
				"Do not enforce full validation of the certificate chain"));

			cr.addField(new TextField(CK_USERNAME,
				"Username",
				"jabberuser",
				"Username to connect with, e. g. 'user' of the JID 'user@example.com'.",
				ConfigurationField.Optional.NOT_OPTIONAL));

			cr.addField(new TextField(CK_RESOURCE,
				"Resource",
				"graylog",
				"Nickname to use in MUCs, e. g. 'resource' of the JID 'user@example.com/resource'.",
				ConfigurationField.Optional.NOT_OPTIONAL));

			cr.addField(new TextField(CK_PASSWORD,
				"Password",
				"",
				"Password to connect with",
				ConfigurationField.Optional.NOT_OPTIONAL,
				TextField.Attribute.IS_PASSWORD));

			cr.addField(new TextField(CK_SERVICE_NAME,
				"XMPP Domain Name",
				"",
				"The domain name of the server, e. g. 'example.org' of the JID 'user@example.org'. If not specified, the hostname is being used.",
				ConfigurationField.Optional.OPTIONAL));

			cr.addField(new TextField(CK_FORMAT,
				"Message format",
				"{source} {message}",
				"The format of the message. Field names in braces are substituted.",
				ConfigurationField.Optional.NOT_OPTIONAL));

			return cr;
		}
	}

	public static class Descriptor extends MessageOutput.Descriptor {
		public Descriptor() {
			super("XMPP Output", false, "", "Send stream to XMPP recipient.");
		}
	}
}
