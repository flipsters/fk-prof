import {GET_CLUSTERS_FAILURE, GET_CLUSTERS_REQUEST, GET_CLUSTERS_SUCCESS,} from 'actions/ClusterActions';

export default function (state = {}, action) {
  switch (action.type) {
    case GET_CLUSTERS_REQUEST:
      return {
        ...state,
        [action.req.app]: {
          asyncStatus: 'PENDING',
          data: [],
        },
      };

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
