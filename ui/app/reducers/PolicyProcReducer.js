import {GET_POLICY_PROCS_FAILURE, GET_POLICY_PROCS_REQUEST, GET_POLICY_PROCS_SUCCESS} from "actions/PolicyProcActions";

export default function (state = {}, action) {
  switch (action.type) {
    case GET_POLICY_PROCS_REQUEST:
      if (!state[action.req.policyCluster]) {
        return {
          ...state,
          [action.req.policyCluster]: {
            asyncStatus: 'PENDING',
            data: [],
          },
        };
      } else {
        return state;
      }

    case GET_POLICY_PROCS_SUCCESS:
      return {
        ...state,
        [action.req.policyCluster]: {
          asyncStatus: 'SUCCESS',
          data: [...new Set([...state[action.req.policyCluster].data, ...action.res])],
        },
      };

    case GET_POLICY_PROCS_FAILURE:
      return {
        ...state,
        [action.req.policyCluster]: {
          asyncStatus: 'ERROR',
        },
      };

    default:
      return state;
  }
}
