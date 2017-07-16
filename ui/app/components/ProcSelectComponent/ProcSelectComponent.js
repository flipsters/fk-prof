import React, {Component, PropTypes} from "react";
import {connect} from "react-redux";
import Select from "react-select";

import fetchProcsAction from "actions/ProcActions";
import fetchPolicyProcsAction from "actions/PolicyProcActions";
import safeTraverse from "utils/safeTraverse";
import debounce from "utils/debounce";
import styles from "./ProcSelectComponent.css";

const noop = () => {};

class ProcSelectComponent extends Component {
  componentDidMount () {
    const { cluster, app } = this.props;
    if (cluster && app) {
      this.props.isPolicyPage? this.props.getPolicyProcs({ policyCluster: cluster, policyApp: app }): this.props.getProcs({ cluster, app });
    }
  }

  componentWillReceiveProps (nextProps) {
    const didAppChange = nextProps.app !== this.props.app;
    const didClusterChange = nextProps.cluster !== this.props.cluster;
    if (didAppChange || didClusterChange) {
      if(this.props.isPolicyPage) {
        this.props.getProcs({
          app: nextProps.app,
          cluster: nextProps.cluster,
        });
      }else{
        this.props.getPolicyProcs({
          policyApp: nextProps.app,
          policyCluster: nextProps.cluster,
        });
      }
    }
  }

  render () {
    const { onChange, procs, policyProcs } = this.props;
    const finalProcs = this.props.isPolicyPage? policyProcs: procs;
    const procList = finalProcs.asyncStatus === 'SUCCESS'
      ? finalProcs.data.map(c => ({ name: c })) : [];
    const valueOption = this.props.value && { name: this.props.value };
    return (
      <div>
        <label className={styles.label} htmlFor="proc">Process</label>
        <Select
          id="proc"
          clearable={false}
          options={procList}
          onChange={onChange || noop}
          labelKey="name"
          valueKey="name"
          onInputChange={debounce(this.props.isPolicyPage ? this.props.getPolicyProcs: this.props.getProcs, 500)}
          isLoading={finalProcs.asyncStatus === 'PENDING'}
          value={valueOption}
          noResultsText={finalProcs.asyncStatus !== 'PENDING' ? 'No results found!' : 'Searching...'}
          placeholder="Type to search..."
        />
      </div>
    );
  }
}

const mapStateToProps = (state, ownProps) => ({
  procs: safeTraverse(state, ['procs', ownProps.cluster]) || {},
  policyProcs: safeTraverse(state, ['policyProcs', ownProps.cluster]) || {},
});

const mapDispatchToProps = dispatch => ({
  getProcs: params => dispatch(fetchProcsAction(params)),
  getPolicyProcs: params => dispatch(fetchPolicyProcsAction(params)),
});

ProcSelectComponent.propTypes = {
  app: PropTypes.string,
  cluster: PropTypes.string,
  procs: PropTypes.object.isRequired,
  policyProcs: PropTypes.object.isRequired,
  getProcs: PropTypes.func.isRequired,
  getPolicyProcs: PropTypes.func.isRequired,
  onChange: PropTypes.func,
  value: PropTypes.string,
  isPolicyPage: PropTypes.bool.isRequired,
};

export default connect(mapStateToProps, mapDispatchToProps)(ProcSelectComponent);
