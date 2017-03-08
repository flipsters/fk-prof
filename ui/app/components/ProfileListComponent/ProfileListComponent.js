import React, { Component, PropTypes } from 'react';
import { connect } from 'react-redux';
import { Link } from 'react-router';
import moment from 'moment';

import { fetchProfilesAction } from 'actions/ProfileActions';
import { objectToQueryParams } from 'utils/UrlUtils';
import Loader from 'components/LoaderComponent';
import safeTraverse from 'utils/safeTraverse';
import dateFormat from 'utils/dateFormat';
import Profile from 'components/ProfileComponent';

import styles from './ProfileListComponent.css';
import 'react-treeview/react-treeview.css';



class ProfileListComponent extends Component {
  componentDidMount () {
    const { app, cluster, proc, start, end } = this.props;
    const startObject = moment(start);
    const endObject = moment(end);
    this.props.fetchProfiles({
      app,
      cluster,
      proc,
      query: {
        start,
        duration: endObject.diff(startObject, 'seconds'),
      },
    });
  }

  componentWillReceiveProps (nextProps) {
    const { app, cluster, proc, start, end } = nextProps;
    const didAppChange = app !== this.props.app;
    const didClusterChange = cluster !== this.props.cluster;
    const didProcChange = proc !== this.props.proc;
    const didStartDateChange = start !== this.props.start;
    if (didAppChange || didClusterChange || didProcChange || didStartDateChange) {
      const startObject = moment(start);
      const endObject = moment(end);
      this.props.fetchProfiles({
        app,
        cluster,
        proc,
        query: {
          start,
          duration: endObject.diff(startObject, 'seconds'),
        },
      });
    }
  }

  render () {
    const { profiles, app, cluster, proc } = this.props;
    if (!profiles) return null;
    if (profiles.asyncStatus === 'PENDING') {
      return (
        <Loader />
      );
    }

    if (profiles.asyncStatus === 'SUCCESS') {
      if (!profiles.data.succeeded.length) {
        return <h2 className={styles.error}>No profiles found</h2>;
      }

      const sortedProfiles = profiles.data.succeeded.slice().sort((a, b) => {
        return new Date(b.start).getTime() - new Date(a.start).getTime();
      });
      return (
        <div style={{ maxHeight: '70vh', overflow: 'auto' }}>
          {sortedProfiles.map((profile) => {
            const date = new Date(profile.start);
            return (
              <Profile
                key={profile.start}
                heading={dateFormat(date, 'profileList')}
                traces={profile.traces}
                start={profile.start}
              />
            );
          })}
        </div>
      );
    }
    return null;
  }
}

ProfileListComponent.propTypes = {
  app: PropTypes.string.isRequired,
  cluster: PropTypes.string.isRequired,
  proc: PropTypes.string.isRequired,
  start: PropTypes.string.isRequired,
  end: PropTypes.number.isRequired,
  fetchProfiles: PropTypes.func.isRequired,
  profiles: PropTypes.object.isRequired,
  getTraces: PropTypes.func.isRequired,
  traces: PropTypes.object,
};

const mapStateToProps = state => ({
  profiles: state.profiles || {},
  traces: safeTraverse(state, ['traces']),
});

const mapDispatchToProps = dispatch => ({
  fetchProfiles: params => dispatch(fetchProfilesAction(params)),
  getTraces: params => dispatch(fetchTracesAction(params)),
});

export default connect(mapStateToProps, mapDispatchToProps)(ProfileListComponent);
