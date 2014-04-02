package com.appian.google.glassware.mirror;

public class JdbcData {
  private String jdbcConnectionUri;
  private String jdbcDriverClass;
  private String jdbcSchemaName;
  public String getJdbcConnectionUri() {
    return jdbcConnectionUri;
  }
  public void setJdbcConnectionUri(String jdbcConnectionUri) {
    this.jdbcConnectionUri = jdbcConnectionUri;
  }
  public String getJdbcDriverClass() {
    return jdbcDriverClass;
  }
  public void setJdbcDriverClass(String jdbcDriverClass) {
    this.jdbcDriverClass = jdbcDriverClass;
  }
  public String getJdbcSchemaName() {
    return jdbcSchemaName;
  }
  public void setJdbcSchemaName(String jdbcSchemaName) {
    this.jdbcSchemaName = jdbcSchemaName;
  }
  @Override
  public String toString() {
    return "JdbcData [jdbcConnectionUri=" + jdbcConnectionUri + ", jdbcDriverClass=" +
      jdbcDriverClass + ", jdbcSchemaName=" + jdbcSchemaName + "]";
  }


}
