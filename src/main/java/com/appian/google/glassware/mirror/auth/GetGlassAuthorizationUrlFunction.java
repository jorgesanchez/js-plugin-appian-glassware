package com.appian.google.glassware.mirror.auth;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.appian.google.glassware.mirror.AppianGlasswareUtils;
import com.appian.google.glassware.mirror.AuthUtil;
import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.expression.annotations.Function;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

@GoogleGlassCategory
public final class GetGlassAuthorizationUrlFunction {

  private static final Logger LOG = Logger.getLogger(GetGlassAuthorizationUrlFunction.class);

  @Function
  public String GetGlassAuthorizationUrl(ServiceContext sc) throws IOException {
    URL resource = AuthUtil.class.getResource("/com/appian/googleglass/plugins/glassware.properties");
    InputStream authPropertiesStream = resource.openStream();
    Properties authProperties = new Properties();
    authProperties.load(authPropertiesStream);

    String clientId = authProperties.getProperty("client_id");
    String clientSecret = authProperties.getProperty("client_secret");
    String redirectUri = AppianGlasswareUtils.getAuthCallbackUri();
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
      new NetHttpTransport(), new JacksonFactory(), clientId, clientSecret,
      AppianGlasswareUtils.SCOPES).build();
    return flow.newAuthorizationUrl()
      .setRedirectUri(redirectUri)
      .setState("Auth URL")
      .setApprovalPrompt("force")
      .setAccessType("offline")
      .set("include_granted_scopes", "true")
      .build();
  }

}
