import {
  GET_POLICY_CLUSTERS_FAILURE,
  GET_POLICY_CLUSTERS_REQUEST,
  GET_POLICY_CLUSTERS_SUCCESS
} from "actions/PolicyClusterActions";

export default function (state = {}, action) {
  switch (action.type) {
    case GET_POLICY_CLUSTERS_REQUEST:
      if (!state[action.req.policyApp]) {
        return {
          ...state,
          [action.req.policyApp]: {
            asyncStatus: 'PENDING',
            data: [],
          },
        };
      } else {
        return state;
      }
    case GET_POLICY_CLUSTERS_SUCCESS:
      return {
        ...state,
        [action.req.policyApp]: {
          asyncStatus: 'SUCCESS',
          data: [...new Set([...state[action.req.policyApp].data, ...action.res])],
        },
      };

    case GET_POLICY_CLUSTERS_FAILURE:
      return {
        ...state,
        [action.req.policyApp]: {
          asyncStatus: 'ERROR',
        },
      };

    default:
      return state;
  }
}
