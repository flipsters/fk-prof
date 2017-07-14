import {combineReducers} from "redux";

import apps from "reducers/AppReducer";
import clusters from "reducers/ClusterReducer";
import procs from "reducers/ProcReducer";
import profiles from "reducers/ProfilesReducer";
import aggregatedProfileData from "reducers/AggregatedProfileDataReducer";
import policyApps from "reducers/PolicyAppReducer";
import policyClusters from "reducers/PolicyClusterReducer";
import policyProcs from "reducers/PolicyProcReducer";

export default combineReducers({
  apps,
  clusters,
  procs,
  profiles,
  aggregatedProfileData,
  policyApps,
  policyClusters,
  policyProcs,
});
