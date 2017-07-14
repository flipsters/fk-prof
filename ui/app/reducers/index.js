import {combineReducers} from "redux";

import apps from "reducers/AppReducer";
import clusters from "reducers/ClusterReducer";
import procs from "reducers/ProcReducer";
import profiles from "reducers/ProfilesReducer";
import aggregatedProfileData from "reducers/AggregatedProfileDataReducer";
import policyClusters from "reducers/PolicyClusterReducer";
import policyProcs from "reducers/PolicyProcReducer";
import isPolicyPage from "reducers/IsPolicyPageReducer";

export default combineReducers({
  apps: apps({isSettings: false}),
  policyApps: apps({isSettings: true}),
  clusters,
  procs,
  profiles,
  aggregatedProfileData,
  policyClusters,
  policyProcs,
  isPolicyPage,
});
