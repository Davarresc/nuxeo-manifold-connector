package org.apache.manifoldcf.authorities.authorities.nuxeo;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.nuxeo.NuxeoConfiguration;
import org.apache.manifoldcf.crawler.connectors.nuxeo.client.NuxeoClient;

/**
 *
 * Nuxeo Authority Connector class
 * 
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoAuthorityConnector extends BaseAuthorityConnector {

	// Configuration tabs
	private static final String CONF_SERVER_TAB_PROPERTY = "NuxeoAuthorityConnector.Server";

	// Prefix for nuxeo configuration and specification parameters
	private static final String PARAMETER_PREFIX = "nuxeo_";

	// Templates
	/**
	 * Javascript to check the configuration parameters
	 */
	private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration_conf.js";

	/**
	 * Server edit tab template
	 */
	private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_conf_server.html";

	/**
	 * Server view tab template
	 */
	private static final String VIEW_CONFIG_FORWARD = "viewConfiguration_conf.html";

	/* Nuxeo instance parameters */
	protected String protocol = null;
	protected String host = null;
	protected String port = null;
	protected String path = null;
	protected String username = null;
	protected String password = null;

	protected NuxeoClient nuxeoClient = null;

	//Constructor
	public NuxeoAuthorityConnector() {
		super();
	}

	public void setNuxeoClient(NuxeoClient nuxeoClient) {
		this.nuxeoClient = nuxeoClient;
	}

	// Close the connection.
	public void disconenct() throws ManifoldCFException {
		if (nuxeoClient != null)
			nuxeoClient = null;

		protocol = null;
		host = null;
		port = null;
		path = null;
		username = null;
		password = null;
	}

	/** CONNECTION **/
	
	//Makes connection to server
	@Override
	public void connect(ConfigParams configParams) {
		super.connect(configParams);

		protocol = params.getParameter(NuxeoConfiguration.Server.PROTOCOL);
		host = params.getParameter(NuxeoConfiguration.Server.HOST);
		port = params.getParameter(NuxeoConfiguration.Server.PORT);
		path = params.getParameter(NuxeoConfiguration.Server.PATH);
		username = params.getParameter(NuxeoConfiguration.Server.USERNAME);
		password = params.getObfuscatedParameter(NuxeoConfiguration.Server.PASSWORD);

		try {
			initNuxeoClient();
		} catch (ManifoldCFException manifoldCFException) {
			manifoldCFException.printStackTrace();
		}
	}

	/**
	 * Check the connection
	 */
	@Override
	public String check() throws ManifoldCFException {
		try {
			if (!isConnected()) {
				initNuxeoClient();
			}

			Boolean result = nuxeoClient.checkAuth();

			if (result)
				return super.check();
			else
				throw new ManifoldCFException("Nuxeo instance could not be reached");

		} catch (ServiceInterruption serviceInterruption) {
			return "Connection temporarily failed: " + serviceInterruption.getMessage();
		} catch (ManifoldCFException manifoldCFException) {
			return "Connection failed: " + manifoldCFException.getMessage();
		} catch (Exception e) {
			return "Connection failed: " + e.getMessage();
		}
	}

	/**
	 * Initialize Nuxeo client using the configured parameters.
	 * 
	 * @throws ManifoldCFException
	 */
	private void initNuxeoClient() throws ManifoldCFException {
		int portInt;

		if (nuxeoClient == null) {

			if (StringUtils.isEmpty(protocol)) {
				throw new ManifoldCFException(
						"Parameter " + NuxeoConfiguration.Server.PROTOCOL + " required but not set");
			}

			if (StringUtils.isEmpty(host)) {
				throw new ManifoldCFException("Parameter " + NuxeoConfiguration.Server.HOST + " required but not set");
			}

			if (port != null && port.length() > 0) {
				try {
					portInt = Integer.parseInt(port);
				} catch (NumberFormatException formatException) {
					throw new ManifoldCFException("Bad number: " + formatException.getMessage(), formatException);
				}
			} else {
				if (protocol.toLowerCase(Locale.ROOT).equals("http")) {
					portInt = 80;
				} else {
					portInt = 443;
				}
			}

			nuxeoClient = new NuxeoClient(protocol, host, portInt, path, username, password);

		}
	}

	/**
	 * @return true if the connector instance is connected.
	 */
	@Override
	public boolean isConnected() {
		return nuxeoClient != null;
	}

	/** VIEW CONFIGURATION **/
	@Override
	public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
			throws ManifoldCFException, IOException {

		Map<String, String> paramMap = new HashMap<String, String>();

		fillInServerConfigurationMap(paramMap, out, parameters);

		Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIG_FORWARD, paramMap, true);
	}

	private static void fillInServerConfigurationMap(Map<String, String> serverMap, IPasswordMapperActivity mapper,
			ConfigParams parameters) {

		String nuxeoProtocol = parameters.getParameter(NuxeoConfiguration.Server.PROTOCOL);
		String nuxeoHost = parameters.getParameter(NuxeoConfiguration.Server.HOST);
		String nuxeoPort = parameters.getParameter(NuxeoConfiguration.Server.PORT);
		String nuxeoPath = parameters.getParameter(NuxeoConfiguration.Server.PATH);
		String nuxeoUsername = parameters.getParameter(NuxeoConfiguration.Server.USERNAME);
		String nuxeoPassword = parameters.getParameter(NuxeoConfiguration.Server.PASSWORD);

		if (nuxeoProtocol == null)
			nuxeoProtocol = NuxeoConfiguration.Server.PROTOCOL_DEFAULT_VALUE;
		if (nuxeoHost == null)
			nuxeoHost = NuxeoConfiguration.Server.HOST_DEFAULT_VALUE;
		if (nuxeoPort == null)
			nuxeoPort = NuxeoConfiguration.Server.PORT_DEFAULT_VALUE;
		if (nuxeoPath == null)
			nuxeoPath = NuxeoConfiguration.Server.PATH_DEFAULT_VALUE;
		if (nuxeoUsername == null)
			nuxeoUsername = NuxeoConfiguration.Server.USERNAME_DEFAULT_VALUE;
		if (nuxeoPassword == null)
			nuxeoPassword = NuxeoConfiguration.Server.PASSWORD_DEFAULT_VALUE;
		else
			nuxeoPassword = mapper.mapKeyToPassword(nuxeoPassword);

		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PROTOCOL, nuxeoProtocol);
		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.HOST, nuxeoHost);
		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PORT, nuxeoPort);
		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PATH, nuxeoPath);
		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.USERNAME, nuxeoUsername);
		serverMap.put(PARAMETER_PREFIX + NuxeoConfiguration.Server.PASSWORD, nuxeoPassword);
	}

	/** CONFIGURATION CONNECTOR **/
	@Override
	public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale,
			ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {

		// Server tab
		tabsArray.add(Messages.getString(locale, CONF_SERVER_TAB_PROPERTY));

		Map<String, String> paramMap = new HashMap<String, String>();

		// Fill in the parameters form each tab
		fillInServerConfigurationMap(paramMap, out, parameters);

		Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_HEADER_FORWARD, paramMap, true);
	}

	@Override
	public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale,
			ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {

		// Call the Velocity tempaltes for each tab
		Map<String, String> paramMap = new HashMap<String, String>();

		// Set the tab name
		paramMap.put("TabName", tabName);

		// Fill in the parameters
		fillInServerConfigurationMap(paramMap, out, parameters);

		// Server tab
		Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_FORWARD_SERVER, paramMap, true);
	}

	@Override
	public String processConfigurationPost(IThreadContext thredContext, IPostParameters variableContext,
			ConfigParams parameters) {

		String nuxeoProtocol = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PROTOCOL);
		if (nuxeoProtocol != null)
			parameters.setParameter(NuxeoConfiguration.Server.PROTOCOL, nuxeoProtocol);

		String nuxeoHost = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.HOST);
		if (nuxeoHost != null)
			parameters.setParameter(NuxeoConfiguration.Server.HOST, nuxeoHost);

		String nuxeoPort = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PORT);
		if (nuxeoPort != null)
			parameters.setParameter(NuxeoConfiguration.Server.PORT, nuxeoPort);

		String nuxeoPath = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PATH);
		if (nuxeoPath != null)
			parameters.setParameter(NuxeoConfiguration.Server.PATH, nuxeoPath);

		String nuxeoUsername = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.USERNAME);
		if (nuxeoUsername != null)
			parameters.setParameter(NuxeoConfiguration.Server.USERNAME, nuxeoUsername);

		String nuxeoPassword = variableContext.getParameter(PARAMETER_PREFIX + NuxeoConfiguration.Server.PASSWORD);
		if (nuxeoPassword != null)
			parameters.setObfuscatedParameter(NuxeoConfiguration.Server.PASSWORD,
					variableContext.mapKeyToPassword(nuxeoPassword));

		return null; // It returns null if the configuration has been successful
	}

	/** AUTHORITY **/
	@Override
	public AuthorizationResponse getDefaultAuthorizationResponse(String userName) {
		return RESPONSE_UNREACHABLE;
	}

	@Override
	public AuthorizationResponse getAuthorizationResponse(String username) throws ManifoldCFException {
		try {
			List<String> authorities = nuxeoClient.getUserAuthorities(username);
			if (authorities == null || authorities.isEmpty()) {
				return RESPONSE_USERNOTFOUND;
			} else {
				return new AuthorizationResponse(authorities.toArray(new String[0]), AuthorizationResponse.RESPONSE_OK);
			}

		} catch (Exception e) {
			return RESPONSE_UNREACHABLE;
		}
	}
}
