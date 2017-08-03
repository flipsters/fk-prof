/**
 * Created by rohit.patiyal on 12/07/17.
 */
import http from 'utils/http';
import {objectToQueryParams} from 'utils/UrlUtils';

export const GET_POLICY_APPS_REQUEST = 'GET_POLICY_APPS_REQUEST';
export const GET_POLICY_APPS_SUCCESS = 'GET_POLICY_APPS_SUCCESS';
export const GET_POLICY_APPS_FAILURE = 'GET_POLICY_APPS_FAILURE';

export function getPolicyAppIdsRequestAction() {
  return {type: GET_POLICY_APPS_REQUEST};
}

export function getPolicyAppIdsSuccessAction(appIds) {
  return {type: GET_POLICY_APPS_SUCCESS, data: appIds};
}

export function getPolicyAppIdsFailureAction(error) {
  return {type: GET_POLICY_APPS_FAILURE, error};
}

export default function fetchPolicyAppIdsAction(prefix) {
  return (dispatch) => {
    dispatch(getPolicyAppIdsRequestAction());
    const queryParams = prefix ? '?' + objectToQueryParams({prefix}) : '';
    const url = `/api/list/policy/appIds${queryParams}`;
    return http.get(url)
      .then(json => dispatch(getPolicyAppIdsSuccessAction(json))) // success, send the data to reducers
      .catch(err => dispatch(getPolicyAppIdsFailureAction(err))); // for error
  };
}
