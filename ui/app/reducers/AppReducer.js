import {GET_APPS_FAILURE, GET_APPS_REQUEST, GET_APPS_SUCCESS} from "actions/AppActions";

const INITIAL_STATE = {
  data: [],
  asyncStatus: 'INIT',
};

export default  (isSettings) => (state = INITIAL_STATE, action) => {
  console.log("state before app reducer");
  console.log(state);
  const stateIsSettings = state.isPolicyPage;
  console.log("stateIsSettings");
  console.log(stateIsSettings);
  if(isSettings !== stateIsSettings) return state;
  switch (action.type) {
    case GET_APPS_REQUEST:
      return {...state, asyncStatus: 'PENDING'};

    case GET_APPS_SUCCESS:
      return {data: action.data, asyncStatus: 'SUCCESS'};

    case GET_APPS_FAILURE:
      return {...state, asyncStatus: 'ERROR'};

    default:
      return state;
  }
}

