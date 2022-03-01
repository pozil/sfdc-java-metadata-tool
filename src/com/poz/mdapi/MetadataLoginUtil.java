package com.poz.mdapi;

import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.LoginResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;


public abstract class MetadataLoginUtil {

	private final static String SOAP_API_URL = "/services/Soap/c/";
	private final static String PROD_AUTH_URL = "https://login.salesforce.com" + SOAP_API_URL;
	private final static String SANDBOX_AUTH_URL = "https://test.salesforce.com" + SOAP_API_URL;
	
    public static MetadataConnection login(String username, String password, boolean isProduction, double apiVersion) throws ConnectionException {
        String authUrl = isProduction ? PROD_AUTH_URL : SANDBOX_AUTH_URL;
        authUrl += Double.toString(apiVersion);
    	final LoginResult loginResult = loginToSalesforce(username, password, authUrl);
        return createMetadataConnection(loginResult);
    }

    private static LoginResult loginToSalesforce(final String username, final String password, final String loginUrl) throws ConnectionException {
        final ConnectorConfig config = new ConnectorConfig();
        config.setAuthEndpoint(loginUrl);
        config.setServiceEndpoint(loginUrl);
        config.setManualLogin(true);
        return (new EnterpriseConnection(config)).login(username, password);
    }
    
    private static MetadataConnection createMetadataConnection(final LoginResult loginResult) throws ConnectionException {
        final ConnectorConfig config = new ConnectorConfig();
        config.setServiceEndpoint(loginResult.getMetadataServerUrl());
        config.setSessionId(loginResult.getSessionId());
        return new MetadataConnection(config);
    }
}