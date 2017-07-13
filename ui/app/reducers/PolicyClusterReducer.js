import {
  GET_POLICY_CLUSTERS_FAILURE,
  GET_POLICY_CLUSTERS_REQUEST,
  GET_POLICY_CLUSTERS_SUCCESS
} from "actions/PolicyClusterActions";

export default function (state = {}, action) {
  switch (action.type) {
    case GET_POLICY_CLUSTERS_REQUEST:
      return {
        ...state,
        [action.req.policyApp] : {
          asyncStatus: 'PENDING',
          data: [],
        },
      };

    case GET_POLICY_CLUSTERS_SUCCESS:
      return {
        ...state,
        [action.req.policyApp]: {
          asyncStatus: 'SUCCESS',
          data: action.res,
        },
      };

    case GET_POLICY_CLUSTERS_FAILURE:
      return {
        ...state,
        [action.req.policyApp]: {
          asyncStatus: 'ERROR',
        },
      };

    default: return state;
  }
}
