import React from "react";
import styles from './PolicyComponent.css';
import WorkType from '../WorkTypeComponent/WorkTypeComponent';
import Loader from '../LoaderComponent/LoaderComponent';

import http from 'utils/http';

export default class PolicyComponent extends React.Component {
  url = '../api/policy/' + this.props.app + '/' + this.props.cluster + '/' + this.props.proc;

  constructor(props) {
    super(props);
    this.handleFormChange = this.handleFormChange.bind(this);
    this.handleSubmitClick = this.handleSubmitClick.bind(this);
    this.handleWorkTypeChangeInForm = this.handleWorkTypeChangeInForm.bind(this);
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

  postPolicy(){
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

  handleFormChange(e) {
    const target = e.target;
    const value = target.value;
    const id = target.id;
    const schedule = {...this.state.json.policyDetails.policy.schedule, [id]: value};
    this.setState((prevState) => ({
        json: {
          version: prevState.json.version,
          policyDetails: {...prevState.json.policyDetails, policy: {...prevState.json.policyDetails.policy , schedule}}
          },
      }));
  }

  handleWorkTypeChangeInForm(e) {
    const prevWorks = this.state.form.values.work;

    const target = e.target;
    const value = target.value;

    const w_type = target.dataset.wType;
    const attribute = target.dataset.attribute;

    const works = prevWorks.filter((work) => {
      return work.w_type === w_type;
    });
    let w = {};
    if (works.length === 0) {
      w = {
        w_type,
        [w_type]: {
          [attribute]: value
        }
      }
    } else {
      //works array must be of size one in this block
      w = {
        w_type,
        [w_type]: {...works[0][w_type], [attribute]: value}
      }
    }
    if (Object.values(w[w_type]).every((el) => (el === ""))) { //Remove work type element from work array if all of the attributes are empty
      this.setState((prevState) => ({
          form: {
            state: prevState.form.state,
            values: {
              ...prevState.form.values,
              work: [...prevState.form.values.work.filter((w) => {
                return w.w_type !== w_type
              })]
            }
          }
        })
      );
    } else {
      this.setState((prevState) => ({
          form: {
            state: prevState.form.state,
            values: {
              ...prevState.form.values,
              work: [...prevState.form.values.work.filter((w) => {
                return w.w_type !== w_type
              }), w]
            }
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

    // const workArray = this.state.form.values.work;

    // const cpu_sample_work = workArray.some((w) => w.w_type === "cpu_sample") ? workArray.filter((w) => w.w_type === "cpu_sample")[0].cpu_sample : " ";
    return (
      <div className="mdl-cell mdl-cell--11-col  mdl-shadow--3dp">
        <div className="mdl-grid mdl-grid--no-spacing">
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--6-col mdl-layout--middle">
                {/*<h2 className="mdl-card__title-text">{this.state.alert.msg}</h2>{this.state.alert.instr}*/}
              </div>
              <div className="mdl-layout-spacer"/>
              <div className="mdl-layout--middle" style={{margin: 'auto 10px auto auto'}}>
                <button id="submit_button" onClick={this.handleSubmitClick}
                        className={"mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent "}
                        style={{background: 'rgb(137, 137, 132)'}}>
                  {/*{this.state.form.state === "UPDATE" ? "UPDATE" : "CREATE"}*/}
                </button>
              </div>
            </div>
          </div>
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Duration (in secs)</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="number" min="100" max="960" id="duration"
                         onChange={this.handleFormChange} value={this.state.json.policyDetails.policy.schedule.duration}/>
                  <label className="mdl-textfield__label" htmlFor="duration">120</label>
                  <span className="mdl-textfield__error">Input should be between 100-960</span>
                </div>
              </div>
              {/*<div className="mdl-cell mdl-cell--4-col mdl-cell--middle">*/}
                {/*<div>Coverage Percentage</div>*/}
                {/*<div className="mdl-textfield mdl-js-textfield">*/}
                  {/*<input className="mdl-textfield__input" type="number" min="0" max="100" id="coverage_pct"*/}
                         {/*onChange={this.handleFormChange} value={this.state.form.values.coverage_pct}/>*/}
                  {/*<label className="mdl-textfield__label" htmlFor="coverage_pct">10</label>*/}
                  {/*<span className="mdl-textfield__error">Input should be between 0-100</span>*/}
                {/*</div>*/}
              {/*</div>*/}
              {/*<div className="mdl-cell mdl-cell--4-col mdl-cell--middle">*/}
                {/*<div>Description</div>*/}
                {/*<div className="mdl-textfield mdl-js-textfield">*/}
              {/*<textarea className="mdl-textfield__input" type="text" rows="3" id="description"*/}
                        {/*onChange={this.handleFormChange} value={this.state.form.values.description}>*/}
                {/*</textarea>*/}
                  {/*<label className="mdl-textfield__label" htmlFor="description">#servicename #nfrpolicy ...</label>*/}
                {/*</div>*/}
              {/*</div>*/}
            </div>
          </div>
          {/*<div className="mdl-cell mdl-cell--12-col">*/}
            {/*<div className="mdl-grid mdl-grid--no-spacing">*/}
              {/*<WorkType name="CPU Sampling" attributes={["Frequency", "Max Frames"]} isDisabled={false}*/}
                        {/*w_type="cpu_sample" onChange={this.handleWorkTypeChangeInForm}*/}
                        {/*value={cpu_sample_work}/>*/}
            {/*</div>*/}
          {/*</div>*/}
        </div>
        <div id="demo-toast-example" className="mdl-js-snackbar mdl-snackbar"
             style={{background: 'rgb(137, 137, 132)'}}>
          <div className="mdl-snackbar__text"></div>
          <button className="mdl-snackbar__action" type="button"></button>
        </div>
      </div>
    );
  }
}
