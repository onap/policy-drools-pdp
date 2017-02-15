package org.openecomp.policy.drools.healthcheck;

import java.util.Properties;

import org.kie.api.runtime.KieSession;
import org.openecomp.policy.drools.core.FeatureAPI;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.core.PolicySession;

public class HealthCheckFeature implements FeatureAPI {
	
	public static final String CONFIGURATION_PROPERTIES_NAME = "policy-healthcheck";

	@Override
	public int getSequenceNumber() {
		return 2;
	}

	@Override
	public void globalInit(String[] args, String configDir) {
		return;
	}

	@Override
	public KieSession activatePolicySession(PolicyContainer policyContainer, String name, String kieBaseName) {
		return null;
	}

	@Override
	public void disposeKieSession(PolicySession policySession) {
		return;
	}

	@Override
	public void destroyKieSession(PolicySession policySession) {
		return;
	}

	@Override
	public void beforeStartEngine() throws IllegalStateException {
		return;
	}

	@Override
	public void afterStartEngine() {
		try {
			HealthCheck.monitor.start();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void beforeShutdownEngine() {
		return;
	}

	@Override
	public void afterShutdownEngine() {
		try {
			HealthCheck.monitor.stop();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void beforeCreateController(String name, Properties properties) {
		return;
	}

	@Override
	public void afterCreateController(String name) {
		return;
	}

	@Override
	public void beforeStartController(String name) {
		return;
	}

	@Override
	public void afterStartController(String name) {
		return;
	}

	@Override
	public boolean isPersistenceEnabled() {
		return false;
	}

}
