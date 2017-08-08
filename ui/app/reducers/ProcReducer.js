import {
  GET_PROCS_REQUEST,
  GET_PROCS_SUCCESS,
  GET_PROCS_FAILURE,
} from 'actions/ProcActions';

export default function (state = {}, action) {
  switch (action.type) {
    case GET_PROCS_REQUEST:
      if (!state[action.req.policyCluster]) {
        return {
          ...state,
          [action.req.cluster]: {
            asyncStatus: 'PENDING',
            data: [],
          },
        };
      } else {
        return state;
      }
    case GET_PROCS_SUCCESS:
      return {
        ...state,
        [action.req.cluster]: {
          asyncStatus: 'SUCCESS',
          data: [...new Set([...state[action.req.cluster].data, ...action.res])],
        },
      };

    case GET_PROCS_FAILURE:
      return {
        ...state,
        [action.req.cluster]: {
          asyncStatus: 'ERROR',
        },
      };

    default: return state;
  }
}
