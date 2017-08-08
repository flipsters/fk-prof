import {
  GET_CLUSTERS_REQUEST,
  GET_CLUSTERS_SUCCESS,
  GET_CLUSTERS_FAILURE,
} from 'actions/ClusterActions';

export default function (state = {}, action) {
  switch (action.type) {
    case GET_CLUSTERS_REQUEST:
      if (!state[action.req.app]) {
        return {
          ...state,
          [action.req.app]: {
            asyncStatus: 'PENDING',
            data: []
          },
        };
      } else {
        return state;
      }
    case GET_CLUSTERS_SUCCESS:
      return {
        ...state,
        [action.req.app]: {
          asyncStatus: 'SUCCESS',
          data: [...new Set([...state[action.req.app].data, ...action.res])],
        },
      };

    case GET_CLUSTERS_FAILURE:
      return {
        ...state,
        [action.req.app]: {
          asyncStatus: 'ERROR',
        },
      };

    default: return state;
  }
}
