import React from "react";
import {connect} from "react-redux";
import Select from "react-select";

import fetchAppIdsAction from "actions/AppActions";
import debounce from "utils/debounce";
import styles from "./AppSelectComponent.css";

const noop = () => {};

class AppSelectComponent extends React.Component {
  componentDidMount () {
    this.props.getApps('')(this.props.isSettings);
  }

  render () {
    const options = this.props.apps.asyncStatus === 'SUCCESS'
      ? this.props.apps.data.map(a => ({ name: a })) : [];

    const noResultsText = this.props.apps.asyncStatus === 'SUCCESS'
      && this.props.apps.data.length === 0 ? 'No results found!' : 'Searching...';
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
          onInputChange={debounce((e) => this.props.getApps(e)(this.props.isSettings), 500)}
          isLoading={this.props.apps.asyncStatus === 'PENDING'}
          noResultsText={noResultsText}
          placeholder="Type to search..."
        />
      </div>
    );
  }
}

const mapStateToProps = state => ({ apps: state.apps});
const mapDispatchToProps = dispatch => ({
  getApps: prefix => isSettings => dispatch(fetchAppIdsAction(prefix, isSettings)),
});

export default connect(mapStateToProps, mapDispatchToProps)(AppSelectComponent);

