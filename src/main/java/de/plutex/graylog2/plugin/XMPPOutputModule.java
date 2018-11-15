package de.plutex.graylog2.plugin;

import org.graylog2.plugin.PluginConfigBean;
import org.graylog2.plugin.PluginModule;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutput.Factory;

import com.google.inject.multibindings.MapBinder;

import java.util.Collections;
import java.util.Set;

public class XMPPOutputModule extends PluginModule {
	@Override
	public Set<? extends PluginConfigBean> getConfigBeans() {
		return Collections.emptySet();
	}

	@Override
	protected void configure() {
		MapBinder<String, Factory<? extends MessageOutput>> outputMapBinder = outputsMapBinder();
		installOutput(outputMapBinder, XMPPOutput.class, XMPPOutput.Factory.class);
	}
}
