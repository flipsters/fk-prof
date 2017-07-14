import React from "react";
import {withRouter} from "react-router";
import DateTime from "react-datetime";

import AppSelect from "components/AppSelectComponent";
import ClusterSelect from "components/ClusterSelectComponent";
import ProcSelect from "components/ProcSelectComponent";
import ProfileList from "components/ProfileListComponent";

import styles from "./AppComponent.scss";

const AppComponent = (props) => {
  const selectedApp = props.location.query.app;
  const selectedCluster = props.location.query.cluster;
  const selectedProc = props.location.query.proc;
  const start = props.location.query.start;
  const end = start ? (new Date(start).getTime() + (24 * 3600 * 1000)) : '';

  const updateQueryParams = ({ pathname = '/', query }) => props.router.push({ pathname, query });
  const updatePolicyQueryParams = ({ pathname = '/profiler/settings', query }) => props.router.push({ pathname, query });
  const updateAppQueryParam = o => updateQueryParams({ query: { app: o.name } });
  const updatePolicyAppQueryParam = o => updatePolicyQueryParams({ query: { app: o.name } });

  const updateClusterQueryParam = (o) => {
    updateQueryParams({ query: { app: selectedApp, cluster: o.name } });
  };
  const updatePolicyClusterQueryParam = (o) => {
    updatePolicyQueryParams({ query: { app: selectedApp, cluster: o.name } });
  };
  const updateProcQueryParam = (o) => {
    updateQueryParams({ query: { app: selectedApp, cluster: selectedCluster, proc: o.name } });
  };
  const updatePolicyProcQueryParam = (o) => {
    updatePolicyQueryParams({ query: { app: selectedApp, cluster: selectedCluster, proc: o.name } });
  };
  const updateStartTime = (dateTimeObject) => {
    updateQueryParams({
      query: {
        app: selectedApp,
        cluster: selectedCluster,
        proc: selectedProc,
        start: dateTimeObject.toISOString(),
      },
    });
  };
  const isSettings = props.location.pathname.includes('settings');
  return (
    <div>
       <div>
        <div className="mdl-grid">
          <div className="mdl-cell mdl-cell--3-col">
            <AppSelect
              onChange={isSettings? updatePolicyAppQueryParam: updateAppQueryParam}
              value={selectedApp}
              isSettings={isSettings}
            />
          </div>
          <div className="mdl-cell mdl-cell--3-col">
            {selectedApp && (
              <ClusterSelect
                app={selectedApp}
                onChange={isSettings? updatePolicyClusterQueryParam: updateClusterQueryParam}
                value={selectedCluster}
                isSettings={isSettings}
              />
            )}
          </div>
          <div className="mdl-cell mdl-cell--3-col">
            {selectedApp && selectedCluster && (
              <ProcSelect
                app={selectedApp}
                cluster={selectedCluster}
                onChange={isSettings? updatePolicyProcQueryParam: updateProcQueryParam}
                value={selectedProc}
                isSettings={isSettings}
              />
            )}
          </div>
          {!isSettings && selectedApp && selectedCluster && selectedProc && (
              <div className="mdl-cell mdl-cell--3-col">
                <label className={styles['label']} htmlFor="startTime">Date</label>
                <div>
                  <DateTime
                    className={styles['date-time']}
                    defaultValue={start ? new Date(start) : ''}
                    onChange={updateStartTime}
                    dateFormat="DD-MM-YYYY"
                    timeFormat={false}
                  />
                </div>
              </div>
            )
          }
        </div>
        {!isSettings && selectedProc && start && end && (
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--3-col">
                <ProfileList
                  app={selectedApp}
                  cluster={selectedCluster}
                  proc={selectedProc}
                  start={start}
                  end={end}
                />
              </div>
              <div className="mdl-cell mdl-cell--9-col">
                {props.children || <h2 className={styles.ingrained}>Select a Trace</h2>}
              </div>
            </div>
          )}
      </div>

    </div>
  );
};

AppComponent.propTypes = {
  location: React.PropTypes.object,
  children: React.PropTypes.node,
};

export default withRouter(AppComponent);
