import { combineReducers } from 'redux';

import apps from 'reducers/AppReducer';
import clusters from 'reducers/ClusterReducer';
import procs from 'reducers/ProcReducer';
import profiles from 'reducers/ProfilesReducer';
import aggregatedProfileData from 'reducers/AggregatedProfileDataReducer';

export default combineReducers({
  apps,
  clusters,
  procs,
  profiles,
  aggregatedProfileData,
});
