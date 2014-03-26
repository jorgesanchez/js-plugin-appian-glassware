package com.appian.googleglass.mirror;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.appian.googleglass.oauth.types.GlassAuthType;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.exceptions.SmartServiceException;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceContext;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.TimelineItem;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Google Glass Services")
public class PostTimelineItemActivity extends AppianSmartService {

  private static final Logger LOG = Logger.getLogger(PostTimelineItemActivity.class);
  private static final String MISSING_PARAMS_ERROR = "error.missing.parameters.userMessage";
  private static final String USER_AUTH_ERROR = "error.auth.userMessage";
  private static final String USER_EXECUTION_ERROR = "error.execution.userMessage";
  private final SmartServiceContext smartServiceCtx;
  private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private static JsonFactory JSON_FACTORY = new JacksonFactory();
  private GlassAuthType authData;
  private String text;
  private String html;
  private Boolean addDeleteMenu; //This will actually add a REPLY option as well.
  private String timelineId;

  @Override
  public void run() throws SmartServiceException {
    TokenResponse response = null;
    Credential creds = null;
    Mirror service = null;
    TimelineItem card = null;
    ArrayList<MenuItem> menus = new ArrayList<MenuItem>();
    // Abort if neither text or html is set
    if( (text == null || text.isEmpty()) && (html == null || html.isEmpty()) ) {
      throw createException(new InvalidParameterException(), MISSING_PARAMS_ERROR, MISSING_PARAMS_ERROR, null);
    }
    try {
      // Figure out the impacted user and get their credentials for API calls
      creds = AuthUtil.getCredential(authData.getUserId());
      service = MirrorClient.getMirror(creds);
    } catch (IOException e) {
      throw createException(e, USER_AUTH_ERROR, USER_AUTH_ERROR, null);
    }
    card = new TimelineItem();
    //card.setNotification(new NotificationConfig().setLevel("DEFAULT"));
    if(html != null && !html.isEmpty()) {
      card.setHtml(html);
    } else {
      card.setText(text);
    }
    if(addDeleteMenu != null && addDeleteMenu.booleanValue()) {
      menus.add(new MenuItem().setAction("REPLY"));
      menus.add(new MenuItem().setAction("DELETE"));
      card.setMenuItems(menus);
    }
    try {
      timelineId = service.timeline().insert(card).execute().getId();
    } catch (IOException e) {
      LOG.error(e);
      throw createException(e, USER_EXECUTION_ERROR, USER_EXECUTION_ERROR, null);
    }


  }

  public PostTimelineItemActivity(SmartServiceContext smartServiceCtx) {
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
  @Name("text")
  public void setText(String val) {
    this.text = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("html")
  public void setHtml(String val) {
    this.html = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("addDeleteMenu")
  public void setAddDeleteMenu(Boolean val) {
    this.addDeleteMenu = val;
  }

  @Name("timelineId")
  public String getTimelineId() {
    return timelineId;
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
