/**
 * Created by rohit.patiyal on 12/07/17.
 */
import {
  GET_POLICY_APPS_REQUEST,
  GET_POLICY_APPS_SUCCESS,
  GET_POLICY_APPS_FAILURE,
} from 'actions/PolicyAppActions';

const INITIAL_STATE = {
  data: [],
  asyncStatus: 'INIT',
};

export default function (state = INITIAL_STATE, action) {
  switch (action.type) {
    case GET_POLICY_APPS_REQUEST:
      return { ...state, asyncStatus: 'PENDING' };

    case GET_POLICY_APPS_SUCCESS:
      return { data: action.data, asyncStatus: 'SUCCESS' };

    case GET_POLICY_APPS_FAILURE:
      return { ...state, asyncStatus: 'ERROR' };

    default: return state;
  }
}
