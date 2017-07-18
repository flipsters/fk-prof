import React from "react";
import {withRouter} from "react-router";
import DateTime from "react-datetime";

import AppSelect from "components/AppSelectComponent";
import ClusterSelect from "components/ClusterSelectComponent";
import ProcSelect from "components/ProcSelectComponent";
import ProfileList from "components/ProfileListComponent";

import styles from "./AppComponent.scss";
import PolicyComponent from "../PolicyComponent/PolicyComponent";

const AppComponent = (props) => {
  const selectedApp = props.location.query.app;
  const selectedCluster = props.location.query.cluster;
  const selectedProc = props.location.query.proc;
  const start = props.location.query.start;
  const end = start ? (new Date(start).getTime() + (24 * 3600 * 1000)) : '';

  const updateQueryParams = ({ pathname = '/', query }) => props.router.push({ pathname, query });
  const updatePolicyQueryParams = ({ pathname = '/profiler/policy', query }) => props.router.push({ pathname, query });
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
  const isPolicyPage = props.location.pathname.includes('policy');
  const columnWidth = isPolicyPage ? 4:3;
  return (
    <div>
       <div>
        <div className="mdl-grid">
          <div className={`mdl-cell mdl-cell--${columnWidth}-col`}>
            <AppSelect
              onChange={isPolicyPage? updatePolicyAppQueryParam: updateAppQueryParam}
              value={selectedApp}
              isPolicyPage={isPolicyPage}
            />
          </div>
          <div className={`mdl-cell mdl-cell--${columnWidth}-col`}>
            {selectedApp && (
              <ClusterSelect
                app={selectedApp}
                onChange={isPolicyPage? updatePolicyClusterQueryParam: updateClusterQueryParam}
                value={selectedCluster}
                isPolicyPage={isPolicyPage}
              />
            )}
          </div>
          <div className={`mdl-cell mdl-cell--${columnWidth}-col`}>
            {selectedApp && selectedCluster && (
              <ProcSelect
                app={selectedApp}
                cluster={selectedCluster}
                onChange={isPolicyPage? updatePolicyProcQueryParam: updateProcQueryParam}
                value={selectedProc}
                isPolicyPage={isPolicyPage}
              />
            )}
          </div>
          {!isPolicyPage && selectedApp && selectedCluster && selectedProc && (
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
        {!isPolicyPage && selectedProc && start && end && (
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
         {isPolicyPage && selectedProc && (
           <div className="mdl-grid">
             <div className="mdl-layout-spacer"/>
             <PolicyComponent
               app={selectedApp}
               cluster={selectedCluster}
               proc={selectedProc}
               start={start}
               end={end}
             />
           <div className="mdl-layout-spacer"/>

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
