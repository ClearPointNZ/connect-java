package cd.connect.spring.jersey;


import cd.connect.spring.jersey.log.JerseyFiltering;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Stream;

/**
 * You could do this monkeying around with creating lots of different spring beans and then registering your resources
 * later, but they would have to do essentially what this one does.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class JerseyApplicationBase extends ResourceConfig implements JerseyApplication {
	private static final Logger logger = LoggerFactory.getLogger(JerseyApplicationBase.class);

	public void init(ApplicationContext context, Stream<Class<?>> resources) {
		// attempt to pull the distributed configuration for all contexts into this context
		List<JaxrsServerConfigurer> configItems = new ArrayList<>();

		try {
			Map<String, JaxrsServerConfigurer> configs = context.getBeansOfType(JaxrsServerConfigurer.class);
			configItems.addAll(configs.values());
		} catch (Exception ex) {
		}

		if (configItems.size() == 0) {
			addLogging(configItems, context);
			configItems.add(new DefaultServerConfigurer());
		}

		configItems.forEach(c -> c.configure(this));

		// now register services specific to this particular Jersey Application.
		if (resources != null) {
			Set<Class<?>> resourceClasses = new HashSet<>();

			resources.forEach(c -> {
				logger.debug("registering jersey resource: {}", c.getName());
				register(context.getBean(c));
				resourceClasses.add(c);
			});

			property(JerseyApplication.RESOURCE_INTERFACE_LIST, resourceClasses);
		}
	}

	private void addLogging(List<JaxrsServerConfigurer> configItems, ApplicationContext context) {
		try {
			JerseyFiltering bean = context.getBean(JerseyFiltering.class);

			if (bean != null) {
				configItems.add(new FilteringServerConfigurer(bean));
			}
		} catch (Exception ex) {
			logger.warn("Unable register logging or path exclusion - perhaps you didn't include `{}` class?", JerseyConfig.class.getName());
		}
	}

	private void registerLogging(ApplicationContext context) {
	}

	/*
	 * override this if you wish to register further stuff
	 */
	protected void enhance(ApplicationContext context, Stream<Class<?>> resources) {
	}
}
