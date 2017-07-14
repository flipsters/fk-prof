import {IS_POLICY_PAGE} from "actions/PolicyPageAction";

const INITIAL_STATE = false;
export default (state = INITIAL_STATE, action) => {
      switch (action.type) {
        case IS_POLICY_PAGE:
          return action.isSettings;
        default:
          return state;
      }
}

