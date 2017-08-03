import {GET_PROCS_FAILURE, GET_PROCS_REQUEST, GET_PROCS_SUCCESS,} from 'actions/ProcActions';

export default function (state = {}, action) {
  switch (action.type) {
    case GET_PROCS_REQUEST:
      return {
        ...state,
        [action.req.cluster]: {
          asyncStatus: 'PENDING',
          data: [],
        },
      };

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
