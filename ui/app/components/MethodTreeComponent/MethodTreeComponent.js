import React, {PropTypes} from 'react';
import {withRouter} from 'react-router';
import http from 'utils/http';
import {objectToQueryParams} from 'utils/UrlUtils';
import Loader from 'components/LoaderComponent';


export class MethodTreeComponent extends React.Component {

  constructor(props) {
    super(props);
    this.state = {asyncStatus: 'PENDING', respTree: {}};
  }

  static workTypeMap = {
    cpu_sample_work: 'cpu-sampling',
  };

  componentDidMount() {
    const {app, cluster, proc, workType, selectedWorkType, profileStart, profileDuration} = this.props.location.query;
    const queryParams = objectToQueryParams({start: profileStart, duration: profileDuration, autoExpand: false});
    const url = `/api/callers/${app}/${cluster}/${proc}/${MethodTreeComponent.workTypeMap[workType || selectedWorkType]}/${this.props.traceName}` + ((queryParams) ? '?' + queryParams : '');
    console.log("URL for Post is ", url);
    http.post(url, [])
      .then(resp => {
        this.setState({asyncStatus: 'SUCCESS', respTree: resp});
      })
      .catch(err => {
        this.setState({asyncStatus: 'ERROR'});
      });
  }

  render() {
    if (!this.state.asyncStatus) return null;

    if (this.state.asyncStatus === 'PENDING') {
      return (
        <div>
          <h4 style={{textAlign: 'center'}}>Please wait, coming right up!</h4>
          <Loader/>
        </div>
      );
    }

    if (this.state.asyncStatus === 'ERROR') {
      return (
        <div1>
          <h2>Failed to fetch the data. Please refresh or try again later</h2>
        </div1>
      );
    }
    const StackLineRenderer = ({name, children, indent}) => {
      return (<div>
        <span style={{marginLeft: (indent*20).toString() + 'px'}}>{name}</span>
        {children && Object.values(children)
          .map(ckv => {
            return (<StackLineRenderer key={ckv.data[0]}
                                       name={this.state.respTree['method_lookup'][ckv.data[0]]}
                                       children={ckv.chld} indent={indent + 1}/>);
          })}
      </div>);
    };
    const rootKeys = Object.keys(this.state.respTree).filter(k => k !== 'method_lookup');
    return (<div>{rootKeys.map(rk => this.state.respTree[rk]).map(rkv => {
      return (<StackLineRenderer key={rkv.data[0]} name={this.state.respTree['method_lookup'][rkv.data[0]]}
                                 children={rkv.chld} indent={0}/>)
    })}</div>);
  }
}

MethodTreeComponent.propTypes = {
  location: PropTypes.shape({
    query: PropTypes.shape({
      app: PropTypes.string,
      cluster: PropTypes.string,
      proc: PropTypes.string,
      workType: PropTypes.string,
      profileStart: PropTypes.string,
      selectedWorkType: PropTypes.string,
      profileDuration: PropTypes.string
    }),
  }),
};

export default withRouter(MethodTreeComponent);
