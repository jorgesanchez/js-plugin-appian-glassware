/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.appian.googleglass.mirror.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.appian.googleglass.mirror.AppianGlasswareUtils;
import com.appian.googleglass.mirror.AuthUtil;
import com.appian.googleglass.mirror.MirrorClient;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.mirror.model.Command;
import com.google.api.services.mirror.model.Contact;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.Subscription;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.common.collect.Lists;

/**
 * Utility functions used when users first authenticate with this service
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NewUserBootstrapper {
  private static final Logger LOG = Logger.getLogger(NewUserBootstrapper.class.getSimpleName());

  /**
   * Bootstrap a new user. Do all of the typical actions for a new user:
   * <ul>
   * <li>Creating a timeline subscription</li>
   * <li>Inserting a contact</li>
   * <li>Sending the user a welcome message</li>
   * </ul>
   */
  public static void bootstrapNewUser(String userId) throws IOException {
    Credential credential = AuthUtil.newAuthorizationCodeFlow().loadCredential(userId);

    // Create contact
    Contact appianGlasswareContact = new Contact();
    appianGlasswareContact.setId("com.appian.glassware.contact");
    appianGlasswareContact.setDisplayName("Appian Glassware Contact");
    appianGlasswareContact.setImageUrls(Lists.newArrayList(
      "https://dl.dropboxusercontent.com/s/wd06ss2p16x1y75/users_male_olive.png"));
    appianGlasswareContact.setAcceptCommands(Lists.newArrayList(
      new Command().setType("TAKE_A_NOTE"), new Command().setType("POST_AN_UPDATE")));
    Contact insertedContact = MirrorClient.insertContact(credential, appianGlasswareContact);
    LOG.info("Bootstrapper inserted contact " + insertedContact.getId() + " for user " + userId);

    try {
      // Subscribe to timeline updates
      Subscription subscription = MirrorClient.insertSubscription(credential,
        AppianGlasswareUtils.getNotificationCallbackUri(), userId, "timeline");
      LOG.info("Bootstrapper inserted subscription " + subscription.getId() + " for user " + userId);
    } catch (GoogleJsonResponseException e) {
      LOG.warning("Failed to create timeline subscription. Might be running on " +
        "localhost. Details:" + e.getDetails().toPrettyString());
    }

    // Send welcome timeline item
    TimelineItem timelineItem = new TimelineItem();
    timelineItem.setText("Welcome to Appian Glassware. You have been subscribed for notifications.");
    ArrayList<MenuItem> menus = new ArrayList<MenuItem>();
    AppianGlasswareUtils.createMenus(menus, true, true, true);
    timelineItem.setMenuItems(menus);
    timelineItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
    TimelineItem insertedItem = MirrorClient.insertTimelineItem(credential, timelineItem);
    LOG.info("Bootstrapper inserted welcome message " + insertedItem.getId() + " for user " +
      userId);
  }
}
