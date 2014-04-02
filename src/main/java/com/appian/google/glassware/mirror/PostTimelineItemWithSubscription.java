package com.appian.google.glassware.mirror;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.log4j.Logger;

import com.appian.google.glassware.oauth.types.GlassAuthType;
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
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Google Glass Services")
public class PostTimelineItemWithSubscription extends AppianSmartService {

  private static final Logger LOG = Logger.getLogger(PostTimelineItemWithSubscription.class);
  private final SmartServiceContext smartServiceCtx;
  private static final String MISSING_PARAMS_ERROR = "error.missing.parameters.userMessage";
  private static final String USER_AUTH_ERROR = "error.auth.userMessage";
  private static final String USER_EXECUTION_ERROR = "error.execution.userMessage";
  private GlassAuthType authData;
  private String htmlData;
  private String moreInfoHtmlData;
  private String bundleId;
  private Boolean addReplyMenu;
  private String replyMenuName;
  private Boolean addDeleteMenu;
  private String[] customMenuOptions;
  private String timelineId;

  @Override
  public void run() throws SmartServiceException {
    TokenResponse response = null;
    Credential creds = null;
    Mirror service = null;
    TimelineItem card = null;
    TimelineItem moreInfo = null;
    ArrayList<MenuItem> menus = new ArrayList<MenuItem>();
    // Abort if neither text or html is set
    if (htmlData == null || htmlData.isEmpty()) {
      throw createException(new InvalidParameterException(), MISSING_PARAMS_ERROR,
        MISSING_PARAMS_ERROR, null);
    }
    try {
      // Figure out the impacted user and get their credentials for API calls
      creds = AuthUtil.getCredential(authData.getUserId());
      service = MirrorClient.getMirror(creds);
    } catch (IOException e) {
      throw createException(e, USER_AUTH_ERROR, USER_AUTH_ERROR, null);
    }
    card = new TimelineItem();
    card.setHtml(htmlData);
    if (moreInfoHtmlData != null && !moreInfoHtmlData.isEmpty()) {
      card.setIsBundleCover(Boolean.TRUE);
      card.setBundleId(bundleId);
      moreInfo = new TimelineItem();
      moreInfo.setHtml(moreInfoHtmlData);
      moreInfo.setId(bundleId);
      moreInfo.setBundleId(bundleId);
      menus.add(new MenuItem().setAction("REPLY"));
      menus.add(new MenuItem().setAction("DELETE"));
      moreInfo.setMenuItems(menus);
      moreInfo.setNotification(new NotificationConfig().setLevel("DEFAULT"));
      card.setMenuItems(Collections.singletonList(new MenuItem().setAction("DELETE")));
    } else {
      card.setNotification(new NotificationConfig().setLevel("DEFAULT"));
      card.setId(bundleId);
      menus.add(new MenuItem().setAction("REPLY"));
      menus.add(new MenuItem().setAction("DELETE"));
      card.setMenuItems(menus);
    }

    try {
      timelineId = service.timeline().insert(card).execute().getId();
      service.timeline().insert(moreInfo).execute();
    } catch (IOException e) {
      LOG.error(e);
      throw createException(e, USER_EXECUTION_ERROR, USER_EXECUTION_ERROR, null);
    }
  }

  public PostTimelineItemWithSubscription(SmartServiceContext smartServiceCtx) {
    super();
    this.smartServiceCtx = smartServiceCtx;
  }

  @Override
  public void onSave(MessageContainer messages) {
  }

  @Override
  public void validate(MessageContainer messages) {
  }

  @Input(required = Required.ALWAYS)
  @Name("authData")
  public void setAuthData(GlassAuthType val) {
    this.authData = val;
  }

  @Input(required = Required.ALWAYS)
  @Name("htmlData")
  public void setHtmlData(String val) {
    this.htmlData = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("moreInfoHtmlData")
  public void setMoreInfoHtmlData(String val) {
    this.moreInfoHtmlData = val;
  }

  @Input(required = Required.ALWAYS)
  @Name("bundleId")
  public void setBundleId(String val) {
    this.bundleId = val;
  }

  @Input(required = Required.ALWAYS, defaultValue="true")
  @Name("addReplyMenu")
  public void setAddReplyMenu(Boolean val) {
    this.addReplyMenu = val;
  }

  @Input(required = Required.OPTIONAL, defaultValue="REPLY")
  @Name("replyMenuName")
  public void setReplyMenuName(String val) {
    this.replyMenuName = val;
  }

  @Input(required = Required.ALWAYS, defaultValue="true")
  @Name("addDeleteMenu")
  public void setAddDeleteMenu(Boolean val) {
    this.addReplyMenu = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("customMenuOptions")
  public void setCustomMenuOptions(String[] val) {
    this.customMenuOptions = val;
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
