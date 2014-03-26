package com.appian.googleglass.mirror.auth;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.appian.googleglass.mirror.AppianGlasswareUtils;
import com.appian.googleglass.mirror.AuthUtil;
import com.appian.googleglass.oauth.types.GlassAuthType;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.exceptions.SmartServiceException;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceContext;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Google Glass Services")
public class StoreGlasswareCredentialsActivity extends AppianSmartService {

  private static final Logger LOG = Logger.getLogger(StoreGlasswareCredentialsActivity.class);
  private static final String USER_EXECUTION_ERROR = "error.execution.userMessage";
  private final SmartServiceContext smartServiceCtx;
  private GlassAuthType authData;
  private String authCode;
  private GlassAuthType updatedAuthData;

  @Override
  public void run() throws SmartServiceException {
    AuthorizationCodeFlow flow;
    TokenResponse tokenResponse;
    try {
      flow = AuthUtil.newAuthorizationCodeFlow();
      tokenResponse = flow.newTokenRequest(authCode)
        .setRedirectUri(AppianGlasswareUtils.getAuthCallbackUri())
        .execute();

      // Extract the Google User ID from the ID token in the auth response
      String userId = ((GoogleTokenResponse) tokenResponse).parseIdToken().getPayload().getUserId();
      LOG.info("Code exchange worked. User " + userId + " logged in.");
      flow.createAndStoreCredential(tokenResponse, userId);
      // The dance is done. Do our bootstrapping stuff for this user
      NewUserBootstrapper.bootstrapNewUser(userId);
      updatedAuthData = new GlassAuthType();
      updatedAuthData.setClientId(flow.getClientId());
      updatedAuthData.setAppianUserId(authData.getUserId());
      updatedAuthData.setAccessToken(tokenResponse.getAccessToken());
      updatedAuthData.setRefreshToken(tokenResponse.getRefreshToken());
      updatedAuthData.setUserId(userId);
    } catch (IOException e) {
      LOG.error(e);
      throw createException(e, USER_EXECUTION_ERROR, USER_EXECUTION_ERROR, null);
    }

  }

  public StoreGlasswareCredentialsActivity(SmartServiceContext smartServiceCtx) {
    super();
    this.smartServiceCtx = smartServiceCtx;
  }

  @Override
  public void onSave(MessageContainer messages) {
  }

  @Override
  public void validate(MessageContainer messages) {
  }

  @Input(required = Required.OPTIONAL)
  @Name("authData")
  public void setAuthData(GlassAuthType val) {
    this.authData = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("authCode")
  public void setAuthCode(String val) {
    this.authCode = val;
  }

  @Name("updatedAuthData")
  public GlassAuthType getUpdatedAuthData() {
    return updatedAuthData;
  }

  private SmartServiceException createException(Throwable t, String userKey, String alertKey,
    Object args[]) {
    SmartServiceException.Builder b = new SmartServiceException.Builder(getClass(), t);
    b.userMessage(userKey, args);
    b.alertMessage(alertKey, args);
    b.addCauseToUserMessageArgs();
    b.addCauseToAlertMessageArgs();
    return b.build();
  }
}
