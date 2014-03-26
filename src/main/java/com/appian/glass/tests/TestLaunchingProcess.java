package com.appian.glass.tests;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.cfg.ConfigurationLoader;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.common.exceptions.InvalidPriorityException;
import com.appiancorp.suiteapi.common.exceptions.InvalidStateException;
import com.appiancorp.suiteapi.common.exceptions.InvalidUserException;
import com.appiancorp.suiteapi.common.exceptions.InvalidVersionException;
import com.appiancorp.suiteapi.common.exceptions.StorageLimitException;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.process.ProcessStartConfig;
import com.appiancorp.suiteapi.process.ProcessVariable;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.NamedTypedValue;

public class TestLaunchingProcess {
  private static final String PMUUID = "0005d8b0-c00d-8000-4595-010000010000";

  public static void main(String[] args) throws InvalidPriorityException, InvalidVersionException,
    InvalidStateException, StorageLimitException, InvalidUserException, IllegalArgumentException,
    Exception {
    ConfigurationLoader.initializeConfigurations();
    ServiceContext sc = ServiceLocator.getAdministratorServiceContext();
    ProcessDesignService pds = ServiceLocator.getProcessDesignService(sc);
    Long pmId = pds.getProcessModelIdByUuid(PMUUID);
    ProcessStartConfig psc = new ProcessStartConfig();
    ProcessVariable appianUserId = new ProcessVariable(new NamedTypedValue("appianUserId",
      (long) AppianType.USERNAME, "jorge"));
    appianUserId.setParameter(true);
    appianUserId.setRequired(true);
    ProcessVariable feedEventEntryId = new ProcessVariable(new NamedTypedValue("feedEventEntryId",
      (long) AppianType.STRING, "x-105"));
    feedEventEntryId.setParameter(true);
    feedEventEntryId.setRequired(true);
    ProcessVariable userComments = new ProcessVariable(new NamedTypedValue("userComments",
      (long) AppianType.STRING, "these are some comments from an App"));
    userComments.setParameter(true);
    userComments.setRequired(true);
    ProcessVariable[] processParams = new ProcessVariable[] {appianUserId, feedEventEntryId,
      userComments};
    psc.setProcessParameters(processParams);
    pds.initiateProcess(pmId, psc);
  }


}
