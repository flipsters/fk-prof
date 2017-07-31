import http from "utils/http";
import {objectToQueryParams} from "utils/UrlUtils";

export const GET_POLICY_CLUSTERS_REQUEST = 'GET_POLICY_CLUSTERS_REQUEST';
export const GET_POLICY_CLUSTERS_SUCCESS = 'GET_POLICY_CLUSTERS_SUCCESS';
export const GET_POLICY_CLUSTERS_FAILURE = 'GET_POLICY_CLUSTERS_FAILURE';

export function getPolicyClustersRequestAction (req) {
  return { type: GET_POLICY_CLUSTERS_REQUEST, ...req };
}

export function getPolicyClustersSuccessAction (data) {
  return { type: GET_POLICY_CLUSTERS_SUCCESS, ...data };
}

export function getPolicyClustersFailureAction ({ error, req }) {
  return { type: GET_POLICY_CLUSTERS_FAILURE, error, req };
}

export default function fetchPolicyClustersAction ({ policyApp, query }) {
  return (dispatch) => {
    if(policyApp) {
      dispatch(getPolicyClustersRequestAction({req: {policyApp}}));
      const queryParams = objectToQueryParams(query);
      const baseUrl = `/api/list/policy/clusterIds/${policyApp}`;
      const url = queryParams ? `${baseUrl}?${queryParams}` : baseUrl;
      return http.get(url)
        .then(res => dispatch(getPolicyClustersSuccessAction({res, req: {policyApp}})))
        .catch(err => dispatch(getPolicyClustersFailureAction({err, req: {policyApp}})));
    }
  };
}
