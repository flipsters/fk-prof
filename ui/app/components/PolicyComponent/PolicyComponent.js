import React from "react";
import Loader from '../LoaderComponent/LoaderComponent';

import http from 'utils/http';

export default class PolicyComponent extends React.Component {

  constructor(props) {
    super(props);
    this.handleScheduleChange = this.handleScheduleChange.bind(this);
    this.handleDescriptionChange = this.handleDescriptionChange.bind(this);
    this.handleSubmitClick = this.handleSubmitClick.bind(this);
    this.handleWorkChange = this.handleWorkChange.bind(this);
    this.getPolicy = this.getPolicy.bind(this);
    this.postPolicy = this.postPolicy.bind(this);
    this.state = {
      query: {
        type: "GET",
        state: "INIT"
      },
      json: null,
      err: null,
      msg: "Failed to fetch the data. Please refresh or try again later"
    };
  }

  componentDidMount() {
    this.getPolicy();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.proc !== this.props.proc) {
      this.getPolicy();
    }
    componentHandler.upgradeDom(); // eslint-disable-line
  }

  getPolicy() {
    this.setState({
      query: {
        type: "GET",
        state: "PENDING"
      }
    });
    const url = '../api/policy/' + this.props.app + '/' + this.props.cluster + '/' + this.props.proc;
    http.get(url).then(json => {
      this.setState({
        msg: "Policy found",
        query: {
          type: "GET",
          state: "SUCCESS"
        },
        json,
        err: {}
      })
    }).catch(err => {
      if (err.status === 404) {
        this.setState({
          msg: "No policy found, create a new one",
          query: {
            type: "GET",
            state: "SUCCESS"
          },
          err,
          json: {
            version: -1,
            policyDetails: {
              createdAt: "",
              modifiedAt: "",
              modifiedBy: "Anonymous",
              policy: {
                description: "",
                schedule: {
                  duration: 120,
                  pgCovPct: 10,
                  after: 0
                },
                work: [{
                  wType: "cpu_sample_work",
                  cpuSample: {
                    frequency: 50,
                    maxFrames: 64
                  }
                }]
              }
            }
          }
        })
      } else {
        this.setState({
          query: {
            type: "GET",
            state: "FAILURE"
          },
          err,
        })
      }
    });
  }

  putPolicy() {
    this.setState({
      query: {
        type: "PUT",
        state: "PENDING"
      }
    });
    const url = '../api/policy/' + this.props.app + '/' + this.props.cluster + '/' + this.props.proc;
    http.put(url, this.state.json).then(json => {
      this.setState({
        query: {
          type: "PUT",
          state: "SUCCESS"
        },
        json,
        err: {},
        msg: "Policy has been updated"
      })
    }).catch(err => {
      this.setState({
        query: {
          type: "PUT",
          state: "FAILURE"
        },
        err,
        msg: "There was a problem updating your policy, try again later"
      })
    });
  }

  postPolicy() {
    this.setState({
      query: {
        type: "POST",
        state: "PENDING"
      }
    });
    const url = '../api/policy/' + this.props.app + '/' + this.props.cluster + '/' + this.props.proc;
    http.post(this.url, this.state.json).then(json => {
      this.setState({
        query: {
          type: "POST",
          state: "SUCCESS"
        },
        json,
        err: {},
        msg: "Policy has been created"
      })
    }).catch(err => {
      this.setState({
        query: {
          type: "POST",
          state: "FAILURE"
        },
        err,
        msg: "There was a problem creating your policy, try again later"
      })
    });
  }

  handleScheduleChange(e) {
    const target = e.target;
    const value = parseInt(target.value);
    const id = target.id;
    const schedule = {...this.state.json.policyDetails.policy.schedule, [id]: value};
    this.setState((prevState) => ({
      json: {
        version: prevState.json.version,
        policyDetails: {...prevState.json.policyDetails, policy: {...prevState.json.policyDetails.policy, schedule}}
      },
    }));
  }

  handleDescriptionChange(e) {
    const value = e.target.value;
    this.setState((prevState) => ({
      json: {
        version: prevState.json.version,
        policyDetails: {
          ...prevState.json.policyDetails,
          policy: {...prevState.json.policyDetails.policy, description: value}
        }
      },
    }));
  }

  handleWorkChange(e) {
    const prevWorks = this.state.json.policyDetails.policy.work;
    const target = e.target;
    const value = parseInt(target.value);

    const wType = target.name;
    const [wKey, attribute] = target.id.split('_');

    const work = prevWorks.filter((work) => {
      return work.wType === wType;
    });

    let w = {};
    if (work.length === 0) {
      w = {
        wType,
        [wKey]: {
          [attribute]: value
        }
      }
    } else {
      w = {
        wType,
        [wKey]: {...work[0][wKey], [attribute]: value}
      }
    }
    if (Object.values(w[wKey]).every((el) => isNaN(el))) { //Remove work type element from work array if all of the attributes are empty
      this.setState((prevState) => ({
        json: {
          version: prevState.json.version,
          policyDetails: {
            ...prevState.json.policyDetails, policy: {
              ...prevState.json.policyDetails.policy, work: [...prevWorks.filter((w) => {
                return w.wType !== wType
              })]
            }
          },
        }
      }));
    } else {
      this.setState((prevState) => ({
          json: {
            version: prevState.json.version,
            policyDetails: {
              ...prevState.json.policyDetails, policy: {
                ...prevState.json.policyDetails.policy, work: [...prevWorks.filter((w) => {
                  return w.wType !== wType
                }), w]
              }
            },
          }
        })
      );
    }
  }

  handleSubmitClick(e) {
    'use strict';
    if ((this.state.query.type === 'POST' || this.state.query.type === 'PUT') && this.state.query.state === 'PENDING') {
      this.setState({
        msg: "Please wait, your previous policy change is still pending"
      });
    } else {
      let data = {};
      if (document.querySelectorAll(":invalid").length !== 0) {
        data = {message: "Please provide appropriate values to the fields marked in red"};
      } else {
        if ((this.state.err.status === 404 && this.state.query.type === 'GET') || (this.state.query.type === 'POST' && this.state.query.state === 'FAILURE')) {
          this.postPolicy();
          data = {message: 'Creating policy'};
        } else {
          this.putPolicy();
          data = {message: 'Updating policy'};
        }
      }
      document.querySelector('#policy-submit').MaterialSnackbar.showSnackbar(data);
    }
  }

  render() {
    if (!this.state.query) return null;
    if (this.state.query.type === 'GET' && this.state.query.state === 'PENDING') {
      return (
        <div>
          <h3 style={{textAlign: 'center'}}>Please wait, coming right up!</h3>
          <Loader/>
        </div>
      );
    }

    if ((this.state.query.type === 'GET' && this.state.query.state === 'SUCCESS') ||
      this.state.query.type === 'POST' || this.state.query.type === 'PUT') {
      return (
        <div className="mdl-grid mdl-grid--no-spacing mdl-cell--11-col mdl-shadow--3dp">
          {this.getMessage()}
          {this.getDisplayDetails()}
          {this.getSchedule()}
          {this.getDescription()}
          {this.getWork()}
          {this.getSubmit()}
          <div id="policy-submit" className="mdl-js-snackbar mdl-snackbar">
            <div className="mdl-snackbar__text"/>
            <button className="mdl-snackbar__action" type="button"/>
          </div>
        </div>
      );
    }
    return (
      <div className="mdl-grid mdl-grid--no-spacing mdl-cell--11-col mdl-shadow--3dp">
        {this.getMessage()}
      </div>
    );
  }


  isCreateView() {
    return (this.state.err.status === 404 && this.state.query.type === 'GET') || (this.state.query.type === 'POST' && (this.state.query.state === 'FAILURE' || this.state.query.state === 'PENDING'));
  }

  getDisplayDetails() {
    if (this.isCreateView()) {
      return (<div className="mdl-grid mdl-cell--12-col" style={{borderBottom: '1px solid rgba(0,0,0,.13)'}}/>);
    }else{
      const createdDate = new Date(this.state.json.policyDetails.createdAt);
      const modifiedDate = new Date(this.state.json.policyDetails.modifiedAt);
      const createdString = createdDate.toDateString() + ' ' + createdDate.toLocaleTimeString();
      const modifiedString = modifiedDate.toDateString() + ' ' + modifiedDate.toLocaleTimeString();
      return (<div className="mdl-grid mdl-cell--12-col" style={{borderBottom: '1px solid rgba(0,0,0,.13)'}}>
        <div className="mdl-cell--2-col"><span
          className="mdl-color-text--primary mdl-typography--caption">Version:  </span>{this.state.json.version}</div>
        <div className="mdl-layout-spacer"/>
        <div className="mdl-cell--4-col mdl-cell--8-col-tablet"><span
          className="mdl-color-text--primary mdl-typography--caption">Created at:  </span>{createdString}</div>
        <div className="mdl-layout-spacer"/>
        <div className="mdl-cell--4-col mdl-cell--8-col-tablet"><span
          className="mdl-color-text--primary mdl-typography--caption">Modified at:  </span>{modifiedString}</div>
        <div className="mdl-layout-spacer"/>
      </div>);
    }
  }

  getMessage() {
    return (<div className="mdl-grid mdl-cell--12-col">
      <div
        className="mdl-typography--headline mdl-typography--font-thin mdl-color-text--primary mdl-cell--12-col">{this.state.msg}</div>
    </div>);
  }

  getSchedule() {
    return (<div className="mdl-grid mdl-cell--12-col" style={{borderBottom: '1px solid rgba(0,0,0,.13)'}}>
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Schedule</div>
      <div className="mdl-typography--caption mdl-color-text--grey">
        Every 20 mins, <span
        className="mdl-typography--font-bold">{this.state.json.policyDetails.policy.schedule.pgCovPct}%</span> host of
        all hosts (with recorder attached) will be profiled.
        On each host being profiled, profiling will run for <span
        className="mdl-typography--font-bold">{this.state.json.policyDetails.policy.schedule.duration} seconds </span>
        exactly once in 20 mins.
      </div>
      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
        <input className="mdl-textfield__input" type="number" min="60" max="960" id="duration"
               onChange={this.handleScheduleChange} value={this.state.json.policyDetails.policy.schedule.duration}
               required/>
        <label className="mdl-textfield__label" htmlFor="duration">Duration in secs...</label>
        <span className="mdl-textfield__error">Duration must be between 60-960 secs</span>
        <div className="mdl-tooltip mdl-tooltip--large" htmlFor="duration">Duration (in secs) one covered host will be
          profiled in every 20 mins
        </div>
      </div>
      <div className="mdl-layout-spacer"/>
      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
        <input className="mdl-textfield__input" type="number" min="0" max="100" id="pgCovPct"
               onChange={this.handleScheduleChange} value={this.state.json.policyDetails.policy.schedule.pgCovPct}
               required/>
        <label className="mdl-textfield__label" htmlFor="pgCovPct">Coverage Percentage...</label>
        <span className="mdl-textfield__error">Coverage % must be between 0-100</span>
        <div className="mdl-tooltip  mdl-tooltip--large" htmlFor="pgCovPct">Percentage of hosts to be covered for
          profiling, every 20 mins
        </div>
      </div>
      <div className="mdl-layout-spacer"/>
    </div>);
  }

  getDescription() {
    return (<div className="mdl-grid mdl-cell--12-col" style={{borderBottom: '1px solid rgba(0,0,0,.13)'}}>
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Description
        <div className="mdl-typography--caption mdl-color-text--grey">
          Please add some description about the policy to help identifying it later.
        </div>
      </div>
      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield">
        <textarea className="mdl-textfield__input" type="text" id="description" rows="2"
                  onChange={this.handleDescriptionChange} value={this.state.json.policyDetails.policy.description}
                  required/>
      </div>
    </div>);
  }

  getWork() {
    const workArray = this.state.json.policyDetails.policy.work;
    const cpu_sample_work = workArray.some((w) => w.wType === "cpu_sample_work") ? workArray.filter((w) => w.wType === "cpu_sample_work")[0].cpuSample : " ";
    return (<div className="mdl-grid mdl-cell--12-col" style={{borderBottom: '1px solid rgba(0,0,0,.13)'}}>
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Enabled Profiling Types</div>
      <div className="mdl-grid mdl-cell--12-col">
        <div
          className="mdl-typography--body-1 mdl-typography--font-thin mdl-color-text--primary mdl-cell--4-col mdl-cell--middle">
          CPU Sampling
          <div className="mdl-typography--caption mdl-color-text--grey">
            Profiles details of methods running on CPU
          </div>
        </div>
        <div className="mdl-layout-spacer"/>
        <div
          className="mdl-cell--4-col mdl-cell--7-col-tablet mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
          <input name="cpu_sample_work" className="mdl-textfield__input" type="number" min="50" max="100"
                 id={"cpuSample_frequency"}
                 onChange={this.handleWorkChange} value={cpu_sample_work.frequency} required/>
          <label className="mdl-textfield__label" htmlFor="cpuSample_frequency">Frequency...</label>
          <span className="mdl-textfield__error">Frequency must be between 50-100</span>
          <div className="mdl-tooltip mdl-tooltip--large" htmlFor="cpuSample_frequency">How frequently should profiles
            be sampled? More value implies finer details but more overhead
          </div>
        </div>
        <div className="mdl-layout-spacer"/>
        <div
          className="mdl-cell--4-col mdl-cell--7-col-tablet mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
          <input name="cpu_sample_work" className="mdl-textfield__input" type="number" min="1" max="999"
                 id={"cpuSample_maxFrames"}
                 onChange={this.handleWorkChange} value={cpu_sample_work.maxFrames} required/>
          <label className="mdl-textfield__label" htmlFor="cpuSample_maxFrames">Max Frames...</label>
          <span className="mdl-textfield__error">Max Frames should be between 1-999</span>
          <div className="mdl-tooltip mdl-tooltip--large" htmlFor="cpuSample_maxFrames">Depth of stacktraces to be used
            for presenting. Large values implies more verbosity
          </div>

        </div>
        <div className="mdl-layout-spacer"/>
      </div>
    </div>);
  }

  getSubmit() {
    let buttonText = '';
    if (this.isCreateView()) {
      buttonText = 'CREATE';
    } else {
      buttonText = 'UPDATE';
    }
    return (
      <div className="mdl-grid mdl-cell--12-col">
        <button className="mdl-button mdl-js-button mdl-button--colored mdl-button--raised mdl-js-ripple-effect"
                style={{margin: 'auto'}}
                onClick={this.handleSubmitClick}>
          {buttonText}
        </button>
      </div>);
  }

}
