import React from "react";
import styles from './PolicyComponent.css';
import Loader from '../LoaderComponent/LoaderComponent';

import http from 'utils/http';

export default class PolicyComponent extends React.Component {
  url = '../api/policy/' + this.props.app + '/' + this.props.cluster + '/' + this.props.proc;

  constructor(props) {
    super(props);
    this.handleScheduleChange = this.handleScheduleChange.bind(this);
    this.handleDescriptionChange = this.handleDescriptionChange.bind(this);
    this.handleSubmitClick = this.handleSubmitClick.bind(this);
    this.handleWorkChange = this.handleWorkChange.bind(this);
    this.fetchPolicy = this.fetchPolicy.bind(this);
    this.postPolicy = this.postPolicy.bind(this);
    this.state = {
      query: {
        type: "GET",
        state: "INIT"
      },
      json: null,
      err: null,
    };
  }

  componentDidMount() {
    console.log("Making a GET request");
    this.fetchPolicy();
    window['counter'] = 0;
  }

  componentDidUpdate() {
    console.log("Component Did Update");
    componentHandler.upgradeDom(); // eslint-disable-line
  }

  fetchPolicy() {
    this.setState({
      query: {
        type: "GET",
        state: "PENDING"
      }
    });
    http.get(this.url).then(json => {
      this.setState({
        query: {
          type: "GET",
          state: "SUCCESS"
        },
        json,
        err: {}
      })
    }).catch(err => {
      //Check if error is 404 then populate the json
      this.setState({
        query: {
          type: "GET",
          state: "FAILURE"
        },
        err,
        json: {}
      })
    });
  }

  postPolicy() {
    // if(verifyJson())

    this.setState({
      query: {
        type: "POST",
        state: "PENDING"
      }
    });
    console.log(this.state.json);
    http.post(this.url, this.state.json).then(json => {
      this.setState({
        query: {
          type: "POST",
          state: "SUCCESS"
        },
        json,
        err: {}
      })
    }).catch(err => {
      this.setState({
        query: {
          type: "POST",
          state: "FAILURE"
        },
        err,
      })
    });
  }

  componentWillReceiveProps(newProps) {
    if (newProps.proc !== this.props.proc) {
      console.log("Making a GET request");
      this.fetchPolicy();
    }
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
    const data = {message: 'Example Message # ' + ++counter};
    console.log("Making a PUT or POST request");
    this.postPolicy();
    document.querySelector('#demo-toast-example').MaterialSnackbar.showSnackbar(data);
  }

  render() {
    if (!this.state.json) return null;
    if (this.state.query.type === 'GET' && this.state.query.state === 'PENDING') {
      return (
        <div>
          <h4 style={{textAlign: 'center'}}>Please wait, coming right up!</h4>
          <Loader />
        </div>
      );
    }
    if (this.state.query.type === 'GET' && this.state.query.state === 'FAILED' && this.state.json === null) {
      return (
        <div className={styles.card}>
          <h2>Failed to fetch the data. Please refresh or try again later</h2>
        </div>
      );
    }

    return (
      <div className="mdl-grid mdl-grid--no-spacing mdl-cell--11-col mdl-shadow--3dp">
        {this.getDisplayDetails()}
        {this.getSchedule()}
        {this.getDescription()}
        {this.getWork()}
        {this.getSubmit()}
        <div id="demo-toast-example" className="mdl-js-snackbar mdl-snackbar"
             style={{background: 'rgb(137, 137, 132)'}}>
          <div className="mdl-snackbar__text"/>
          <button className="mdl-snackbar__action" type="button"/>
        </div>
      </div>
    );
  }


  getDisplayDetails() {
    return (<div className="mdl-grid mdl-cell--12-col mdl-shadow--3dp">
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Policy Found</div>
      <div className="mdl-cell--2-col">Version: {this.state.json.version}</div>
      <div className="mdl-layout-spacer"/>
      <div className="mdl-cell--3-col">Created at: {this.state.json.policyDetails.createdAt}</div>
      <div className="mdl-layout-spacer"/>
      <div className="mdl-cell--3-col">Modified at: {this.state.json.policyDetails.modifiedAt}</div>
      <div className="mdl-layout-spacer"/>

    </div>);
  }

  getSchedule() {
    return (<div className="mdl-grid mdl-cell--12-col mdl-shadow--3dp">
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Schedule</div>
      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
        <input className="mdl-textfield__input" type="number" min="60" max="960" id="duration"
               onChange={this.handleScheduleChange} value={this.state.json.policyDetails.policy.schedule.duration}/>
        <label className="mdl-textfield__label" htmlFor="duration">Duration...</label>
        <span className="mdl-textfield__error">Duration must be between 60-960</span>
      </div>
      <div className="mdl-layout-spacer"/>
      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
        <input className="mdl-textfield__input" type="number" min="0" max="100" id="pgCovPct"
               onChange={this.handleScheduleChange} value={this.state.json.policyDetails.policy.schedule.pgCovPct}/>
        <label className="mdl-textfield__label" htmlFor="pgCovPct">Coverage Percentage...</label>
        <span className="mdl-textfield__error">Coverage % must be between 0-100</span>
      </div>
      <div className="mdl-layout-spacer"/>
    </div>);
  }

  getDescription() {
    return (<div className="mdl-grid mdl-cell--12-col mdl-shadow--3dp">
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Description</div>

      <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
        <textarea className="mdl-textfield__input" type="text" id="description" rows="2"
                  onChange={this.handleDescriptionChange}
                  value={this.state.json.policyDetails.policy.description}/>
        <label className="mdl-textfield__label" htmlFor="description">Some details about the policy...</label>
      </div>
    </div>);
  }

  getWork() {
    const workArray = this.state.json.policyDetails.policy.work;
    const cpu_sample_work = workArray.some((w) => w.wType === "cpu_sample_work") ? workArray.filter((w) => w.wType === "cpu_sample_work")[0].cpuSample : " ";
    return (<div className="mdl-grid mdl-cell--12-col mdl-shadow--3dp">
      <div className="mdl-typography--headline mdl-typography--font-thin mdl-cell--12-col">Work</div>
      <div className="mdl-grid mdl-cell--12-col">
        <div className="mdl-typography--body-1 mdl-typography--font-thin mdl-cell--3-col mdl-cell--middle">
          CPU Sampling
        </div>
        <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
          <input name="cpu_sample_work" className="mdl-textfield__input" type="number" min="50" max="100"
                 id={"cpuSample_frequency"}
                 onChange={this.handleWorkChange} value={cpu_sample_work.frequency}/>
          <label className="mdl-textfield__label" htmlFor={"cpuSample_frequency"}>Frequency...</label>
          <span className="mdl-textfield__error">Frequency must be between 50-100</span>
        </div>
        <div className="mdl-layout-spacer"/>
        <div className="mdl-cell--4-col mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
          <input name="cpu_sample_work" className="mdl-textfield__input" type="number" min="1" max="999"
                 id={"cpuSample_maxFrames"}
                 onChange={this.handleWorkChange} value={cpu_sample_work.maxFrames}/>
          <label className="mdl-textfield__label" htmlFor={"cpuSample_maxFrames"}>Max Frames...</label>
          <span className="mdl-textfield__error">Max Frames should be between 1-999</span>
        </div>
        <div className="mdl-layout-spacer"/>
      </div>
    </div>);
  }

  getSubmit() {
    return (
      <div className="mdl-grid mdl-cell--12-col mdl-shadow--3dp">
        <button className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect"
                style={{background: 'rgb(137, 137, 132)', color: 'white', margin: 'auto'}}
                onClick={this.handleSubmitClick}>
          Submit
        </button>
      </div>)
  }
}
