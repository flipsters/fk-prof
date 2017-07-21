import React from "react";
import styles from './PolicyComponent.css';
import WorkType from '../WorkTypeComponent/WorkTypeComponent';
import Loader from '../LoaderComponent/LoaderComponent';
export default class PolicyComponent extends React.Component {
  constructor(props) {
    super(props);
    this.state = {status: "GET_INIT", policy: {duration}, duration:""};
    this.policyGetStatusResultText = this.policyGetStatusResultText.bind(this);
    this.handleChange = this.handleChange.bind(this);

  }

  componentDidMount() {
    console.log("Making a GET request");
    this.setState({status: "GET_PENDING"});
    // window.setTimeout(() => this.setState({
    //   status: "GET_SUCCESS",
    //   policy: {duration: 120, coverage: 10, work: {work_type: 1}}
    // }), 2000);
    window.setTimeout(() => this.setState({status: "GET_SUCCESS"}), 2000);
  }

  componentDidUpdate () {
    console.log("Component Did Update");
    componentHandler.upgradeDom(); // eslint-disable-line
  }

  componentWillReceiveProps(newProps) {
    if (newProps.proc !== this.props.proc) {
      console.log("Making a GET request");
      this.setState({status: "GET_PENDING"});
      // window.setTimeout(() => this.setState({
      //   status: "GET_SUCCESS",
      //   policy: {duration: 120, coverage: 10, work: {work_type: 1}}
      // }), 2000);
      window.setTimeout(() => this.setState({status: "GET_SUCCESS"}), 2000);
    }
  }

  policyGetStatusResultText() {
    if (this.state.status === "GET_SUCCESS") {
      if (this.state.policy !== null) {
        return (<div className="mdl-grid">
          <div className="mdl-cell mdl-cell--11-col mdl-layout--middle">
            <h2 className="mdl-card__title-text">Policy found</h2>Please find details below
          </div>
          <div className="mdl-layout-spacer"/>
          <div className="mdl-cell mdl-cell--1-col mdl-layout--middle">
            <button
              className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
              style={{background: 'rgb(137, 137, 132)', margin: '0 auto'}}>Update
            </button>
          </div>
        </div>)
      } else {
        return (<div className="mdl-grid">
          <div className="mdl-cell mdl-cell--11-col mdl-layout--middle">
            <h2 className="mdl-card__title-text">No policy found</h2>Create a new one by adding details below
          </div>
          <div className="mdl-layout-spacer"/>
          <div className="mdl-cell mdl-cell--1-col mdl-layout--middle">
            <button
              className="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
              style={{background: 'rgb(137, 137, 132)', margin: '0 auto'}}>Create
            </button>
          </div>
        </div>)
      }
    }
  }

  handleChange(e){
    const target = e.target;
    const value = target.value;
    const id = target.id;
    console.log(id + "  " + value);
    this.setState({
        [id]: value
    });
  }


  render() {
    if (!this.state.status) return null;
    if (this.state.status === 'GET_PENDING') {
      return (
        <div>
          <h4 style={{textAlign: 'center'}}>Please wait, coming right up!</h4>
          <Loader />
        </div>
      );
    }
    if (this.props.status === 'GET_ERROR') {
      return (
        <div className={styles.card}>
          <h2>Failed to fetch the data. Please refresh or try again later</h2>
        </div>
      );
    }
    return (
      <div className="mdl-cell mdl-cell--11-col  mdl-shadow--3dp">
        <div className="mdl-grid mdl-grid--no-spacing">
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            {this.policyGetStatusResultText()}
          </div>
          <div className="mdl-cell mdl-cell--12-col mdl-shadow--2dp">
            <div className="mdl-grid">
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Duration (in secs)</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="text" pattern="^[1-9][0-9][0-9]$" id="duration" onChange={this.handleChange} value={this.state.duration}/>
                  <label className="mdl-textfield__label" htmlFor="duration">120</label>
                  <span className="mdl-textfield__error">Input should be between 100-960!</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Coverage Percentage</div>
                <div className="mdl-textfield mdl-js-textfield">
                  <input className="mdl-textfield__input" type="text" pattern="^[0-9][0-9]?$|^100$" id="coverage_pct" onChange={this.handleChange} value={this.state.policy? this.state.policy.coverage_pct:""}/>
                  <label className="mdl-textfield__label" htmlFor="coverage_pct">10</label>
                  <span className="mdl-textfield__error">Input should be between 0-100!</span>
                </div>
              </div>
              <div className="mdl-cell mdl-cell--4-col mdl-cell--middle">
                <div>Description</div>
                <div className="mdl-textfield mdl-js-textfield">
                <textarea className="mdl-textfield__input" type="text" rows="3" id="description" onChange={this.handleChange} value={this.state.policy? this.state.policy.description:""}>
                </textarea>
                  <label className="mdl-textfield__label" htmlFor="description">#servicename #nfrpolicy ...</label>
                </div>
              </div>
            </div>
          </div>
          <div className="mdl-cell mdl-cell--12-col">
            <div className="mdl-grid mdl-grid--no-spacing">
              <WorkType name="CPUSampling" attributes={["Frequency", "Max Frames"]} isDisabled={false}/>
              <WorkType name="Thread Sampling" attributes={["Frequency", "Max Frames"]} isDisabled={true}/>
              <WorkType name="Monitor Contention" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}/>
              <WorkType name="Monitor Wait" attributes={["Max Monitors", "Max Frames"]} isDisabled={true}/>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
