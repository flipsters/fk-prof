import React from "react";
import {connect} from "react-redux";
import Select from "react-select";

import fetchAppsAction from "actions/AppActions";
import fetchPolicyAppsAction from "actions/PolicyAppActions";
import debounce from "utils/debounce";
import styles from "./AppSelectComponent.css";

const noop = () => {};

class AppSelectComponent extends React.Component {
  componentDidMount () {
    this.props.isSettings ? this.props.getPolicyApps(''): this.props.getApps('');
  }
  componentWillReceiveProps (nextProps) {
    if(nextProps.isSettings !== this.props.isSettings) {
      nextProps.isSettings ? this.props.getPolicyApps('') : this.props.getApps('');
    }
  }

  render () {
    const finalApps = this.props.isSettings ? this.props.policyApps: this.props.apps;
    const options = finalApps.asyncStatus === 'SUCCESS'
      ? finalApps.data.map(a => ({ name: a })) : [];

    const noResultsText = finalApps.asyncStatus === 'SUCCESS'
      && finalApps.data.length === 0 ? 'No results found!' : 'Searching...';
    const valueOption = this.props.value && { name: this.props.value };
    return (
      <div>
        <label className={styles.label} htmlFor="appid">App</label>
        <Select
          clearable={false}
          id="appid"
          options={options}
          value={valueOption}
          onChange={this.props.onChange || noop}
          labelKey="name"
          valueKey="name"
          onInputChange={debounce(this.props.isSettings ? this.props.getPolicyApps: this.props.getApps, 500)}
          isLoading={finalApps.asyncStatus === 'PENDING'}
          noResultsText={noResultsText}
          placeholder="Type to search..."
        />
      </div>
    );
  }
}

const mapStateToProps = state => ({ apps: state.apps, policyApps: state.policyApps });
const mapDispatchToProps = dispatch => ({
  getApps: prefix => dispatch(fetchAppsAction(prefix)),
  getPolicyApps: prefix => dispatch(fetchPolicyAppsAction(prefix))
});

export default connect(mapStateToProps, mapDispatchToProps)(AppSelectComponent);

