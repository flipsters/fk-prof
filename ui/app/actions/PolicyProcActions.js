import http from "utils/http";
import {objectToQueryParams} from "utils/UrlUtils";

export const GET_POLICY_PROCS_REQUEST = 'GET_POLICY_PROCS_REQUEST';
export const GET_POLICY_PROCS_SUCCESS = 'GET_POLICY_PROCS_SUCCESS';
export const GET_POLICY_PROCS_FAILURE = 'GET_POLICY_PROCS_FAILURE';

export function getPolicyProcsRequestAction(req) {
  return {type: GET_POLICY_PROCS_REQUEST, ...req};
}

export function getPolicyProcsSuccessAction(data) {
  return {type: GET_POLICY_PROCS_SUCCESS, ...data};
}

export function getPolicyProcsFailureAction({error, req}) {
  return {type: GET_POLICY_PROCS_FAILURE, error, req};
}

export default function fetchPolicyProcsAction(policyApp, policyCluster, prefix) {
  return (dispatch) => {
    dispatch(getPolicyProcsRequestAction({req: {policyApp, policyCluster}}));
    const queryParams = prefix ? '?' + objectToQueryParams({prefix}) : '';
    const baseUrl = `/api/list/policy/procNames/${policyApp}/${policyCluster}`;
    const url = `${baseUrl}${queryParams}`;
    return http.get(url)
      .then(json => dispatch(getPolicyProcsSuccessAction({res: json, req: {policyApp, policyCluster}})))
      .catch(err => dispatch(getPolicyProcsFailureAction({err, req: {policyApp, policyCluster}})));
  };
}
