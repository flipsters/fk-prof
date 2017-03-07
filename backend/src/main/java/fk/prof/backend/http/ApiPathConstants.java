package fk.prof.backend.http;

public final class ApiPathConstants {
  public static final String AGGREGATOR_POST_PROFILE = "/profile";
  public static final String LEADER_POST_LOAD = "/leader/load";
  public static final String LEADER_GET_WORK = "/leader/work";
  public static final String LEADER_PUT_ASSOCIATION = "/leader/association";
  public static final String BACKEND_PUT_ASSOCIATION = "/association";
  public static final String BACKEND_GET_POLICIES_GIVEN_APPID = "/policies/:appId";

  private ApiPathConstants() {
  }

}
